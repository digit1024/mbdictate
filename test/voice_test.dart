import 'package:flutter_test/flutter_test.dart';
import 'package:mbdictate/voice/models.dart';

void main() {
  test('catalog resolves providers and models', () {
    final provider = VoiceCatalog.provider('groq');
    expect(provider.label, 'Groq');
    expect(VoiceCatalog.model('groq', 'whisper-large-v3').model, 'whisper-large-v3');
    expect(VoiceCatalog.model('groq', 'missing').id, 'whisper-large-v3');
  });

  test('settings serialize the resolved provider config', () {
    const settings = VoiceSettings(
      providerId: 'groq',
      modelId: 'whisper-large-v3',
      language: 'pl',
      inputMode: 'tap',
      vocabulary: ['Filip'],
    );
    final json = settings.toNativeJson();
    final stt = json['stt'] as Map<String, dynamic>;
    expect(json['providerId'], 'groq');
    expect(json['inputMode'], 'tap');
    expect(stt['protocol'], 'openai_transcription');
    expect(stt['model'], 'whisper-large-v3');
    expect(stt['language'], 'pl');
    expect(stt['supportsVocabularyHint'], isTrue);
    expect(json['vocabulary'], ['Filip']);
  });

  test('auto-detect language serializes as empty and round-trips', () {
    const settings = VoiceSettings();
    expect(settings.language, 'auto');
    expect(settings.toNativeJson()['stt']['language'], '');

    final restored = VoiceSettings.fromNativeJson(
      {'stt': {'language': ''}},
    );
    expect(restored.language, 'auto');
    expect(
      VoiceCatalog.languages[restored.language],
      'Auto-detect',
    );
  });

  test('custom provider uses endpoint and model overrides', () {
    const settings = VoiceSettings(
      providerId: 'custom_openai',
      modelId: 'custom',
      customEndpoint: 'https://example.test/transcribe',
      customModel: 'my-model',
    );
    final stt = settings.toNativeJson()['stt'] as Map<String, dynamic>;
    expect(stt['endpoint'], 'https://example.test/transcribe');
    expect(stt['model'], 'my-model');
  });
}
