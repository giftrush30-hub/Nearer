# NeaRer messaging: forwarding and voice-note foundations

Date: 2026-11-13

## Research conclusions

### Forwarding

- WhatsApp labels forwarded content and has used forwarding friction to reduce rapid viral spread. NeaRer should keep the simple visible "Forwarded message" cue.
- Telegram's Secret Chats disable forwarding, showing that forwarding is privacy-sensitive and can be restricted in stricter conversations.
- Security research on end-to-end encrypted forwarding warns against exposing chains of original senders and forwarders. NeaRer must not create a global identity trail.
- The Internet Society cautions that mandatory originator metadata can weaken privacy and create false attribution risks.

Sources:
- WhatsApp: https://about.fb.com/news/2020/04/whatsapp-message-forward-limit/
- Telegram FAQ: https://telegram.org/faq
- IACR, Traceback for End-to-End Encrypted Messaging: https://eprint.iacr.org/2019/981.pdf
- Internet Society, Traceability in End-to-End Encrypted Environments: https://www.internetsociety.org/resources/doc/2024/traceability-in-end-to-end-environments/

### NeaRer forwarding rules

1. Forwarded content becomes a new message with a new ID, timestamp, and encryption to the selected recipient.
2. Default label: **Forwarded message**. Do not disclose the original sender, conversation ID, message ID, or helper route.
3. Attribution, if offered, must be an explicit choice and use only a display label—not a stable NeaRer ID or cryptographic identity.
4. Never silently forward to a different peer when the selected recipient loses its route.
5. Preserve existing message-size validation and recipient-bound encryption. Do not bypass the normal send queue.
6. Future restricted chats may disable forwarding, but that requires synchronized chat-level enforcement.

ChatForwardPolicy currently provides pure validation and safe draft formatting rules with JVM tests. The visible forward action and transport/UI wiring remain a separate integration step; this commit does not claim forwarding is available in the app.

## Voice notes

Android MediaRecorder supports microphone recording and MediaPlayer can play the resulting file. Android requires RECORD_AUDIO permission, and microphone access is restricted for background apps on Android 9 and later unless recording is managed by a foreground service. The safe first version should record only while the user is actively in the chat screen.

Sources:
- Android Developers, MediaRecorder overview: https://developer.android.com/media/platform/mediarecorder
- Android MediaRecorder API: https://developer.android.com/reference/android/media/MediaRecorder

### NeaRer voice-note rules

1. Recording starts only after a clear user tap and microphone permission grant; show an obvious recording indicator and elapsed time.
2. Provide Send, Cancel, and preview/playback before sending. Stop and release recorder resources on cancel, screen exit, permission loss, and errors.
3. Begin with Android-friendly AAC in an MPEG-4 container (audio/mp4), with a 3GPP fallback only where device support requires it.
4. Keep audio in a file. Send it through the existing file-transfer engine; do not base64-encode audio into chat packets or load a whole recording into memory.
5. Store duration, safe display filename, MIME type, byte count, and transfer state. Validate before playback or sharing.
6. A voice note is delivered only when the existing transfer path confirms it. Offline queues must preserve the media file until receipt or user deletion.
7. Never record silently in the background. The initial implementation should not request a foreground microphone service.

VoiceNotePolicy currently validates bounded metadata and safe filenames with JVM tests. Recording UI, playback UI, permission flow, and file-transfer integration are not yet implemented.

## Compatibility and release gates

- Keep minSdk 26 (Android 8).
- Reuse the current nearby transport and file-transfer ownership boundaries.
- Run JVM tests and Android debug assembly before merging.
- Test recording, playback, cancel, app backgrounding, permission denial, and transfer interruption on physical Android 8+ devices. Emulator microphone support is not a substitute for device testing.
- Do not describe either feature as complete until visible UI and end-to-end transfer are implemented and verified.
