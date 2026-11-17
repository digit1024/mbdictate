enum SttProtocol {
  openAiTranscription('openai_transcription'),
  dashScopeAsr('dashscope_asr');

  const SttProtocol(this.wireName);
  final String wireName;
}

class SttModel {
  const SttModel({
    required this.id,
    required this.label,
    required this.model,
    required this.maxAudioSeconds,
    this.supportsVocabularyHint = false,
  });

  final String id;
  final String label;
  final String model;
  final int maxAudioSeconds;
  final bool supportsVocabularyHint;
}

class SttProvider {
  const SttProvider({
    required this.id,
    required this.label,
    required this.protocol,
    required this.endpoint,
    required this.models,
    this.isCustom = false,
  });

  final String id;
  final String label;
  final SttProtocol protocol;
  final String endpoint;
  final List<SttModel> models;
  final bool isCustom;
}

class VoiceSettings {
  const VoiceSettings({
    this.providerId = 'groq',
    this.modelId = 'whisper-large-v3',
    this.language = VoiceCatalog.autoLanguage,
    this.inputMode = 'hold',
    this.vocabulary = const [],
    this.customEndpoint = '',
    this.customModel = '',
    this.insertionMode = 'auto',
  });

  final String providerId;
  final String modelId;
  final String language;
  final String inputMode;
  final List<String> vocabulary;
  final String customEndpoint;
  final String customModel;
  final String insertionMode;

  VoiceSettings copyWith({
    String? providerId,
    String? modelId,
    String? language,
    String? inputMode,
    List<String>? vocabulary,
    String? customEndpoint,
    String? customModel,
    String? insertionMode,
  }) {
    return VoiceSettings(
      providerId: providerId ?? this.providerId,
      modelId: modelId ?? this.modelId,
      language: language ?? this.language,
      inputMode: inputMode ?? this.inputMode,
      vocabulary: vocabulary ?? this.vocabulary,
      customEndpoint: customEndpoint ?? this.customEndpoint,
      customModel: customModel ?? this.customModel,
      insertionMode: insertionMode ?? this.insertionMode,
    );
  }

  SttProvider get provider => VoiceCatalog.provider(providerId);
  SttModel get model => VoiceCatalog.model(providerId, modelId);

  String get resolvedEndpoint =>
      customEndpoint.trim().isNotEmpty ? customEndpoint.trim() : provider.endpoint;
  String get resolvedModel =>
      customModel.trim().isNotEmpty ? customModel.trim() : model.model;

  String get wireLanguage =>
      language == VoiceCatalog.autoLanguage ? '' : language;

  Map<String, dynamic> toNativeJson() {
    return {
      'providerId': provider.id,
      'providerLabel': provider.label,
      'modelId': model.id,
      'stt': {
        'protocol': provider.protocol.wireName,
        'endpoint': resolvedEndpoint,
        'model': resolvedModel,
        'language': wireLanguage,
        'maxAudioSeconds': model.maxAudioSeconds,
        'supportsVocabularyHint': model.supportsVocabularyHint,
        'extraParams': <String, dynamic>{},
      },
      'inputMode': inputMode,
      'vocabulary': vocabulary,
      'insertionMode': insertionMode,
    };
  }

  factory VoiceSettings.fromNativeJson(Map<String, dynamic> json) {
    final stt = (json['stt'] as Map?)?.cast<String, dynamic>() ?? {};
    final providerId = json['providerId'] as String? ?? 'groq';
    final provider = VoiceCatalog.provider(providerId);
    final customEndpoint =
        provider.isCustom ? (stt['endpoint'] as String? ?? '') : '';
    final customModel = provider.isCustom ? (stt['model'] as String? ?? '') : '';
    final rawLanguage = (stt['language'] as String? ?? '').trim();
    return VoiceSettings(
      providerId: providerId,
      modelId: json['modelId'] as String? ?? 'whisper-large-v3',
      language: rawLanguage.isEmpty ? VoiceCatalog.autoLanguage : rawLanguage,
      inputMode: json['inputMode'] as String? ?? 'hold',
      vocabulary: (json['vocabulary'] as List<dynamic>? ?? const [])
          .map((e) => e.toString())
          .where((e) => e.trim().isNotEmpty)
          .toList(),
      customEndpoint: customEndpoint,
      customModel: customModel,
      insertionMode: json['insertionMode'] as String? ?? 'auto',
    );
  }
}

class VoiceCatalog {
  VoiceCatalog._();

  static const providers = <SttProvider>[
    SttProvider(
      id: 'groq',
      label: 'Groq',
      protocol: SttProtocol.openAiTranscription,
      endpoint: 'https://api.groq.com/openai/v1/audio/transcriptions',
      models: [
        SttModel(
          id: 'whisper-large-v3',
          label: 'Whisper Large v3',
          model: 'whisper-large-v3',
          maxAudioSeconds: 300,
          supportsVocabularyHint: true,
        ),
        SttModel(
          id: 'whisper-large-v3-turbo',
          label: 'Whisper Large v3 Turbo',
          model: 'whisper-large-v3-turbo',
          maxAudioSeconds: 300,
          supportsVocabularyHint: true,
        ),
      ],
    ),
    SttProvider(
      id: 'qwen',
      label: 'Qwen / DashScope',
      protocol: SttProtocol.dashScopeAsr,
      endpoint:
          'https://dashscope-intl.aliyuncs.com/compatible-mode/v1/chat/completions',
      models: [
        SttModel(
          id: 'qwen3-asr-flash',
          label: 'Qwen3 ASR Flash',
          model: 'qwen3-asr-flash',
          maxAudioSeconds: 300,
          supportsVocabularyHint: true,
        ),
      ],
    ),
    SttProvider(
      id: 'openrouter',
      label: 'OpenRouter',
      protocol: SttProtocol.openAiTranscription,
      endpoint: 'https://openrouter.ai/api/v1/audio/transcriptions',
      models: [
        SttModel(
          id: 'elevenlabs-scribe-v2',
          label: 'ElevenLabs Scribe v2',
          model: 'elevenlabs/scribe-v2',
          maxAudioSeconds: 45,
        ),
        SttModel(
          id: 'voxtral-small-transcribe',
          label: 'Voxtral Small Transcribe 3',
          model: 'mistralai/voxtral-small-transcribe-3-0',
          maxAudioSeconds: 45,
        ),
        SttModel(
          id: 'whisper-large-v3',
          label: 'Whisper Large v3',
          model: 'openai/whisper-large-v3',
          maxAudioSeconds: 45,
        ),
        SttModel(
          id: 'deepgram-nova-3',
          label: 'Deepgram Nova 3',
          model: 'deepgram/nova-3',
          maxAudioSeconds: 45,
        ),
      ],
    ),
    SttProvider(
      id: 'custom_openai',
      label: 'Custom (OpenAI-compatible)',
      protocol: SttProtocol.openAiTranscription,
      endpoint: '',
      isCustom: true,
      models: [
        SttModel(
          id: 'custom',
          label: 'Custom model',
          model: '',
          maxAudioSeconds: 60,
          supportsVocabularyHint: true,
        ),
      ],
    ),
    SttProvider(
      id: 'custom_dashscope',
      label: 'Custom (DashScope)',
      protocol: SttProtocol.dashScopeAsr,
      endpoint: '',
      isCustom: true,
      models: [
        SttModel(
          id: 'custom',
          label: 'Custom model',
          model: '',
          maxAudioSeconds: 300,
          supportsVocabularyHint: true,
        ),
      ],
    ),
  ];

  static const autoLanguage = 'auto';

  static const languages = <String, String>{
    autoLanguage: 'Auto-detect',
    'en': 'English',
    'pl': 'Polish',
    'de': 'German',
    'fr': 'French',
    'es': 'Spanish',
    'it': 'Italian',
    'pt': 'Portuguese',
    'ru': 'Russian',
    'uk': 'Ukrainian',
    'cs': 'Czech',
    'nl': 'Dutch',
    'sv': 'Swedish',
    'da': 'Danish',
    'fi': 'Finnish',
    'no': 'Norwegian',
    'hu': 'Hungarian',
    'ro': 'Romanian',
    'tr': 'Turkish',
    'ar': 'Arabic',
    'hi': 'Hindi',
    'ja': 'Japanese',
    'ko': 'Korean',
    'zh': 'Chinese',
    'th': 'Thai',
    'vi': 'Vietnamese',
    'id': 'Indonesian',
  };

  static SttProvider provider(String id) => providers.firstWhere(
        (p) => p.id == id,
        orElse: () => providers.first,
      );

  static SttModel model(String providerId, String modelId) {
    final provider = VoiceCatalog.provider(providerId);
    return provider.models.firstWhere(
      (m) => m.id == modelId,
      orElse: () => provider.models.first,
    );
  }
}
