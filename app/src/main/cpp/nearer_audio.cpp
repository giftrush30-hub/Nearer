#include <jni.h>
#include <oboe/Oboe.h>

#include <algorithm>
#include <array>
#include <atomic>
#include <cstdint>
#include <memory>
#include <mutex>

namespace {

constexpr uint32_t kRingCapacity = 16384;
constexpr uint32_t kRingMask = kRingCapacity - 1;
static_assert(std::atomic<uint32_t>::is_always_lock_free, "Audio callback counters must be lock-free");

class FloatRing {
public:
    uint32_t push(const float* source, uint32_t count) noexcept {
        const uint32_t write = write_.load(std::memory_order_relaxed);
        const uint32_t read = read_.load(std::memory_order_acquire);
        const uint32_t freeFrames = kRingCapacity - (write - read);
        const uint32_t accepted = static_cast<uint32_t>(
            std::min<uint32_t>(count, freeFrames));
        for (uint32_t i = 0; i < accepted; ++i) {
            samples_[(write + i) & kRingMask] = source[i];
        }
        write_.store(write + accepted, std::memory_order_release);
        return accepted;
    }

    uint32_t pop(float* destination, uint32_t count) noexcept {
        const uint32_t read = read_.load(std::memory_order_relaxed);
        const uint32_t write = write_.load(std::memory_order_acquire);
        const uint32_t available = static_cast<uint32_t>(
            std::min<uint32_t>(count, write - read));
        for (uint32_t i = 0; i < available; ++i) {
            destination[i] = samples_[(read + i) & kRingMask];
        }
        read_.store(read + available, std::memory_order_release);
        return available;
    }

    void reset() noexcept {
        read_.store(0, std::memory_order_relaxed);
        write_.store(0, std::memory_order_relaxed);
    }

private:
    alignas(64) std::array<float, kRingCapacity> samples_{};
    alignas(64) std::atomic<uint32_t> write_{0};
    alignas(64) std::atomic<uint32_t> read_{0};
};

class DuplexEngine final : public oboe::AudioStreamDataCallback,
                           public oboe::AudioStreamErrorCallback,
                           public std::enable_shared_from_this<DuplexEngine> {
public:
    bool start(int32_t inputDeviceId) {
        playbackRing_.reset();
        analysisRing_.reset();
        disconnected_.store(false, std::memory_order_relaxed);
        outputUnderruns_.store(0, std::memory_order_relaxed);
        droppedCaptureFrames_.store(0, std::memory_order_relaxed);

        auto self = shared_from_this();
        const auto dataCallback =
            std::static_pointer_cast<oboe::AudioStreamDataCallback>(self);
        const auto errorCallback =
            std::static_pointer_cast<oboe::AudioStreamErrorCallback>(self);

        auto openOutput = [&](oboe::SharingMode sharing) {
            oboe::AudioStreamBuilder builder;
            builder.setDirection(oboe::Direction::Output)
                ->setChannelCount(1)
                ->setFormat(oboe::AudioFormat::Float)
                ->setFormatConversionAllowed(true)
                ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
                ->setSharingMode(sharing)
                ->setDataCallback(dataCallback)
                ->setErrorCallback(errorCallback);
            return builder.openStream(output_);
        };

        oboe::Result result = openOutput(oboe::SharingMode::Exclusive);
        if (result != oboe::Result::OK) {
            result = openOutput(oboe::SharingMode::Shared);
        }
        if (result != oboe::Result::OK || !output_) {
            closeStreams();
            return false;
        }

        auto openInput = [&](oboe::SharingMode sharing) {
            oboe::AudioStreamBuilder builder;
            builder.setDirection(oboe::Direction::Input)
                ->setChannelCount(1)
                ->setFormat(oboe::AudioFormat::Float)
                ->setFormatConversionAllowed(true)
                ->setSampleRate(output_->getSampleRate())
                ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
                ->setSharingMode(sharing)
                ->setInputPreset(oboe::InputPreset::VoiceRecognition)
                ->setDataCallback(dataCallback)
                ->setErrorCallback(errorCallback);
            if (inputDeviceId >= 0) builder.setDeviceId(inputDeviceId);
            return builder.openStream(input_);
        };

        result = openInput(oboe::SharingMode::Exclusive);
        if (result != oboe::Result::OK) {
            result = openInput(oboe::SharingMode::Shared);
        }
        if (result != oboe::Result::OK || !input_) {
            closeStreams();
            return false;
        }

        const int32_t outputBurst = std::max(1, output_->getFramesPerBurst());
        const int32_t inputBurst = std::max(1, input_->getFramesPerBurst());
        output_->setBufferSizeInFrames(outputBurst * 2);
        input_->setBufferSizeInFrames(inputBurst * 2);

        running_.store(true, std::memory_order_release);
        result = output_->requestStart();
        if (result != oboe::Result::OK) {
            running_.store(false, std::memory_order_release);
            closeStreams();
            return false;
        }
        result = input_->requestStart();
        if (result != oboe::Result::OK) {
            running_.store(false, std::memory_order_release);
            closeStreams();
            return false;
        }
        return true;
    }

    void stop() {
        running_.store(false, std::memory_order_release);
        closeStreams();
    }

    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream* stream, void* audioData, int32_t numFrames) override {
        if (numFrames <= 0 || audioData == nullptr) {
            return oboe::DataCallbackResult::Continue;
        }
        auto* samples = static_cast<float*>(audioData);
        const auto frameCount = static_cast<uint32_t>(numFrames);
        if (stream->getDirection() == oboe::Direction::Input) {
            const uint32_t writtenToPlayback = playbackRing_.push(samples, frameCount);
            const uint32_t writtenToAnalysis = analysisRing_.push(samples, frameCount);
            if (writtenToPlayback < frameCount || writtenToAnalysis < frameCount) {
                droppedCaptureFrames_.fetch_add(
                    frameCount - std::min(writtenToPlayback, writtenToAnalysis),
                    std::memory_order_relaxed);
            }
        } else {
            const uint32_t copied = playbackRing_.pop(samples, frameCount);
            if (copied < frameCount) {
                std::fill(samples + copied, samples + frameCount, 0.0f);
                outputUnderruns_.fetch_add(1, std::memory_order_relaxed);
            }
        }
        return oboe::DataCallbackResult::Continue;
    }

    void onErrorAfterClose(oboe::AudioStream*, oboe::Result) override {
        disconnected_.store(true, std::memory_order_release);
        running_.store(false, std::memory_order_release);
    }

    uint32_t readAnalysis(float* destination, uint32_t count) noexcept {
        return analysisRing_.pop(destination, count);
    }

    jlongArray stats(JNIEnv* env) const {
        std::lock_guard<std::mutex> lock(streamMutex_);
        int32_t xrunCount = -1;
        if (output_ && !disconnected_.load(std::memory_order_acquire)) {
            const auto xrunResult = output_->getXRunCount();
            if (xrunResult == oboe::Result::OK) xrunCount = xrunResult.value();
        }
        jlong values[7] = {
            output_ ? output_->getSampleRate() : 0,
            output_ ? output_->getFramesPerBurst() : 0,
            output_ ? output_->getBufferSizeInFrames() : 0,
            xrunCount,
            static_cast<jlong>(outputUnderruns_.load(std::memory_order_relaxed)),
            static_cast<jlong>(droppedCaptureFrames_.load(std::memory_order_relaxed)),
            disconnected_.load(std::memory_order_acquire) ? 1 : 0
        };
        jlongArray result = env->NewLongArray(7);
        if (result != nullptr) env->SetLongArrayRegion(result, 0, 7, values);
        return result;
    }

    bool isRunning() const noexcept {
        return running_.load(std::memory_order_acquire);
    }

private:
    void closeStreams() {
        std::lock_guard<std::mutex> lock(streamMutex_);
        if (input_) {
            input_->requestStop();
            input_->close();
            input_.reset();
        }
        if (output_) {
            output_->requestStop();
            output_->close();
            output_.reset();
        }
    }

    mutable std::mutex streamMutex_;
    std::shared_ptr<oboe::AudioStream> input_;
    std::shared_ptr<oboe::AudioStream> output_;
    FloatRing playbackRing_;
    FloatRing analysisRing_;
    std::atomic<bool> running_{false};
    std::atomic<bool> disconnected_{false};
    std::atomic<uint32_t> outputUnderruns_{0};
    std::atomic<uint32_t> droppedCaptureFrames_{0};
};

std::mutex gEngineMutex;
std::shared_ptr<DuplexEngine> gEngine;

} // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_nearer_app_StudioOboeEngine_nativeStart(JNIEnv*, jobject, jint inputDeviceId) {
    std::lock_guard<std::mutex> lock(gEngineMutex);
    if (gEngine) gEngine->stop();
    auto engine = std::make_shared<DuplexEngine>();
    if (!engine->start(inputDeviceId)) {
        gEngine.reset();
        return JNI_FALSE;
    }
    gEngine = std::move(engine);
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_nearer_app_StudioOboeEngine_nativeStop(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(gEngineMutex);
    if (gEngine) gEngine->stop();
    gEngine.reset();
}

extern "C" JNIEXPORT jint JNICALL
Java_com_nearer_app_StudioOboeEngine_nativeReadAnalysis(
    JNIEnv* env, jobject, jfloatArray destination) {
    if (destination == nullptr) return 0;
    std::shared_ptr<DuplexEngine> engine;
    {
        std::lock_guard<std::mutex> lock(gEngineMutex);
        engine = gEngine;
    }
    if (!engine) return 0;
    const jsize length = env->GetArrayLength(destination);
    if (length <= 0) return 0;
    jfloat* samples = env->GetFloatArrayElements(destination, nullptr);
    if (samples == nullptr) return 0;
    const uint32_t copied = engine->readAnalysis(samples, static_cast<uint32_t>(length));
    env->ReleaseFloatArrayElements(destination, samples, 0);
    return static_cast<jint>(copied);
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_com_nearer_app_StudioOboeEngine_nativeGetStats(JNIEnv* env, jobject) {
    std::shared_ptr<DuplexEngine> engine;
    {
        std::lock_guard<std::mutex> lock(gEngineMutex);
        engine = gEngine;
    }
    if (engine) return engine->stats(env);
    jlong empty[7] = {0, 0, 0, -1, 0, 0, 0};
    jlongArray result = env->NewLongArray(7);
    if (result != nullptr) env->SetLongArrayRegion(result, 0, 7, empty);
    return result;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_nearer_app_StudioOboeEngine_nativeIsRunning(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(gEngineMutex);
    return gEngine && gEngine->isRunning() ? JNI_TRUE : JNI_FALSE;
}
