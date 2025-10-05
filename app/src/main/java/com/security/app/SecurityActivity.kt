package com.security.app

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
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
        
        // Listen for real-time security updates
        listenForSecurityUpdates()
    }
    
    private fun listenForSecurityUpdates() {
        db.collection("security sensors").document("live_status")
            .addSnapshotListener { documentSnapshot, e ->
                if (e != null) {
                    android.util.Log.w("SecurityActivity", "Listen failed.", e)
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
        
        zones.forEach { (zoneName, zoneData) ->
            val (sensors, statusView) = zoneData
            val sensor1 = data[sensors[0]] as? Long ?: 0
            val sensor2 = data[sensors[1]] as? Long ?: 0
            
            when {
                sensor1 == 1L && sensor2 == 1L -> {
                    statusView.text = "🔴 BREACH!"
                    statusView.setTextColor(getColor(android.R.color.holo_red_light))
                    activeZones++  // Only count dual-sensor breaches
                    activityLog.add("[$currentTime] $zoneName: BOTH SENSORS ACTIVE")
                }
                sensor1 == 1L || sensor2 == 1L -> {
                    statusView.text = "🟡 Motion"
                    statusView.setTextColor(getColor(android.R.color.holo_orange_light))
                    // ✅ Removed activeZones++ - single sensors don't count as active zones
                    val sensorType = if (sensor1 == 1L) "Motion" else "Sensor"
                    activityLog.add("[$currentTime] $zoneName: $sensorType detected (monitoring only)")
                }
                else -> {
                    statusView.text = "🟢 Clear"
                    statusView.setTextColor(getColor(android.R.color.holo_green_light))
                }
            }
        }
        
        // Update system status
        runOnUiThread {
            systemStatusText.text = when (activeZones) {
                0 -> "🟢 All Zones Armed - System Online"
                1 -> "🟡 1 Zone Active - Monitoring"
                else -> "🔴 $activeZones Zones Active - ALERT!"
            }
            
            systemStatusText.setTextColor(
                when (activeZones) {
                    0 -> getColor(android.R.color.holo_green_light)
                    1 -> getColor(android.R.color.holo_orange_light)
                    else -> getColor(android.R.color.holo_red_light)
                }
            )
            
            // Update recent activity
            if (activityLog.isNotEmpty()) {
                recentActivityText.text = activityLog.takeLast(5).joinToString("\n")
                recentActivityText.setTextColor(getColor(android.R.color.white))
            } else {
                recentActivityText.text = "No recent activity"
                recentActivityText.setTextColor(getColor(android.R.color.darker_gray))
            }
        }
    }
}