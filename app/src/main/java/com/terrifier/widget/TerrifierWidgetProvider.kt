package com.terrifier.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import java.util.concurrent.Executors

class TerrifierWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        triggerRefresh(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        LiveUpdateService.start(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        LiveUpdateService.stop(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            LiveUpdateService.start(context)
            triggerRefresh(context)
        }
    }

    private fun triggerRefresh(context: Context) {
        Executors.newSingleThreadExecutor().execute {
            WidgetUpdater.fetchAndUpdate(context.applicationContext)
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.terrifier.widget.ACTION_REFRESH"
    }
}
