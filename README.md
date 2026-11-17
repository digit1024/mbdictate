# mbdictate

A Flutter + Android dictation app. Tap or hold a floating bubble over any app,
speak, and the transcript is inserted into the focused text field.

Android-only in practice: the Flutter UI is a thin control panel, and the real
work happens in Kotlin (recording, STT requests, overlay, accessibility-based
text insertion).

## Features

- Floating overlay bubble that appears only while a text field is focused
  (password fields are ignored).
- Two input modes: **hold to dictate** and **tap to start/stop**, configurable
  in Settings.
- Speech-to-text through a selectable provider/model, with an API key stored in
  encrypted app storage.
- Per-language selection or **Auto-detect**: the language hint is omitted so
  Whisper infers it from the audio, and DashScope requests `enable_lid`.
- Insertion via the accessibility service, falling back to the clipboard when
  direct insertion fails.
- Audio is chunked to respect each model's max request length
  (`WavChunker` / `WavWriter`).
- Optional vocabulary hints (names, jargon) passed to providers that support
  them.
- Setup screen for microphone, display-over-other-apps, accessibility,
  notification and battery-optimization permissions.

## Supported STT providers

| Provider | Protocol | Models |
| --- | --- | --- |
| Groq | OpenAI transcriptions | Whisper Large v3, Whisper Large v3 Turbo |
| Qwen / DashScope | DashScope ASR | Qwen3 ASR Flash |
| OpenRouter | OpenAI transcriptions | ElevenLabs Scribe v2, Voxtral Small Transcribe 3, Whisper Large v3, Deepgram Nova 3 |
| Custom (OpenAI-compatible) | OpenAI transcriptions | any endpoint + model |
| Custom (DashScope) | DashScope ASR | any endpoint + model |

Default: Groq, `whisper-large-v3`, auto-detected language, hold-to-dictate.

**Auto-detect** omits the language hint from the request, so Whisper, Scribe,
Voxtral and Qwen3 ASR (`enable_lid`) decide the language from the audio.
Deepgram Nova 3 needs an explicit language — pick one when using that model.

## Requirements

- Flutter 3.47+ (Dart SDK `^3.13.7`)
- Android device or emulator, `minSdk` 26
- An API key for the selected STT provider

## Project layout

```
lib/
  main.dart                  app shell and theme
  screens/home_screen.dart   permission status, service toggle
  screens/settings_screen.dart provider/model/language/API key/vocabulary
  voice/models.dart          provider catalog and settings serialization
  platform/native_bridge.dart MethodChannel bridge to Kotlin
android/app/src/main/kotlin/com/example/mbdictate/
  MainActivity.kt            Flutter engine + permission/settings plumbing
  DictationService.kt        foreground service
  DictationController.kt     recording -> STT -> insertion pipeline
  OverlayController.kt       floating bubble
  DictateAccessibilityService.kt focused-field detection and insertion
  recording/                 AudioRecorder, WavWriter
  stt/SttClient.kt           OpenAI-compatible and DashScope requests
  insert/TextInserter.kt     direct insert or clipboard fallback
  storage/                   ConfigStore, SecureKeyStore
```

## Development

```bash
flutter pub get
flutter run
flutter test          # unit tests for the voice catalog and settings
flutter analyze
```

## Release deployment

`deploy_mobile.sh` builds a release binary and installs it on a connected
device, mirroring the LunaAI deploy flow:

```bash
./deploy_mobile.sh                # auto-detect platform from connected devices
./deploy_mobile.sh --android      # force Android
./deploy_mobile.sh -a -d emulator-5554   # target a specific device
./deploy_mobile.sh --help
```

The script runs `flutter clean`, `flutter pub get`, `flutter build apk --release`
(or `flutter build ios --release --no-codesign`), then installs the binary.
On Android it uses `adb install -r`, so app data and granted
accessibility/overlay permissions survive redeploys; without `adb` it falls back
to `flutter install --release`, which uninstalls first and resets them.
The Android APK lands at `build/app/outputs/flutter-apk/app-release.apk`.

## First run

1. Launch the app and work through the permission tiles on the home screen
   (microphone, display over other apps, accessibility, notifications,
   battery optimization).
2. Open **Settings**, pick a provider and model, choose a language (or leave
   **Auto-detect**) and an input mode, paste the API key, optionally add
   vocabulary hints, then save.
3. Start the dictation service from the home screen.
4. Focus a text field in any app, then hold or tap the bubble and speak.