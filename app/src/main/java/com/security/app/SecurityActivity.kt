package com.security.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class SecurityActivity : AppCompatActivity() {
    
    private lateinit var db: FirebaseFirestore
    private lateinit var systemStatusText: TextView
    private lateinit var garageStatus: TextView
    private lateinit var garageSideStatus: TextView
    private lateinit var southStatus: TextView
    private lateinit var backStatus: TextView
    private lateinit var northStatus: TextView
    private lateinit var frontStatus: TextView
    private lateinit var recentActivityText: TextView

    private val CHANNEL_ID = "security_alerts"
    private val NOTIF_ID = 1001

    companion object {
        private const val REQ_POST_NOTIF = 2001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_security)
        
        // Initialize Firebase
        db = FirebaseFirestore.getInstance()
        
        // Initialize views
        systemStatusText = findViewById(R.id.systemStatusText)
        garageStatus = findViewById(R.id.garageStatus)
        garageSideStatus = findViewById(R.id.garageSideStatus)
        southStatus = findViewById(R.id.southStatus)
        backStatus = findViewById(R.id.backStatus)
        northStatus = findViewById(R.id.northStatus)
        frontStatus = findViewById(R.id.frontStatus)
        recentActivityText = findViewById(R.id.recentActivityText)
        
        // Setup back button
        findViewById<TextView>(R.id.securityBackButton).setOnClickListener {
            finish()
        }

        createNotificationChannel()

        // Request runtime POST_NOTIFICATIONS permission on Android 13+
        requestNotificationPermissionIfNeeded()

        // Listen for real-time security updates
        listenForSecurityUpdates()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                Log.d("SecurityActivity", "Requesting POST_NOTIFICATIONS permission")
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_POST_NOTIF)
            } else {
                Log.d("SecurityActivity", "POST_NOTIFICATIONS already granted")
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_POST_NOTIF) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d("SecurityActivity", "POST_NOTIFICATIONS granted by user")
            } else {
                Log.w("SecurityActivity", "POST_NOTIFICATIONS denied by user; alerts may be suppressed")
            }
        }
    }

    private fun listenForSecurityUpdates() {
        db.collection("security sensors").document("live_status")
            .addSnapshotListener { documentSnapshot, e ->
                if (e != null) {
                    Log.w("SecurityActivity", "Listen failed.", e)
                    return@addSnapshotListener
                }
                
                if (documentSnapshot != null && documentSnapshot.exists()) {
                    updateSecurityDisplay(documentSnapshot.data ?: emptyMap())
                }
            }
    }
    
    private fun updateSecurityDisplay(data: Map<String, Any>) {
        val zones = mapOf(
            "Garage" to Pair(listOf("garage_motion", "garage_sensor"), garageStatus),
            "Garage Side" to Pair(listOf("garage_side_motion", "garage_side_sensor"), garageSideStatus),
            "South" to Pair(listOf("south_motion", "south_sensor"), southStatus),
            "Back" to Pair(listOf("back_motion", "back_sensor"), backStatus),
            "North" to Pair(listOf("north_motion", "north_sensor"), northStatus),
            "Front" to Pair(listOf("front_motion", "front_sensor"), frontStatus)
        )
        
        var activeZones = 0
        val activityLog = mutableListOf<String>()
        val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

        // Collect UI updates first (so we can apply them on the UI thread)
        val zoneUiUpdates = mutableListOf<Triple<TextView, String, Int>>()

        // Track whether we should show a full-screen alarm and which zone triggered it
        var alarmZoneName: String? = null

        zones.forEach { (zoneName, zoneData) ->
            val (sensors, statusView) = zoneData
            val sensor1Raw = data[sensors[0]]
            val sensor2Raw = data[sensors[1]]

            val sensor1 = sensorValueToBoolean(sensor1Raw)
            val sensor2 = sensorValueToBoolean(sensor2Raw)

            when {
                sensor1 && sensor2 -> {
                    zoneUiUpdates.add(Triple(statusView, "🔴 BREACH!", android.R.color.holo_red_light))
                    activeZones++ // dual-sensor breach counts as active zone
                    activityLog.add("[$currentTime] $zoneName: BOTH SENSORS ACTIVE")
                    // mark the first zone with a dual breach to launch the full-screen alarm
                    if (alarmZoneName == null) alarmZoneName = zoneName
                }
                sensor1 || sensor2 -> {
                    zoneUiUpdates.add(Triple(statusView, "🟡 Motion", android.R.color.holo_orange_light))
                    val sensorType = if (sensor1) "Motion" else "Sensor"
                    activityLog.add("[$currentTime] $zoneName: $sensorType detected (monitoring only)")
                }
                else -> {
                    zoneUiUpdates.add(Triple(statusView, "🟢 Clear", android.R.color.holo_green_light))
                }
            }
        }

        // Apply collected UI updates on the main thread
        runOnUiThread {
            zoneUiUpdates.forEach { (view, text, colorRes) ->
                view.text = text
                view.setTextColor(resources.getColor(colorRes, theme))
            }

            // Update system status
            systemStatusText.text = when (activeZones) {
                0 -> "🟢 All Zones Armed - System Online"
                1 -> "🟡 1 Zone Active - Monitoring"
                else -> "🔴 $activeZones Zones Active - ALERT!"
            }

            systemStatusText.setTextColor(
                when (activeZones) {
                    0 -> resources.getColor(android.R.color.holo_green_light, theme)
                    1 -> resources.getColor(android.R.color.holo_orange_light, theme)
                    else -> resources.getColor(android.R.color.holo_red_light, theme)
                }
            )

            // Update recent activity
            if (activityLog.isNotEmpty()) {
                recentActivityText.text = activityLog.takeLast(5).joinToString("\n")
                recentActivityText.setTextColor(resources.getColor(android.R.color.white, theme))
            } else {
                recentActivityText.text = "No recent activity"
                recentActivityText.setTextColor(resources.getColor(android.R.color.darker_gray, theme))
            }

            // Launch full-screen alarm activity for the zone with a dual-sensor breach, if any
            if (alarmZoneName != null) {
                val now = System.currentTimeMillis()
                val lastZone = AlarmFullscreenActivity.lastAlarmZone
                val lastTime = AlarmFullscreenActivity.lastAlarmTimeMs
                if (lastZone == alarmZoneName && now - lastTime < AlarmFullscreenActivity.MIN_RESTART_MS) {
                    Log.d("SecurityActivity", "Alarm for $alarmZoneName recently shown, skipping relaunch")
                } else {
                    AlarmFullscreenActivity.lastAlarmZone = alarmZoneName
                    AlarmFullscreenActivity.lastAlarmTimeMs = now
                    Log.i("SecurityActivity", "Launching AlarmFullscreenActivity for $alarmZoneName")
                    val intent = Intent(this@SecurityActivity, AlarmFullscreenActivity::class.java)
                    intent.putExtra("zone", alarmZoneName)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    startActivity(intent)
                }
            }
        }

        // Send notification for breaches (if any)
        if (activeZones > 0) {
            showSecurityNotification(activeZones, activityLog.takeLast(3))
        }
    }

    // Robust converter for Firestore values -> boolean
    private fun sensorValueToBoolean(value: Any?): Boolean {
        return when (value) {
            is Boolean -> value
            is Long -> value == 1L
            is Int -> value == 1
            is Double -> value == 1.0
            is String -> value == "1" || value.equals("true", ignoreCase = true)
            else -> false
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Security Alerts"
            val descriptionText = "Notifications for security breaches"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableLights(true)
                enableVibration(true)
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showSecurityNotification(activeZones: Int, recent: List<String>) {
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val content = if (recent.isNotEmpty()) {
            recent.joinToString("\n")
        } else {
            "$activeZones zone(s) active"
        }

        // Use a platform icon fallback to avoid missing drawable resources
        val smallIcon = android.R.drawable.ic_dialog_alert

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(smallIcon)
            .setContentTitle("Security Alert: $activeZones breach(s)")
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSound(soundUri)
            .setAutoCancel(true)

        // Check POST_NOTIFICATIONS permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.w("SecurityActivity", "Skipping notification send: POST_NOTIFICATIONS not granted")
                return
            }
        }

        with(NotificationManagerCompat.from(this)) {
            notify(NOTIF_ID, builder.build())
        }
    }
}