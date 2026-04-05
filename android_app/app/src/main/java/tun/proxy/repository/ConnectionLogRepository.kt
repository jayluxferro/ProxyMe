package tun.proxy.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import tun.proxy.model.ConnectionEvent

class ConnectionLogRepository(context: Context) {
    private val prefs: SharedPreferences
    private val gson = Gson()
    private val key = "connection_log"

    init {
        val masterKey = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        prefs = EncryptedSharedPreferences.create(
            "connection_log_encrypted",
            masterKey,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getAll(): List<ConnectionEvent> {
        val json = prefs.getString(key, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<ConnectionEvent>>() {}.type
            gson.fromJson<List<ConnectionEvent>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun log(event: ConnectionEvent) {
        val events = getAll().toMutableList()
        events.add(0, event)
        // Keep only the last 50 entries
        val trimmed = if (events.size > 50) events.take(50) else events
        prefs.edit().putString(key, gson.toJson(trimmed)).apply()
    }

    fun clear() {
        prefs.edit().remove(key).apply()
    }
}
