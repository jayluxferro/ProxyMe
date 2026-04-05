package tun.proxy.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.content.SharedPreferences
import android.os.SystemClock
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import tun.proxy.model.ProxyConfig
import tun.proxy.repository.ConfigRepository

data class RotationState(
    val enabled: Boolean = false,
    val configIds: List<String> = emptyList(),
    val intervalMinutes: Int = 30,
    val currentIndex: Int = 0,
    val nextRotationTime: Long = 0
)

class ProxyRotationManager(private val context: Context) {
    private val TAG = "ProxyRotationManager"
    private val gson = Gson()
    private val prefs: SharedPreferences
    private val configRepository = ConfigRepository(context)

    companion object {
        const val ACTION_ROTATE = "tun.proxy.ACTION_ROTATE_PROXY"
        private const val PREFS_NAME = "proxy_rotation_encrypted"
        private const val KEY_STATE = "rotation_state"
        private const val REQUEST_CODE = 9999

        val INTERVAL_OPTIONS = listOf(5, 10, 15, 30, 60, 120, 240)
    }

    init {
        val masterKey = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        prefs = EncryptedSharedPreferences.create(
            PREFS_NAME, masterKey, context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getState(): RotationState {
        val json = prefs.getString(KEY_STATE, null) ?: return RotationState()
        return try {
            gson.fromJson(json, RotationState::class.java) ?: RotationState()
        } catch (e: Exception) {
            RotationState()
        }
    }

    fun saveState(state: RotationState) {
        prefs.edit().putString(KEY_STATE, gson.toJson(state)).apply()
    }

    fun getRotationConfigs(): List<ProxyConfig> {
        val state = getState()
        val allConfigs = configRepository.getAll()
        return state.configIds.mapNotNull { id -> allConfigs.find { it.id == id } }
    }

    fun getNextConfig(): ProxyConfig? {
        val state = getState()
        val configs = getRotationConfigs()
        if (configs.isEmpty()) return null
        val nextIndex = (state.currentIndex + 1) % configs.size
        saveState(state.copy(currentIndex = nextIndex))
        return configs[nextIndex]
    }

    fun getCurrentConfig(): ProxyConfig? {
        val state = getState()
        val configs = getRotationConfigs()
        if (configs.isEmpty()) return null
        val index = state.currentIndex.coerceIn(0, configs.size - 1)
        return configs[index]
    }

    fun enable(configIds: List<String>, intervalMinutes: Int) {
        val state = RotationState(
            enabled = true,
            configIds = configIds,
            intervalMinutes = intervalMinutes,
            currentIndex = 0,
            nextRotationTime = System.currentTimeMillis() + intervalMinutes * 60_000L
        )
        saveState(state)
        scheduleNextRotation(intervalMinutes)
        Log.d(TAG, "Rotation enabled: ${configIds.size} configs, every ${intervalMinutes}min")
    }

    fun disable() {
        val state = getState()
        saveState(state.copy(enabled = false, currentIndex = 0, nextRotationTime = 0))
        cancelAlarm()
        Log.d(TAG, "Rotation disabled")
    }

    fun scheduleNextRotation(intervalMinutes: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(ACTION_ROTATE).apply { setPackage(context.packageName) }
        val pendingIntent = PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerAt = SystemClock.elapsedRealtime() + intervalMinutes * 60_000L

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                // Fallback to inexact alarm
                alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm not permitted, using inexact", e)
            alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
        }

        val state = getState()
        saveState(state.copy(nextRotationTime = System.currentTimeMillis() + intervalMinutes * 60_000L))
    }

    private fun cancelAlarm() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(ACTION_ROTATE).apply { setPackage(context.packageName) }
        val pendingIntent = PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
}
