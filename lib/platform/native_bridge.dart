import 'package:flutter/services.dart';

class NativeBridge {
  NativeBridge._();

  static const _channel = MethodChannel('com.example.mbdictate/native');

  static Future<Map<String, dynamic>> getStatus() async {
    try {
      final result = await _channel.invokeMapMethod<String, dynamic>('getStatus');
      return result ?? const {};
    } catch (_) {
      return const {};
    }
  }

  static Future<bool> requestMicPermission() async {
    try {
      return await _channel.invokeMethod<bool>('requestMicPermission') ?? false;
    } catch (_) {
      return false;
    }
  }

  static Future<bool> requestNotificationPermission() async {
    try {
      return await _channel.invokeMethod<bool>('requestNotificationPermission') ??
          false;
    } catch (_) {
      return false;
    }
  }

  static Future<void> openOverlaySettings() =>
      _channel.invokeMethod<void>('openOverlaySettings');

  static Future<void> openAccessibilitySettings() =>
      _channel.invokeMethod<void>('openAccessibilitySettings');

  static Future<void> openNotificationSettings() =>
      _channel.invokeMethod<void>('openNotificationSettings');

  static Future<void> openBatterySettings() =>
      _channel.invokeMethod<void>('openBatterySettings');

  static Future<void> openAppSettings() =>
      _channel.invokeMethod<void>('openAppSettings');

  static Future<Map<String, dynamic>> loadConfig() async {
    final result = await _channel.invokeMapMethod<String, dynamic>('getConfig');
    return result ?? const {};
  }

  static Future<void> saveConfig(Map<String, dynamic> config) =>
      _channel.invokeMethod<void>('setConfig', {'config': config});

  static Future<String?> getApiKey(String providerId) =>
      _channel.invokeMethod<String>('getApiKey', {'providerId': providerId});

  static Future<void> setApiKey(String providerId, String key) =>
      _channel.invokeMethod<void>(
        'setApiKey',
        {'providerId': providerId, 'key': key},
      );

  static Future<bool> startService() async {
    try {
      return await _channel.invokeMethod<bool>('startService') ?? false;
    } catch (_) {
      return false;
    }
  }

  static Future<void> stopService() =>
      _channel.invokeMethod<void>('stopService');
}
