package tw.chehu.displayfaultsimulator

import android.content.Context

class LineSettings(context: Context) {
    private val preferences = context.createDeviceProtectedStorageContext()
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var serviceEnabled: Boolean
        get() = preferences.getBoolean(KEY_ENABLED, false)
        set(value) = preferences.edit().putBoolean(KEY_ENABLED, value).apply()

    var startOnBoot: Boolean
        get() = preferences.getBoolean(KEY_BOOT, true)
        set(value) = preferences.edit().putBoolean(KEY_BOOT, value).apply()

    var pendingStartAt: Long
        get() = preferences.getLong(KEY_START_AT, 0L)
        set(value) = preferences.edit().putLong(KEY_START_AT, value).apply()

    var autoStopAt: Long
        get() = preferences.getLong(KEY_STOP_AT, 0L)
        set(value) = preferences.edit().putLong(KEY_STOP_AT, value).apply()

    fun clearSchedule() {
        preferences.edit().remove(KEY_START_AT).remove(KEY_STOP_AT).apply()
    }

    internal fun rawPreferences() = preferences

    companion object {
        const val PREFS = "line_settings"
        private const val KEY_ENABLED = "service_enabled"
        private const val KEY_BOOT = "start_on_boot"
        private const val KEY_START_AT = "pending_start_at"
        private const val KEY_STOP_AT = "auto_stop_at"
    }
}
