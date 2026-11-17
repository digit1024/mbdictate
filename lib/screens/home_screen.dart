import 'dart:async';

import 'package:flutter/material.dart';

import '../platform/native_bridge.dart';
import '../voice/models.dart';
import 'settings_screen.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> with WidgetsBindingObserver {
  Map<String, dynamic> _status = {};
  Map<String, dynamic> _config = {};
  bool _busy = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    unawaited(_refresh());
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      unawaited(_refresh());
    }
  }

  Future<void> _refresh() async {
    final status = await NativeBridge.getStatus();
    final config = await NativeBridge.loadConfig();
    if (!mounted) return;
    setState(() {
      _status = status;
      _config = config;
    });
  }

  bool _on(String key) => _status[key] == true;

  Future<void> _run(Future<void> Function() action) async {
    if (_busy) return;
    setState(() => _busy = true);
    try {
      await action();
      await Future<void>.delayed(const Duration(milliseconds: 300));
      await _refresh();
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Action failed: $e')),
        );
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _toggleService() async {
    if (_on('service')) {
      await NativeBridge.stopService();
    } else {
      final started = await NativeBridge.startService();
      if (!started && mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Could not start dictation service')),
        );
      } else {
        await Future<void>.delayed(const Duration(milliseconds: 300));
      }
    }
    await _refresh();
  }

  Future<void> _showInfo() async {
    final settings = VoiceSettings.fromNativeJson(_config);
    await showDialog<void>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Dictation provider'),
        content: Text(
          '${settings.provider.label}\n'
          '${settings.model.label}\n'
          'Language: ${VoiceCatalog.languages[settings.language] ?? settings.language}\n'
          'Input: ${settings.inputMode}',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Close'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final settings = VoiceSettings.fromNativeJson(_config);
    final allReady = _status['microphone'] == true &&
        _status['overlay'] == true &&
        _status['accessibility'] == true &&
        _status['service'] == true;

    return Scaffold(
      appBar: AppBar(
        title: const Text('mbdictate'),
        actions: [
          IconButton(
            tooltip: 'Settings',
            icon: const Icon(Icons.settings),
            onPressed: () async {
              await Navigator.of(context).push(
                MaterialPageRoute<void>(
                  builder: (_) => const SettingsScreen(),
                ),
              );
              await _refresh();
            },
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    allReady
                        ? 'Ready to dictate'
                        : 'Complete the setup below',
                    style: Theme.of(context).textTheme.titleLarge,
                  ),
                  const SizedBox(height: 8),
                  Text(
                    'Use the floating bubble in any app. Hold to dictate, or tap to start and send.',
                  ),
                  const SizedBox(height: 12),
                  Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    children: [
                      FilledButton.icon(
                        onPressed: _busy ? null : _showInfo,
                        icon: const Icon(Icons.record_voice_over),
                        label: Text(settings.provider.label),
                      ),
                      OutlinedButton.icon(
                        onPressed: _busy ? null : _toggleService,
                        icon: Icon(
                          _on('service')
                              ? Icons.stop_circle_outlined
                              : Icons.play_circle_outline,
                        ),
                        label: Text(
                          _on('service')
                              ? 'Stop dictation service'
                              : 'Start dictation service',
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 8),
          _PermissionTile(
            icon: Icons.mic,
            title: 'Microphone',
            subtitle: 'Required to record dictation',
            ok: _on('microphone'),
            onTap: () => _run(() async {
              final granted = await NativeBridge.requestMicPermission();
              if (!granted) await NativeBridge.openAppSettings();
            }),
          ),
          _PermissionTile(
            icon: Icons.layers_outlined,
            title: 'Display over other apps',
            subtitle: 'Shows the floating bubble over other apps',
            ok: _on('overlay'),
            onTap: () => _run(NativeBridge.openOverlaySettings),
          ),
          _PermissionTile(
            icon: Icons.accessibility_new,
            title: 'Accessibility service',
            subtitle: 'Detects text fields and inserts the transcript',
            ok: _on('accessibility'),
            onTap: () => _run(NativeBridge.openAccessibilitySettings),
          ),
          _PermissionTile(
            icon: Icons.notifications_outlined,
            title: 'Notifications',
            subtitle: 'Keeps the dictation service visible',
            ok: _on('notifications'),
            onTap: () => _run(() async {
              final granted =
                  await NativeBridge.requestNotificationPermission();
              if (!granted) await NativeBridge.openNotificationSettings();
            }),
          ),
          _PermissionTile(
            icon: Icons.battery_saver,
            iconColor: Colors.orange,
            title: 'Battery optimization',
            subtitle: 'Recommended so the service is not stopped',
            ok: _on('battery'),
            onTap: () => _run(NativeBridge.openBatterySettings),
          ),
          const SizedBox(height: 16),
          Text(
            'The bubble appears only while a text field is focused. '
            'Password fields are ignored. If direct insertion fails, the text is copied to the clipboard.',
            style: Theme.of(context).textTheme.bodySmall,
          ),
        ],
      ),
    );
  }
}

class _PermissionTile extends StatelessWidget {
  const _PermissionTile({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.ok,
    required this.onTap,
    this.iconColor,
  });

  final IconData icon;
  final String title;
  final String subtitle;
  final bool ok;
  final VoidCallback onTap;
  final Color? iconColor;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: ListTile(
        leading: Icon(icon, color: iconColor),
        title: Text(title),
        subtitle: Text(subtitle),
        trailing: ok
            ? const Icon(Icons.check_circle, color: Colors.green)
            : const Icon(Icons.chevron_right),
        onTap: onTap,
      ),
    );
  }
}
