package com.security.app

import android.content.Context
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class AlarmFullscreenActivity : AppCompatActivity() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    companion object {
        // simple in-memory rate-limit to avoid relaunch storms
        var lastAlarmZone: String? = null
        var lastAlarmTimeMs: Long = 0
        const val MIN_RESTART_MS = 30_000L // 30 seconds
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Make activity full-screen and show over lockscreen
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                    or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        setContentView(R.layout.activity_alarm)

        val zone = intent.getStringExtra("zone") ?: "Unknown"
        val messageView = findViewById<TextView>(R.id.alarmZoneText)
        messageView.text = "BREACH: $zone"

        // Play system alarm (fallback to notification)
        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            mediaPlayer = MediaPlayer.create(this, alarmUri)
            mediaPlayer?.isLooping = true
            mediaPlayer?.start()
        } catch (e: Exception) {
            mediaPlayer = null
        }

        // Vibrate continuously in a pattern
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        try {
            if (vibrator != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 250, 500), 0))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(longArrayOf(0, 500, 250, 500), 0)
                }
            }
        } catch (ignored: Exception) {
        }

        findViewById<Button>(R.id.dismissAlarmButton).setOnClickListener {
            stopAlarmAndFinish()
        }
    }

    private fun stopAlarmAndFinish() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (ignored: Exception) {
        }
        try {
            vibrator?.cancel()
        } catch (ignored: Exception) {
        }
        // reset rate-limit so a future alarm can be triggered
        lastAlarmZone = null
        lastAlarmTimeMs = 0
        finish()
    }

    override fun onDestroy() {
        try {
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (ignored: Exception) {
        }
        try {
            vibrator?.cancel()
        } catch (ignored: Exception) {
        }
        super.onDestroy()
    }

    override fun onBackPressed() {
        // prevent back from dismissing the alarm accidentally
    }
}

