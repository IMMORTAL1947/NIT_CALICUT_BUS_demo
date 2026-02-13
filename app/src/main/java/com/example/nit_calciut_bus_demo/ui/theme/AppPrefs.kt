package com.example.nit_calciut_bus_demo.ui.theme

import android.content.Context

object AppPrefs {
    private const val PREFS = "app_prefs"
    private const val KEY_COLLEGE_CODE = "college_code"
    private const val KEY_SERVER_URL = "server_url"

    fun getCollegeCode(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_COLLEGE_CODE, null)

    fun setCollegeCode(context: Context, code: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_COLLEGE_CODE, code.trim()).apply()
    }

    fun getServerUrl(context: Context): String =
        normalizeServerUrl(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_SERVER_URL, defaultServer()) ?: defaultServer()
        )

    fun setServerUrl(context: Context, url: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_SERVER_URL, normalizeServerUrl(url)).apply()
    }

    private fun defaultServer(): String = "http://10.0.2.2:3000"

    private fun normalizeServerUrl(url: String): String {
        var u = url.trim().removeSuffix("/")
        if (u.endsWith("/api", ignoreCase = true)) {
            u = u.dropLast(4).removeSuffix("/")
        }
        return u
    }
}
