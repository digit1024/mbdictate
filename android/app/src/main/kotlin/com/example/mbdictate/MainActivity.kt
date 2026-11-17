package com.example.mbdictate

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.mbdictate.storage.ConfigStore
import com.example.mbdictate.storage.SecureKeyStore
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : FlutterActivity() {
    companion object {
        private const val CHANNEL = "com.example.mbdictate/native"
        private const val REQUEST_MIC = 1001
        private const val REQUEST_NOTIFICATIONS = 1002
    }

    private var pendingResult: MethodChannel.Result? = null
    private var pendingType: String? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            CHANNEL,
        ).setMethodCallHandler { call, result ->
            try {
                when (call.method) {
                    "getStatus" -> result.success(statusMap())
                    "requestMicPermission" -> requestMic(result)
                    "requestNotificationPermission" -> requestNotifications(result)
                    "openOverlaySettings" -> {
                        openSettings(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:$packageName"),
                            ),
                        )
                        result.success(null)
                    }
                    "openAccessibilitySettings" -> {
                        openSettings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        result.success(null)
                    }
                    "openNotificationSettings" -> {
                        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                        } else {
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:$packageName"),
                            )
                        }
                        openSettings(intent)
                        result.success(null)
                    }
                    "openBatterySettings" -> {
                        openSettings(
                            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                        )
                        result.success(null)
                    }
                    "openAppSettings" -> {
                        openSettings(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:$packageName"),
                            ),
                        )
                        result.success(null)
                    }
                    "getConfig" -> {
                        val json = try {
                            JSONObject(ConfigStore.loadRaw(this))
                        } catch (_: Exception) {
                            JSONObject()
                        }
                        result.success(jsonToMap(json))
                    }
                    "setConfig" -> {
                        val config = call.argument<Map<String, Any?>>("config")
                        if (config == null) {
                            result.error("bad_args", "Missing config", null)
                        } else {
                            ConfigStore.save(this, JSONObject(config))
                            result.success(null)
                        }
                    }
                    "getApiKey" -> {
                        val providerId = call.argument<String>("providerId") ?: ""
                        result.success(SecureKeyStore.get(this, providerId))
                    }
                    "setApiKey" -> {
                        val providerId = call.argument<String>("providerId") ?: ""
                        val key = call.argument<String>("key") ?: ""
                        SecureKeyStore.set(this, providerId, key)
                        result.success(null)
                    }
                    "startService" -> {
                        if (ContextCompat.checkSelfPermission(
                                this,
                                Manifest.permission.RECORD_AUDIO,
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            result.success(false)
                        } else {
                            DictationService.ensureRunning(this)
                            result.success(true)
                        }
                    }
                    "stopService" -> {
                        DictationService.stop(this)
                        result.success(null)
                    }
                    else -> result.notImplemented()
                }
            } catch (e: Exception) {
                result.error("native_error", e.message, null)
            }
        }
    }

    private fun statusMap(): Map<String, Any> {
        val notificationsEnabled =
            getSystemService(NotificationManager::class.java).areNotificationsEnabled()
        val powerManager = getSystemService(PowerManager::class.java)
        return mapOf(
            "microphone" to (
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.RECORD_AUDIO,
                ) == PackageManager.PERMISSION_GRANTED
                ),
            "overlay" to Settings.canDrawOverlays(this),
            "accessibility" to isAccessibilityEnabled(),
            "notifications" to notificationsEnabled,
            "battery" to powerManager.isIgnoringBatteryOptimizations(packageName),
            "service" to DictationService.isRunning,
        )
    }

    private fun isAccessibilityEnabled(): Boolean {
        val manager = getSystemService(AccessibilityManager::class.java)
        val component = ComponentName(this, DictateAccessibilityService::class.java)
        return manager.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK,
        ).any { info ->
            val serviceInfo = info.resolveInfo.serviceInfo
            ComponentName(serviceInfo.packageName, serviceInfo.name) == component
        }
    }

    private fun requestMic(result: MethodChannel.Result) {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            result.success(true)
            return
        }
        if (pendingResult != null) {
            result.success(false)
            return
        }
        pendingResult = result
        pendingType = "mic"
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            REQUEST_MIC,
        )
    }

    private fun requestNotifications(result: MethodChannel.Result) {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.areNotificationsEnabled()) {
            result.success(true)
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            result.success(false)
            return
        }
        if (pendingResult != null) {
            result.success(false)
            return
        }
        pendingResult = result
        pendingType = "notifications"
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            REQUEST_NOTIFICATIONS,
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val result = pendingResult ?: return
        pendingResult = null
        val type = pendingType
        pendingType = null
        val granted = grantResults.isNotEmpty() &&
            grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        result.success(
            if (type == "notifications") {
                getSystemService(NotificationManager::class.java).areNotificationsEnabled()
            } else {
                granted
            },
        )
    }

    private fun openSettings(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: Exception) {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName"),
                ),
            )
        }
    }

    private fun jsonToMap(json: JSONObject): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        val iterator = json.keys()
        while (iterator.hasNext()) {
            val key = iterator.next()
            map[key] = when (val value = json.opt(key)) {
                null, JSONObject.NULL -> null
                is JSONObject -> jsonToMap(value)
                is JSONArray -> (0 until value.length()).map { index ->
                    when (val item = value.opt(index)) {
                        null, JSONObject.NULL -> null
                        is JSONObject -> jsonToMap(item)
                        is JSONArray -> (0 until item.length()).map { item.opt(it) }
                        else -> item
                    }
                }
                else -> value
            }
        }
        return map
    }
}
