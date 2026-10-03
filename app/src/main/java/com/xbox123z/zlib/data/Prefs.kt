package com.xbox123z.zlib.data

import android.content.Context
import android.content.SharedPreferences

class Prefs(context: Context) {

    private val sp: SharedPreferences =
        context.getSharedPreferences("zlib_prefs", Context.MODE_PRIVATE)

    var domain: String
        get() = sp.getString(KEY_DOMAIN, DEFAULT_DOMAIN) ?: DEFAULT_DOMAIN
        set(v) = sp.edit().putString(KEY_DOMAIN, v.trim().trimEnd('/')).apply()

    var proxy: String
        get() = sp.getString(KEY_PROXY, "") ?: ""
        set(v) = sp.edit().putString(KEY_PROXY, v.trim()).apply()

    var userId: String
        get() = sp.getString(KEY_USER_ID, "") ?: ""
        set(v) = sp.edit().putString(KEY_USER_ID, v).apply()

    var userKey: String
        get() = sp.getString(KEY_USER_KEY, "") ?: ""
        set(v) = sp.edit().putString(KEY_USER_KEY, v).apply()

    var email: String
        get() = sp.getString(KEY_EMAIL, "") ?: ""
        set(v) = sp.edit().putString(KEY_EMAIL, v).apply()

    var theme: String
        get() = sp.getString(KEY_THEME, "auto") ?: "auto"
        set(v) = sp.edit().putString(KEY_THEME, v).apply()

    var kindleTo: String
        get() = sp.getString(KEY_KINDLE_TO, "") ?: ""
        set(v) = sp.edit().putString(KEY_KINDLE_TO, v.trim()).apply()

    var kindleFrom: String
        get() = sp.getString(KEY_KINDLE_FROM, "") ?: ""
        set(v) = sp.edit().putString(KEY_KINDLE_FROM, v.trim()).apply()

    var kindleHost: String
        get() = sp.getString(KEY_KINDLE_HOST, "") ?: ""
        set(v) = sp.edit().putString(KEY_KINDLE_HOST, v.trim()).apply()

    var kindlePort: Int
        get() = sp.getInt(KEY_KINDLE_PORT, 465)
        set(v) = sp.edit().putInt(KEY_KINDLE_PORT, v).apply()

    var kindlePassword: String
        get() = sp.getString(KEY_KINDLE_PWD, "") ?: ""
        set(v) = sp.edit().putString(KEY_KINDLE_PWD, v).apply()

    var saveKindlePassword: Boolean
        get() = sp.getBoolean(KEY_SAVE_KINDLE_PWD, false)
        set(v) {
            sp.edit().putBoolean(KEY_SAVE_KINDLE_PWD, v).apply()
            if (!v) kindlePassword = ""
        }

    val isLoggedIn: Boolean get() = userId.isNotEmpty() && userKey.isNotEmpty()

    fun saveSession(userId: String, userKey: String, domain: String) {
        sp.edit()
            .putString(KEY_USER_ID, userId)
            .putString(KEY_USER_KEY, userKey)
            .putString(KEY_DOMAIN, domain.trimEnd('/'))
            .apply()
    }

    fun logout() {
        sp.edit().remove(KEY_USER_ID).remove(KEY_USER_KEY).apply()
    }

    companion object {
        private const val KEY_DOMAIN = "domain"
        private const val KEY_PROXY = "proxy"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_KEY = "user_key"
        private const val KEY_EMAIL = "email"
        private const val KEY_THEME = "theme"
        private const val KEY_KINDLE_TO = "kindle_to"
        private const val KEY_KINDLE_FROM = "kindle_from"
        private const val KEY_KINDLE_HOST = "kindle_host"
        private const val KEY_KINDLE_PORT = "kindle_port"
        private const val KEY_KINDLE_PWD = "kindle_pwd"
        private const val KEY_SAVE_KINDLE_PWD = "save_kindle_pwd"
    }
}
