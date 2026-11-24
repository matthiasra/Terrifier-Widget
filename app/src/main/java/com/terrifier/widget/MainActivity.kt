package com.terrifier.widget

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        val etUrl = findViewById<EditText>(R.id.et_url)
        val etInterval = findViewById<EditText>(R.id.et_interval)
        val cbAlerts = findViewById<CheckBox>(R.id.cb_alerts)
        val btnSave = findViewById<Button>(R.id.btn_save_start)
        val btnStop = findViewById<Button>(R.id.btn_stop)

        etUrl.setText(Prefs.getUrl(this))
        etInterval.setText(Prefs.getIntervalSec(this).toString())
        cbAlerts.isChecked = Prefs.isAlertsEnabled(this)

        btnSave.setOnClickListener {
            val url = etUrl.text.toString().trim()
            val sec = etInterval.text.toString().toLongOrNull() ?: 2L
            Prefs.setUrl(this, url)
            Prefs.setIntervalSec(this, sec)
            Prefs.setAlertsEnabled(this, cbAlerts.isChecked)
            LiveUpdateService.start(this)
            Toast.makeText(this, "Gespeichert! Intervall: ${sec}s · Alerts: ${if (cbAlerts.isChecked) "AN" else "AUS"}", Toast.LENGTH_SHORT).show()
        }

        btnStop.setOnClickListener {
            LiveUpdateService.stop(this)
            Toast.makeText(this, "Live-Service gestoppt", Toast.LENGTH_SHORT).show()
        }
    }
}
