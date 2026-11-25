package com.terrifier.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.abs

object WidgetUpdater {

    data class Summary(
        val equity: String,
        val pnl: String,
        val state: String,
        val level: Int,
        val maxLevel: Int,
        val todayNet: String
    )

    fun fetchAndUpdate(context: Context): Summary? {
        val apiUrl = Prefs.getUrl(context)
        val jsonText = try {
            val conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 4000
                requestMethod = "GET"
                useCaches = false
            }
            if (conn.responseCode == 200) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else null
        } catch (_: Exception) {
            null
        } ?: return null

        return try {
            val obj = JSONObject(jsonText)
            val equityVal = obj.optDouble("equity", 0.0)
            val balanceVal = obj.optDouble("balance", 0.0)
            val pnlVal = obj.optDouble("pnl", 0.0)
            val liveBalVal = balanceVal + pnlVal

            val liveBalStr = String.format(Locale.US, "%.2f", liveBalVal)
            val equityStr = String.format(Locale.US, "%.2f", equityVal)

            val targetVal = obj.optDouble("target", 0.0)
            val targetStr = String.format(Locale.US, "%.2f", targetVal)
            val todayVal = obj.optDouble("today_net", 0.0)
            val state = obj.optString("state", "IDLE")
            val level = obj.optInt("level", 0)
            val maxLevel = obj.optInt("max_level", 5)
            val trend = obj.optString("trend", "-")
            val adx = obj.optString("adx", "-")
            val stFull = obj.optString("st", "")
            val timeShort = if (stFull.length >= 19) stFull.substring(11, 19) else stFull

            val cfg = obj.optJSONObject("cfg")
            val s2Enabled = cfg?.optBoolean("t_2s", false) ?: false
            val s2Trigger = cfg?.optDouble("t_s2", 3.5) ?: 3.5

            val pnlStr = formatEuro(pnlVal)
            val todayStr = formatEuro(todayVal)

            val trailTag = when {
                level > 0 && s2Enabled && pnlVal >= s2Trigger -> "TRL S2"
                level > 0 && targetVal > 0 && pnlVal >= targetVal -> "TRL S1"
                else -> "Ziel +€$targetStr"
            }

            val tradesBuilder = StringBuilder()
            val tradesArr = obj.optJSONArray("trades")
            if (level == 0 || tradesArr == null || tradesArr.length() == 0) {
                tradesBuilder.append("Keine Positionen")
            } else {
                val count = minOf(tradesArr.length(), 6)
                for (i in 0 until count) {
                    val t = tradesArr.getJSONObject(i)
                    val tType = t.optString("type", "")
                    val tLot = t.optString("lot", "")
                    val tPnl = formatEuro(t.optDouble("pnl", 0.0))
                    val tPips = t.optString("pips", "0")
                    if (i > 0) tradesBuilder.append("\n")
                    tradesBuilder.append("$tType ${tLot}L $tPnl (${tPips}p)")
                }
            }

            val views = RemoteViews(context.packageName, R.layout.widget_terrifier)
            views.setTextViewText(R.id.tv_equity, "€$liveBalStr")
            views.setTextViewText(R.id.tv_pnl, pnlStr)
            views.setTextViewText(R.id.tv_bal_today, "Eq €$equityStr · Heute $todayStr")
            views.setTextViewText(R.id.tv_status_line, "$state · Lvl $level/$maxLevel · $trend ($adx)\n$timeShort")
            views.setTextViewText(R.id.tv_trades_header, "TRADES ($level) · $trailTag")
            views.setTextViewText(R.id.tv_trades_list, tradesBuilder.toString())

            val dotRes = when (state.uppercase()) {
                "HUNTING" -> R.drawable.dot_green
                "WAITING" -> R.drawable.dot_orange
                "ADX PAUSE", "PROFIT HIT" -> R.drawable.dot_yellow
                "EMERGENCY", "NEWS CLOSE" -> R.drawable.dot_red
                else -> R.drawable.dot_blue
            }
            views.setImageViewResource(R.id.iv_status_dot, dotRes)

            val pillRes = when {
                pnlVal > 0 -> R.drawable.pill_green
                pnlVal < 0 -> R.drawable.pill_red
                else -> R.drawable.pill_neutral
            }
            views.setInt(R.id.tv_pnl, "setBackgroundResource", pillRes)

            // Tap Left -> Open Web-Terminal in Browser
            val dashIntent = Intent(Intent.ACTION_VIEW, Uri.parse(Prefs.getDashboardUrl(context))).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val dashPi = PendingIntent.getActivity(
                context, 1, dashIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.col_left, dashPi)

            // Tap Right -> Instant Refresh
            val refreshIntent = Intent(context, TerrifierWidgetProvider::class.java).apply {
                action = TerrifierWidgetProvider.ACTION_REFRESH
            }
            val refreshPi = PendingIntent.getBroadcast(
                context, 2, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.col_right, refreshPi)

            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, TerrifierWidgetProvider::class.java))
            for (id in ids) {
                mgr.updateAppWidget(id, views)
            }

            Summary("€$liveBalStr", pnlStr, state, level, maxLevel, todayStr)
        } catch (_: Exception) {
            null
        }
    }

    private fun formatEuro(v: Double): String {
        val absStr = if (abs(v) % 1.0 == 0.0) abs(v).toInt().toString() else String.format(Locale.US, "%.2f", abs(v))
        return when {
            v > 0 -> "+€$absStr"
            v < 0 -> "-€$absStr"
            else -> "€0"
        }
    }
}
