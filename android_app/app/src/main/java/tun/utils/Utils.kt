package tun.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class Utils(context: Context) {
    val sharedPreferences: SharedPreferences

    init {
        val masterKey = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        sharedPreferences = EncryptedSharedPreferences.create(
            "vpnconfig_encrypted",
            masterKey,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        migrateFromPlain(context)
    }

    private fun migrateFromPlain(context: Context) {
        val old = context.getSharedPreferences("vpnconfig", Context.MODE_PRIVATE)
        if (old.contains("vpnStatus")) {
            sharedPreferences.edit()
                .putBoolean("vpnStatus", old.getBoolean("vpnStatus", false))
                .putString("proxyName", old.getString("proxyName", ""))
                .apply()
            old.edit().clear().apply()
        }
    }

    fun setVpnStatus(status: Boolean) {
        sharedPreferences.edit().putBoolean("vpnStatus", status).apply()
    }

    fun getVpnStatus(): Boolean {
        return sharedPreferences.getBoolean("vpnStatus", false)
    }

    fun setProxyName(name: String) {
        sharedPreferences.edit().putString("proxyName", name).apply()
    }
}
