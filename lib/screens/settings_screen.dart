import 'dart:async';

import 'package:flutter/material.dart';

import '../platform/native_bridge.dart';
import '../voice/models.dart';

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  final _apiKey = TextEditingController();
  final _vocabulary = TextEditingController();
  final _customEndpoint = TextEditingController();
  final _customModel = TextEditingController();

  VoiceSettings _settings = const VoiceSettings();
  bool _loading = true;
  bool _obscureKey = true;

  @override
  void initState() {
    super.initState();
    unawaited(_load());
  }

  @override
  void dispose() {
    _apiKey.dispose();
    _vocabulary.dispose();
    _customEndpoint.dispose();
    _customModel.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    try {
      final config = await NativeBridge.loadConfig();
      final settings = VoiceSettings.fromNativeJson(config);
      final key = await NativeBridge.getApiKey(settings.providerId);
      if (!mounted) return;
      setState(() {
        _settings = settings;
        _apiKey.text = key ?? '';
        _customEndpoint.text = settings.customEndpoint;
        _customModel.text = settings.customModel;
        _loading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() => _loading = false);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Could not load settings: $e')),
      );
    }
  }

  Future<void> _changeProvider(String? providerId) async {
    if (providerId == null) return;
    final provider = VoiceCatalog.provider(providerId);
    final model = provider.models.first;
    final key = await NativeBridge.getApiKey(providerId);
    if (!mounted) return;
    setState(() {
      _settings = _settings.copyWith(
        providerId: providerId,
        modelId: model.id,
        customEndpoint: '',
        customModel: '',
      );
      _apiKey.text = key ?? '';
      _customEndpoint.clear();
      _customModel.clear();
    });
  }

  Future<void> _save() async {
    final provider = _settings.provider;
    if (provider.isCustom) {
      if (_customEndpoint.text.trim().isEmpty ||
          _customModel.text.trim().isEmpty) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Custom provider needs an endpoint and model'),
          ),
        );
        return;
      }
    }
    if (_apiKey.text.trim().isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Enter an API key for this provider')),
      );
      return;
    }

    final settings = _settings.copyWith(
      customEndpoint: _customEndpoint.text.trim(),
      customModel: _customModel.text.trim(),
    );
    try {
      await NativeBridge.setApiKey(settings.providerId, _apiKey.text.trim());
      await NativeBridge.saveConfig(settings.toNativeJson());
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Settings saved')),
      );
      Navigator.of(context).pop();
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Could not save settings: $e')),
      );
    }
  }

  void _addVocabulary() {
    final term = _vocabulary.text.trim();
    if (term.isEmpty || _settings.vocabulary.contains(term)) return;
    setState(() {
      _settings = _settings.copyWith(
        vocabulary: [..._settings.vocabulary, term],
      );
      _vocabulary.clear();
    });
  }

  void _removeVocabulary(String term) {
    setState(() {
      _settings = _settings.copyWith(
        vocabulary: _settings.vocabulary.where((t) => t != term).toList(),
      );
    });
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) {
      return const Scaffold(
        body: Center(child: CircularProgressIndicator()),
      );
    }

    final provider = _settings.provider;
    final models = provider.models;
    final modelId = models.any((m) => m.id == _settings.modelId)
        ? _settings.modelId
        : models.first.id;
    final language = VoiceCatalog.languages.containsKey(_settings.language)
        ? _settings.language
        : VoiceCatalog.languages.keys.first;

    return Scaffold(
      appBar: AppBar(title: const Text('Settings')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          DropdownButtonFormField<String>(
            key: ValueKey('provider-${provider.id}'),
            initialValue: provider.id,
            decoration: const InputDecoration(
              labelText: 'STT provider',
              border: OutlineInputBorder(),
            ),
            isExpanded: true,
            items: VoiceCatalog.providers
                .map(
                  (p) => DropdownMenuItem(
                    value: p.id,
                    child: Text(p.label),
                  ),
                )
                .toList(),
            onChanged: _changeProvider,
          ),
          const SizedBox(height: 12),
          DropdownButtonFormField<String>(
            key: ValueKey('model-${provider.id}-$modelId'),
            initialValue: modelId,
            decoration: const InputDecoration(
              labelText: 'Model',
              border: OutlineInputBorder(),
            ),
            isExpanded: true,
            items: models
                .map(
                  (m) => DropdownMenuItem(
                    value: m.id,
                    child: Text(m.label),
                  ),
                )
                .toList(),
            onChanged: (value) {
              if (value != null) {
                setState(() => _settings = _settings.copyWith(modelId: value));
              }
            },
          ),
          const SizedBox(height: 8),
          Text(
            'Max request: ${_settings.model.maxAudioSeconds}s · '
            'Vocabulary hints: ${_settings.model.supportsVocabularyHint ? 'supported' : 'not supported by this model'}',
            style: Theme.of(context).textTheme.bodySmall,
          ),
          const SizedBox(height: 16),
          DropdownButtonFormField<String>(
            key: ValueKey('language-$language'),
            initialValue: language,
            decoration: const InputDecoration(
              labelText: 'Language',
              border: OutlineInputBorder(),
            ),
            isExpanded: true,
            items: VoiceCatalog.languages.entries
                .map(
                  (e) => DropdownMenuItem(
                    value: e.key,
                    child: Text(e.value),
                  ),
                )
                .toList(),
            onChanged: (value) {
              if (value != null) {
                setState(
                  () => _settings = _settings.copyWith(language: value),
                );
              }
            },
          ),
          const SizedBox(height: 8),
          Text(
            language == VoiceCatalog.autoLanguage
                ? 'Detected per recording from the audio. Pick a language if it '
                    'often guesses wrong or you dictate short phrases.'
                : 'Forces this language for every recording.',
            style: Theme.of(context).textTheme.bodySmall,
          ),
          const SizedBox(height: 16),
          TextField(
            controller: _apiKey,
            obscureText: _obscureKey,
            decoration: InputDecoration(
              labelText: '${provider.label} API key',
              helperText: 'Stored in encrypted app storage',
              border: const OutlineInputBorder(),
              suffixIcon: IconButton(
                icon: Icon(
                  _obscureKey ? Icons.visibility : Icons.visibility_off,
                ),
                onPressed: () => setState(() => _obscureKey = !_obscureKey),
              ),
            ),
          ),
          if (provider.isCustom) ...[
            const SizedBox(height: 12),
            TextField(
              controller: _customEndpoint,
              decoration: const InputDecoration(
                labelText: 'Endpoint',
                hintText: 'https://…',
                border: OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _customModel,
              decoration: const InputDecoration(
                labelText: 'Model ID',
                border: OutlineInputBorder(),
              ),
            ),
          ],
          const SizedBox(height: 20),
          Text(
            'Input mode',
            style: Theme.of(context).textTheme.titleSmall,
          ),
          const SizedBox(height: 8),
          SegmentedButton<String>(
            segments: const [
              ButtonSegment(
                value: 'hold',
                label: Text('Hold to dictate'),
                icon: Icon(Icons.pan_tool_alt),
              ),
              ButtonSegment(
                value: 'tap',
                label: Text('Tap to start/stop'),
                icon: Icon(Icons.touch_app),
              ),
            ],
            selected: {_settings.inputMode},
            onSelectionChanged: (selection) {
              setState(
                () => _settings =
                    _settings.copyWith(inputMode: selection.first),
              );
            },
          ),
          const SizedBox(height: 8),
          Text(
            _settings.inputMode == 'hold'
                ? 'Hold the bubble to record; release to transcribe and insert.'
                : 'Tap the bubble to expand and start; press the send button to transcribe and insert.',
            style: Theme.of(context).textTheme.bodySmall,
          ),
          const SizedBox(height: 20),
          Text(
            'Vocabulary hints',
            style: Theme.of(context).textTheme.titleSmall,
          ),
          const SizedBox(height: 4),
          Text(
            'Names and terms passed to the STT provider. They are not rewritten.',
            style: Theme.of(context).textTheme.bodySmall,
          ),
          const SizedBox(height: 8),
          Row(
            children: [
              Expanded(
                child: TextField(
                  controller: _vocabulary,
                  decoration: const InputDecoration(
                    hintText: 'e.g. Filip',
                    border: OutlineInputBorder(),
                  ),
                  onSubmitted: (_) => _addVocabulary(),
                ),
              ),
              const SizedBox(width: 8),
              FilledButton(onPressed: _addVocabulary, child: const Text('Add')),
            ],
          ),
          const SizedBox(height: 8),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: _settings.vocabulary
                .map(
                  (term) => InputChip(
                    label: Text(term),
                    onDeleted: () => _removeVocabulary(term),
                  ),
                )
                .toList(),
          ),
          const SizedBox(height: 28),
          FilledButton.icon(
            onPressed: _save,
            icon: const Icon(Icons.save),
            label: const Text('Save settings'),
          ),
        ],
      ),
    );
  }
}
