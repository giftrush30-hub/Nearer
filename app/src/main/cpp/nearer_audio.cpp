#include <jni.h>
#include <oboe/Oboe.h>

#include <algorithm>
#include <array>
#include <atomic>
#include <cstdint>
#include <condition_variable>
#include <memory>
#include <mutex>
#include <string>

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

    uint32_t available() const noexcept {
        const uint32_t read = read_.load(std::memory_order_relaxed);
        const uint32_t write = write_.load(std::memory_order_acquire);
        return write - read;
    }

    uint32_t discard(uint32_t count) noexcept {
        const uint32_t read = read_.load(std::memory_order_relaxed);
        const uint32_t write = write_.load(std::memory_order_acquire);
        const uint32_t discarded = std::min<uint32_t>(count, write - read);
        read_.store(read + discarded, std::memory_order_release);
        return discarded;
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
    bool start(int32_t inputDeviceId, bool preferUnprocessedInput, bool privacySensitiveCapture) {
        playbackRing_.reset();
        analysisRing_.reset();
        disconnected_.store(false, std::memory_order_relaxed);
        inputDisconnected_.store(false, std::memory_order_relaxed);
        outputDisconnected_.store(false, std::memory_order_relaxed);
        inputRaw_.store(nullptr, std::memory_order_relaxed);
        outputRaw_.store(nullptr, std::memory_order_relaxed);
        errorCallbacksInFlight_.store(0, std::memory_order_relaxed);
        outputUnderruns_.store(0, std::memory_order_relaxed);
        lastXrunCount_.store(-1, std::memory_order_relaxed);
        droppedCaptureFrames_.store(0, std::memory_order_relaxed);
        droppedPlaybackFrames_.store(0, std::memory_order_relaxed);
        droppedAnalysisFrames_.store(0, std::memory_order_relaxed);

        auto self = shared_from_this();
        const auto dataCallback =
            std::static_pointer_cast<oboe::AudioStreamDataCallback>(self);
        const auto errorCallback =
            std::static_pointer_cast<oboe::AudioStreamErrorCallback>(self);

        auto openOutput = [&](oboe::SharingMode sharing) {
            oboe::AudioStreamBuilder builder;
            builder.setDirection(oboe::Direction::Output)
                ->setChannelCount(1)
                ->setChannelConversionAllowed(true)
                ->setFormat(oboe::AudioFormat::Float)
                ->setFormatConversionAllowed(true)
                ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
                ->setUsage(oboe::Usage::Media)
                ->setContentType(oboe::ContentType::Music)
                ->setPackageName("com.nearer.app")
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
        outputRaw_.store(output_.get(), std::memory_order_release);

        auto openInput = [&](oboe::SharingMode sharing) {
            oboe::AudioStreamBuilder builder;
            builder.setDirection(oboe::Direction::Input)
                ->setChannelCount(1)
                ->setChannelConversionAllowed(true)
                ->setFormat(oboe::AudioFormat::Float)
                ->setFormatConversionAllowed(true)
                ->setSampleRate(output_->getSampleRate())
                ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
                ->setSharingMode(sharing)
                ->setInputPreset(preferUnprocessedInput ? oboe::InputPreset::Unprocessed : oboe::InputPreset::VoiceRecognition)
                ->setPackageName("com.nearer.app")
                ->setDataCallback(dataCallback)
                ->setErrorCallback(errorCallback);
            // AAudio added explicit privacy-sensitive capture in Android 11 / API 30.
            // Apply it only when the Kotlin caller has verified the runtime API level.
            if (privacySensitiveCapture) {
                builder.setPrivacySensitiveMode(oboe::PrivacySensitiveMode::Enabled);
            }
            if (inputDeviceId >= 0) builder.setDeviceId(inputDeviceId);
            builder.setSampleRateConversionQuality(oboe::SampleRateConversionQuality::Medium);
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
        inputRaw_.store(input_.get(), std::memory_order_release);
        // Android may substitute a different device even when a device ID was requested.
        // Never silently claim that the chosen USB microphone is active.
        if (inputDeviceId >= 0 && input_->getDeviceId() != inputDeviceId) {
            closeStreams();
            return false;
        }

        const int32_t outputBurst = std::max(1, output_->getFramesPerBurst());
        const int32_t inputBurst = std::max(1, input_->getFramesPerBurst());
        maxPlaybackQueueFrames_ = static_cast<uint32_t>(
            std::min<uint64_t>(kRingCapacity / 2, static_cast<uint64_t>(outputBurst) * 4));
        output_->setBufferSizeInFrames(outputBurst * 2);
        input_->setBufferSizeInFrames(inputBurst * 2);

        running_.store(true, std::memory_order_release);
        // Start capture first so the playback ring has audio before the output callback begins.
        result = input_->requestStart();
        if (result != oboe::Result::OK) {
            running_.store(false, std::memory_order_release);
            closeStreams();
            return false;
        }
        result = output_->requestStart();
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
        // Stop playback immediately when the user disables monitoring or a route errors.
        // Do not let an in-flight output callback drain queued microphone samples.
        if (!running_.load(std::memory_order_acquire)) {
            if (stream->getDirection() == oboe::Direction::Output) {
                std::fill(samples, samples + frameCount, 0.0f);
            }
            return oboe::DataCallbackResult::Continue;
        }
        if (stream->getDirection() == oboe::Direction::Input) {
            const uint32_t writtenToPlayback = playbackRing_.push(samples, frameCount);
            const uint32_t writtenToAnalysis = analysisRing_.push(samples, frameCount);
            if (writtenToPlayback < frameCount || writtenToAnalysis < frameCount) {
                droppedCaptureFrames_.fetch_add(
                    frameCount - std::min(writtenToPlayback, writtenToAnalysis),
                    std::memory_order_relaxed);
            }
        } else {
            const uint32_t queued = playbackRing_.available();
            if (queued > maxPlaybackQueueFrames_) {
                const uint32_t discarded = playbackRing_.discard(queued - maxPlaybackQueueFrames_);
                droppedPlaybackFrames_.fetch_add(discarded, std::memory_order_relaxed);
            }
            const uint32_t copied = playbackRing_.pop(samples, frameCount);
            if (copied < frameCount) {
                std::fill(samples + copied, samples + frameCount, 0.0f);
                outputUnderruns_.fetch_add(1, std::memory_order_relaxed);
            }
        }
        return oboe::DataCallbackResult::Continue;
    }

    void onErrorBeforeClose(oboe::AudioStream* stream, oboe::Result) override {
        // The error callback must never take streamMutex_: closeStreams() may be
        // waiting for Oboe to finish this callback while holding that mutex.
        errorCallbacksInFlight_.fetch_add(1, std::memory_order_acq_rel);
        if (stream == inputRaw_.load(std::memory_order_acquire)) {
            inputDisconnected_.store(true, std::memory_order_release);
        } else if (stream == outputRaw_.load(std::memory_order_acquire)) {
            outputDisconnected_.store(true, std::memory_order_release);
            const auto result = stream->getXRunCount();
            if (result == oboe::Result::OK) {
                lastXrunCount_.store(result.value(), std::memory_order_relaxed);
            }
        }
        disconnected_.store(true, std::memory_order_release);
        running_.store(false, std::memory_order_release);
    }

    void onErrorAfterClose(oboe::AudioStream*, oboe::Result) override {
        disconnected_.store(true, std::memory_order_release);
        errorCallbacksInFlight_.fetch_sub(1, std::memory_order_acq_rel);
        errorWaitCondition_.notify_all();
    }

    uint32_t readAnalysis(float* destination, uint32_t count) noexcept {
        const uint32_t queued = analysisRing_.available();
        if (queued > maxAnalysisQueueFrames_) {
            const uint32_t discarded = analysisRing_.discard(queued - maxAnalysisQueueFrames_);
            droppedAnalysisFrames_.fetch_add(discarded, std::memory_order_relaxed);
        }
        return analysisRing_.pop(destination, count);
    }

    jlongArray stats(JNIEnv* env) const {
        std::lock_guard<std::mutex> lock(streamMutex_);
        int32_t xrunCount = lastXrunCount_.load(std::memory_order_relaxed);
        if (output_ && !disconnected_.load(std::memory_order_acquire)) {
            const auto xrunResult = output_->getXRunCount();
            if (xrunResult == oboe::Result::OK) {
                xrunCount = xrunResult.value();
                lastXrunCount_.store(xrunCount, std::memory_order_relaxed);
            }
        }
        jlong values[13] = {
            output_ ? output_->getSampleRate() : 0,
            output_ ? output_->getFramesPerBurst() : 0,
            output_ ? output_->getBufferSizeInFrames() : 0,
            xrunCount,
            static_cast<jlong>(outputUnderruns_.load(std::memory_order_relaxed)),
            static_cast<jlong>(droppedCaptureFrames_.load(std::memory_order_relaxed)),
            static_cast<jlong>(droppedPlaybackFrames_.load(std::memory_order_relaxed)),
            static_cast<jlong>(droppedAnalysisFrames_.load(std::memory_order_relaxed)),
            disconnected_.load(std::memory_order_acquire) ? 1 : 0,
            input_ ? input_->getDeviceId() : -1,
            output_ ? output_->getDeviceId() : -1,
            output_ && output_->getPerformanceMode() == oboe::PerformanceMode::LowLatency ? 1 : 0,
            output_ && output_->getSharingMode() == oboe::SharingMode::Exclusive ? 1 : 0
        };
        jlongArray result = env->NewLongArray(13);
        if (result != nullptr) env->SetLongArrayRegion(result, 0, 13, values);
        return result;
    }

    bool isRunning() const noexcept {
        return running_.load(std::memory_order_acquire);
    }

private:
    // Streams can fail before they are started (for example, if opening the
    // paired input stream fails). requestStop() is only valid for active states.
    // Oboe also auto-closes disconnected streams after onErrorBeforeClose().
    void stopAndCloseIfOpen(std::shared_ptr<oboe::AudioStream>& stream, bool disconnected) {
        if (!stream) return;
        const auto state = stream->getState();
        if (disconnected || state == oboe::StreamState::Disconnected ||
            state == oboe::StreamState::Closed) {
            stream.reset();
            return;
        }
        if (state == oboe::StreamState::Started ||
            state == oboe::StreamState::Starting ||
            state == oboe::StreamState::Pausing ||
            state == oboe::StreamState::Paused) {
            stream->requestStop();
        }
        stream->close();
        stream.reset();
    }

    void closeStreams() {
        std::lock_guard<std::mutex> lock(streamMutex_);
        {
            std::unique_lock<std::mutex> waitLock(errorWaitMutex_);
            errorWaitCondition_.wait(waitLock, [this] {
                return errorCallbacksInFlight_.load(std::memory_order_acquire) == 0;
            });
        }
        stopAndCloseIfOpen(input_, inputDisconnected_.load(std::memory_order_acquire));
        inputRaw_.store(nullptr, std::memory_order_release);
        stopAndCloseIfOpen(output_, outputDisconnected_.load(std::memory_order_acquire));
        outputRaw_.store(nullptr, std::memory_order_release);
    }

    mutable std::mutex streamMutex_;
    std::mutex errorWaitMutex_;
    std::condition_variable errorWaitCondition_;
    std::atomic<uint32_t> errorCallbacksInFlight_{0};
    std::atomic<oboe::AudioStream*> inputRaw_{nullptr};
    std::atomic<oboe::AudioStream*> outputRaw_{nullptr};
    std::atomic<bool> inputDisconnected_{false};
    std::atomic<bool> outputDisconnected_{false};
    std::shared_ptr<oboe::AudioStream> input_;
    std::shared_ptr<oboe::AudioStream> output_;
    FloatRing playbackRing_;
    FloatRing analysisRing_;
    uint32_t maxPlaybackQueueFrames_ = 768;
    static constexpr uint32_t maxAnalysisQueueFrames_ = 4096;
    std::atomic<bool> running_{false};
    std::atomic<bool> disconnected_{false};
    std::atomic<uint32_t> outputUnderruns_{0};
    mutable std::atomic<int32_t> lastXrunCount_{-1};
    std::atomic<uint32_t> droppedCaptureFrames_{0};
    std::atomic<uint32_t> droppedPlaybackFrames_{0};
    std::atomic<uint32_t> droppedAnalysisFrames_{0};
};

std::mutex gEngineMutex;
std::shared_ptr<DuplexEngine> gEngine;

} // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_nearer_app_StudioOboeEngine_nativeStart(
    JNIEnv*, jobject, jint inputDeviceId, jboolean preferUnprocessedInput,
    jboolean privacySensitiveCapture) {
    std::lock_guard<std::mutex> lock(gEngineMutex);
    if (gEngine) gEngine->stop();
    auto engine = std::make_shared<DuplexEngine>();
    if (!engine->start(inputDeviceId, preferUnprocessedInput == JNI_TRUE,
                       privacySensitiveCapture == JNI_TRUE)) {
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
    jlong empty[13] = {0, 0, 0, -1, 0, 0, 0, 0, 0, -1, -1, 0, 0};
    jlongArray result = env->NewLongArray(13);
    if (result != nullptr) env->SetLongArrayRegion(result, 0, 13, empty);
    return result;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_nearer_app_StudioOboeEngine_nativeIsRunning(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(gEngineMutex);
    return gEngine && gEngine->isRunning() ? JNI_TRUE : JNI_FALSE;
}
