package tun.proxy.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import tun.proxy.model.ProxyConfig

class ConfigRepository(context: Context) {
    private val prefs: SharedPreferences
    private val gson = Gson()
    private val key = "saved_proxy_configs"

    init {
        val masterKey = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        prefs = EncryptedSharedPreferences.create(
            "proxy_configs_encrypted",
            masterKey,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        migrateFromPlainPrefs(context)
    }

    private fun migrateFromPlainPrefs(context: Context) {
        val oldPrefs = context.getSharedPreferences("proxy_configs", Context.MODE_PRIVATE)
        val oldJson = oldPrefs.getString(key, null)
        if (oldJson != null) {
            prefs.edit().putString(key, oldJson).apply()
            oldPrefs.edit().clear().apply()
        }
    }

    fun getAll(): List<ProxyConfig> {
        val json = prefs.getString(key, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<ProxyConfig>>() {}.type
            val list: List<ProxyConfig>? = gson.fromJson(json, type)
            @Suppress("SENSELESS_COMPARISON")
            list?.filter { config ->
                config.id != null && config.name != null &&
                    config.protocol != null && config.host != null
            } ?: emptyList()
        } catch (e: Exception) {
            Log.w("ConfigRepository", "Failed to parse saved configs", e)
            emptyList()
        }
    }

    fun save(config: ProxyConfig) {
        val configs = getAll().toMutableList()
        configs.add(config)
        prefs.edit().putString(key, gson.toJson(configs)).apply()
    }

    fun update(config: ProxyConfig) {
        val configs = getAll().map { if (it.id == config.id) config else it }
        prefs.edit().putString(key, gson.toJson(configs)).apply()
    }

    fun delete(id: String) {
        val configs = getAll().filter { it.id != id }
        prefs.edit().putString(key, gson.toJson(configs)).apply()
    }

    fun getById(id: String): ProxyConfig? {
        return getAll().find { it.id == id }
    }
}
