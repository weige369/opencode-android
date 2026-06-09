package com.opencode.android.util

import android.content.Context
import android.content.SharedPreferences

/**
 * 轻量级偏好设置管理器
 */
object PreferencesManager {

    private const val PREFS_NAME = "opencode_prefs"
    private const val KEY_SERVER_PORT = "server_port"
    private const val KEY_SERVER_PASSWORD = "server_password"
    private const val KEY_DEFAULT_WORK_DIR = "default_work_dir"
    private const val KEY_AUTO_START = "auto_start"
    private const val KEY_DARK_THEME = "dark_theme"
    private const val KEY_LAST_SESSION_ID = "last_session_id"

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Server config
    fun getServerPort(context: Context): Int =
        getPrefs(context).getInt(KEY_SERVER_PORT, 4096)

    fun setServerPort(context: Context, port: Int) {
        getPrefs(context).edit().putInt(KEY_SERVER_PORT, port).apply()
    }

    fun getServerPassword(context: Context): String? =
        getPrefs(context).getString(KEY_SERVER_PASSWORD, null)

    fun setServerPassword(context: Context, password: String?) {
        getPrefs(context).edit().putString(KEY_SERVER_PASSWORD, password).apply()
    }

    fun getDefaultWorkDir(context: Context): String? =
        getPrefs(context).getString(KEY_DEFAULT_WORK_DIR, null)

    fun setDefaultWorkDir(context: Context, dir: String) {
        getPrefs(context).edit().putString(KEY_DEFAULT_WORK_DIR, dir).apply()
    }

    fun isAutoStart(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_AUTO_START, false)

    fun setAutoStart(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_START, enabled).apply()
    }

    fun isDarkTheme(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_DARK_THEME, true)

    fun setDarkTheme(context: Context, dark: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_DARK_THEME, dark).apply()
    }

    fun getLastSessionId(context: Context): String? =
        getPrefs(context).getString(KEY_LAST_SESSION_ID, null)

    fun setLastSessionId(context: Context, sessionId: String) {
        getPrefs(context).edit().putString(KEY_LAST_SESSION_ID, sessionId).apply()
    }
}