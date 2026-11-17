package com.example.mbdictate.storage

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SecureKeyStore {
    private const val TAG = "SecureKeyStore"
    private const val PREFS = "mbdictate_secure"
    private const val PREFIX = "api_key_"

    private fun prefs(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                PREFS,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Encrypted storage unavailable; using private prefs", e)
            context.getSharedPreferences("${PREFS}_fallback", Context.MODE_PRIVATE)
        }
    }

    fun get(context: Context, providerId: String): String? {
        return prefs(context).getString(PREFIX + providerId, null)
            ?.takeIf { it.isNotBlank() }
    }

    fun set(context: Context, providerId: String, value: String) {
        val editor = prefs(context).edit()
        if (value.isBlank()) {
            editor.remove(PREFIX + providerId)
        } else {
            editor.putString(PREFIX + providerId, value.trim())
        }
        editor.apply()
    }
}
