package com.terrifier.widget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class LiveUpdateService : Service() {

    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private var pollTask: ScheduledFuture<*>? = null
    private var lastAlertLevel = 0
    private var lastAlertState = ""

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> startPolling()
                Intent.ACTION_SCREEN_OFF -> stopPolling()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannels()
        startForeground(NOTIF_ID, buildLiveNotification("Terrifier Live", "Smart-Polling aktiv"))

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenReceiver, filter)

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isInteractive) startPolling()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Prefs.setServiceRunning(this, true)
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isInteractive) startPolling()
        return START_STICKY
    }

    private fun startPolling() {
        stopPolling()
        val intervalSec = Prefs.getIntervalSec(this)
        pollTask = scheduler.scheduleWithFixedDelay({
            val s = WidgetUpdater.fetchAndUpdate(applicationContext)
            if (s != null) {
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(
                    NOTIF_ID,
                    buildLiveNotification(
                        "${s.equity} (${s.pnl}) · Heute ${s.todayNet}",
                        "${s.state} · Grid ${s.level}/${s.maxLevel}"
                    )
                )
                checkAlerts(nm, s)
            }
        }, 0, intervalSec, TimeUnit.SECONDS)
    }

    private fun checkAlerts(nm: NotificationManager, s: WidgetUpdater.Summary) {
        if (!Prefs.isAlertsEnabled(this)) return

        // Grid Level >= 4 Alert (triggers once per escalation)
        if (s.level >= 4 && s.level > lastAlertLevel) {
            sendAlert(nm, 2001, "⚠️ Terrifier Grid Level ${s.level}/${s.maxLevel}", "Floating PnL: ${s.pnl} · Equity: ${s.equity}")
        }
        lastAlertLevel = s.level

        // Critical State Alerts (triggers once per state change)
        val st = s.state.uppercase()
        if (st != lastAlertState) {
            when (st) {
                "PROFIT HIT" -> sendAlert(nm, 2002, "🏆 Tagesziel erreicht!", "Heute: ${s.todayNet} · Equity: ${s.equity}")
                "EMERGENCY" -> sendAlert(nm, 2003, "🚨 EMERGENCY STOP!", "Drawdown-Limit ausgelöst · Equity: ${s.equity}")
                "NEWS CLOSE" -> sendAlert(nm, 2004, "📰 News Panic Close", "Alle Positionen vor High-Impact News geschlossen.")
            }
            lastAlertState = st
        }
    }

    private fun sendAlert(nm: NotificationManager, id: Int, title: String, text: String) {
        val notif = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        nm.notify(id, notif)
    }

    private fun stopPolling() {
        pollTask?.cancel(false)
        pollTask = null
    }

    override fun onDestroy() {
        stopPolling()
        scheduler.shutdownNow()
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val liveCh = NotificationChannel(CHANNEL_ID, "Terrifier Live Status", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            }
            val alertCh = NotificationChannel(ALERT_CHANNEL_ID, "Terrifier Trading Alerts", NotificationManager.IMPORTANCE_HIGH)
            nm.createNotificationChannel(liveCh)
            nm.createNotificationChannel(alertCh)
        }
    }

    private fun buildLiveNotification(title: String, text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "terrifier_live_ch"
        private const val ALERT_CHANNEL_ID = "terrifier_alert_ch"
        private const val NOTIF_ID = 1001

        fun start(context: Context) {
            val i = Intent(context, LiveUpdateService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }

        fun stop(context: Context) {
            Prefs.setServiceRunning(context, false)
            context.stopService(Intent(context, LiveUpdateService::class.java))
        }
    }
}
