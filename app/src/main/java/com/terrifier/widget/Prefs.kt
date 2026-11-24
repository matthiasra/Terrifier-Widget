package com.terrifier.widget

import android.content.Context

object Prefs {
    private const val NAME = "terrifier_prefs"
    private const val KEY_URL = "api_url"
    private const val KEY_INTERVAL = "interval_sec"
    private const val KEY_ALERTS = "alerts_enabled"
    private const val KEY_RUNNING = "service_running"
    private const val DEFAULT_URL = "https://nachname.me/index.php?api=data"

    fun getUrl(ctx: Context): String {
        return ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getString(KEY_URL, DEFAULT_URL) ?: DEFAULT_URL
    }

    fun getDashboardUrl(ctx: Context): String {
        val u = getUrl(ctx)
        val idx = u.indexOf("?")
        return if (idx > 0) u.substring(0, idx) else u
    }

    fun setUrl(ctx: Context, url: String) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_URL, url.trim()).apply()
    }

    fun getIntervalSec(ctx: Context): Long {
        return ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getLong(KEY_INTERVAL, 2L).coerceIn(1L, 60L)
    }

    fun setIntervalSec(ctx: Context, sec: Long) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putLong(KEY_INTERVAL, sec.coerceIn(1L, 60L)).apply()
    }

    fun isAlertsEnabled(ctx: Context): Boolean {
        return ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ALERTS, true)
    }

    fun setAlertsEnabled(ctx: Context, enabled: Boolean) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ALERTS, enabled).apply()
    }

    fun isServiceRunning(ctx: Context): Boolean {
        return ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_RUNNING, true)
    }

    fun setServiceRunning(ctx: Context, running: Boolean) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_RUNNING, running).apply()
    }
}
