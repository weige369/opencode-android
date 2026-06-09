package com.opencode.android.util

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * 安全偏好设置管理器
 *
 * 使用 EncryptedSharedPreferences 保护敏感数据（如服务密码）。
 * MasterKey 使用 AES-256-GCM，由 Android Keystore 硬件支持。
 */
object PreferencesManager {

    private const val PREFS_NAME = "opencode_prefs_secure"
    private const val KEY_SERVER_PORT = "server_port"
    private const val KEY_SERVER_PASSWORD = "server_password"
    private const val KEY_DEFAULT_WORK_DIR = "default_work_dir"
    private const val KEY_AUTO_START = "auto_start"
    private const val KEY_DARK_THEME = "dark_theme"
    private const val KEY_LAST_SESSION_ID = "last_session_id"

    @Volatile
    private var masterKey: MasterKey? = null

    private fun getEncryptedPrefs(context: Context): android.content.SharedPreferences {
        if (masterKey == null) {
            synchronized(this) {
                if (masterKey == null) {
                    masterKey = MasterKey.Builder(context)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build()
                }
            }
        }
        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey!!,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    // ============== Server config ==============

    fun getServerPort(context: Context): Int =
        getEncryptedPrefs(context).getInt(KEY_SERVER_PORT, 4096)

    fun setServerPort(context: Context, port: Int) {
        getEncryptedPrefs(context).edit().putInt(KEY_SERVER_PORT, port).apply()
    }

    fun getServerPassword(context: Context): String? =
        getEncryptedPrefs(context).getString(KEY_SERVER_PASSWORD, null)

    fun setServerPassword(context: Context, password: String?) {
        getEncryptedPrefs(context).edit().putString(KEY_SERVER_PASSWORD, password).apply()
    }

    fun getDefaultWorkDir(context: Context): String? =
        getEncryptedPrefs(context).getString(KEY_DEFAULT_WORK_DIR, null)

    fun setDefaultWorkDir(context: Context, dir: String) {
        getEncryptedPrefs(context).edit().putString(KEY_DEFAULT_WORK_DIR, dir).apply()
    }

    fun isAutoStart(context: Context): Boolean =
        getEncryptedPrefs(context).getBoolean(KEY_AUTO_START, false)

    fun setAutoStart(context: Context, enabled: Boolean) {
        getEncryptedPrefs(context).edit().putBoolean(KEY_AUTO_START, enabled).apply()
    }

    fun isDarkTheme(context: Context): Boolean =
        getEncryptedPrefs(context).getBoolean(KEY_DARK_THEME, true)

    fun setDarkTheme(context: Context, dark: Boolean) {
        getEncryptedPrefs(context).edit().putBoolean(KEY_DARK_THEME, dark).apply()
    }

    fun getLastSessionId(context: Context): String? =
        getEncryptedPrefs(context).getString(KEY_LAST_SESSION_ID, null)

    fun setLastSessionId(context: Context, sessionId: String) {
        getEncryptedPrefs(context).edit().putString(KEY_LAST_SESSION_ID, sessionId).apply()
    }
}