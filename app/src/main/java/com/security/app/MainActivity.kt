package com.security.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging

class MainActivity : AppCompatActivity() {
    
    private lateinit var db: FirebaseFirestore
    private lateinit var statusText: TextView
    private lateinit var timestampText: TextView
    private lateinit var securityStatus: TextView
    private lateinit var scadaStatus: TextView
    private lateinit var idsStatus: TextView
    private lateinit var iperlStatus: TextView
    
    // Data Analytics TextViews
    private lateinit var totalPointsValue: TextView
    private lateinit var activeSensorsValue: TextView
    private lateinit var dataRateValue: TextView
    private lateinit var temperatureDataCount: TextView
    private lateinit var humidityDataCount: TextView
    private lateinit var windDataCount: TextView
    private lateinit var powerDataCount: TextView
    private lateinit var dvrDataCount: TextView
    private lateinit var dataAnalyticsTimestamp: TextView
    
    // Card views for alarm color changes
    private lateinit var securityCard: androidx.cardview.widget.CardView
    private lateinit var scadaCard: androidx.cardview.widget.CardView
    private lateinit var idsCard: androidx.cardview.widget.CardView
    private lateinit var iperlCard: androidx.cardview.widget.CardView
    
    // Data analytics tracking
    private var totalDataPoints = 0
    private var temperaturePoints = 0
    private var humidityPoints = 0
    private var windPoints = 0
    private var powerPoints = 0
    private var dvrPoints = 0
    private var lastUpdateTime = System.currentTimeMillis()
    private var dataRate = 0.0
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_dashboard)
        
        // Initialize Firebase
        FirebaseApp.initializeApp(this)
        db = FirebaseFirestore.getInstance()
        
        // Initialize views
        statusText = findViewById(R.id.statusText)
        timestampText = findViewById(R.id.timestampText)
        securityStatus = findViewById(R.id.securityStatus)
        scadaStatus = findViewById(R.id.scadaStatus)
        idsStatus = findViewById(R.id.idsStatus)
        iperlStatus = findViewById(R.id.iperlStatus)
        
        // Initialize card views for alarm colors
        securityCard = findViewById(R.id.securityCard)
        scadaCard = findViewById(R.id.scadaCard)
        idsCard = findViewById(R.id.idsCard)
        iperlCard = findViewById(R.id.iperlCard)
        
        // Initialize data analytics views
        totalPointsValue = findViewById(R.id.totalPointsValue)
        activeSensorsValue = findViewById(R.id.activeSensorsValue)
        dataRateValue = findViewById(R.id.dataRateValue)
        temperatureDataCount = findViewById(R.id.temperatureDataCount)
        humidityDataCount = findViewById(R.id.humidityDataCount)
        windDataCount = findViewById(R.id.windDataCount)
        powerDataCount = findViewById(R.id.powerDataCount)
        dvrDataCount = findViewById(R.id.dvrDataCount)
        dataAnalyticsTimestamp = findViewById(R.id.dataAnalyticsTimestamp)
        
        // Create notification channel
        createNotificationChannel()
        
        // Get FCM token and save to Firestore
        getFCMToken()
        
        // Listen for sensor status updates
        listenForSensorUpdates()
        
        // Setup navigation
        setupNavigation()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = getString(R.string.default_notification_channel_id)
            val name = getString(R.string.channel_name)
            val descriptionText = getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_HIGH
            
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Delete existing channel to reset sound settings
            notificationManager.deleteNotificationChannel(channelId)
            
            // Create new channel with alarm sound
            val alarmUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                setShowBadge(true)
                setBypassDnd(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setSound(
                    alarmUri,
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setFlags(android.media.AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                        .build()
                )
            }
            
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun getFCMToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("FCM", "Fetching FCM registration token failed", task.exception)
                return@addOnCompleteListener
            }

            // Get new FCM registration token
            val token = task.result
            Log.d("FCM", "FCM Registration Token: $token")
            
            // Save token to Firestore
            saveTokenToFirestore(token)
            
            // Show token in UI for debugging
            runOnUiThread {
                Toast.makeText(this, "FCM Token saved", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun saveTokenToFirestore(token: String) {
        val tokenData = hashMapOf(
            "token" to token,
            "updated" to com.google.firebase.Timestamp.now(),
            "platform" to "android"
        )
        
        db.collection("fcm_tokens")
            .document(token)
            .set(tokenData)
            .addOnSuccessListener {
                Log.d("Firestore", "Token saved successfully")
            }
            .addOnFailureListener { e ->
                Log.w("Firestore", "Error saving token", e)
            }
    }
    
    private fun listenForSensorUpdates() {
        // Listen for security sensor updates
        db.collection("security sensors")
            .document("live_status")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("Firestore", "Listen failed.", e)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val data = snapshot.data
                    updateUI(data)
                } else {
                    Log.d("Firestore", "Current data: null")
                }
            }
            
        // Listen for gauge/water meter updates
        db.collection("security sensors")
            .document("gauge_status")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("Firestore", "Gauge listen failed.", e)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val gaugeData = snapshot.data
                    updateGaugeUI(gaugeData)
                } else {
                    Log.d("Firestore", "Gauge data: null")
                }
            }
    }
    
    private fun updateUI(data: Map<String, Any>?) {
        if (data == null) return
        
        val zones = mapOf(
            "Garage" to listOf("garage_motion", "garage_sensor"),
            "Garage Side" to listOf("garage_side_motion", "garage_side_sensor"),
            "South" to listOf("south_motion", "south_sensor"),
            "Back" to listOf("back_motion", "back_sensor"),
            "North" to listOf("north_motion", "north_sensor"),
            "Front" to listOf("front_motion", "front_sensor")
        )
        
        val statusBuilder = StringBuilder("Security Zone Status:\n\n")
        
        zones.forEach { (zoneName, sensors) ->
            val sensor1 = data[sensors[0]] as? Long ?: 0
            val sensor2 = data[sensors[1]] as? Long ?: 0
            
            val status = if (sensor1 == 1L && sensor2 == 1L) {
                "🚨 MOTION DETECTED"
            } else {
                "✅ CLEAR"
            }
            
            statusBuilder.append("$zoneName: $status\n")
        }
        
        // Add timestamp if available
        val timestamp = data["timestamp"]
        if (timestamp != null) {
            statusBuilder.append("\nLast Update: ${timestamp}")
        }
        
        runOnUiThread {
            statusText.text = "🏠 Home Automation Hub - All Systems Online"
            timestampText.text = "Last Update: ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}"
            
            // Update individual status blocks
            updateStatusBlocks(data)
            
            // Update data analytics with Firebase data
            updateDataAnalytics(data)
        }
    }
    
    private fun updateGaugeUI(gaugeData: Map<String, Any>?) {
        if (gaugeData == null) return
        
        runOnUiThread {
            // Update IPERL Status with water meter data
            val mikeWaterReading = (gaugeData["MikeWaterReading"] as? Double) ?: 0.0
            val allenWaterReading = (gaugeData["AllenWaterReading"] as? Double) ?: 0.0
            val mikeRSSI = (gaugeData["MikeRSSI"] as? Double) ?: -999.0
            val allenRSSI = (gaugeData["AllenRSSI"] as? Double) ?: -999.0
            
            val metersOnline = listOf(mikeRSSI, allenRSSI).count { it != -999.0 }
            val waterDataAvailable = listOf(mikeWaterReading, allenWaterReading).count { it > 0 }
            
            iperlStatus.text = when {
                waterDataAvailable == 2 -> "2 Meters: ${String.format("%.0f", mikeWaterReading)}L / ${String.format("%.0f", allenWaterReading)}L"
                waterDataAvailable == 1 -> "1 Meter Reading Available"
                metersOnline > 0 -> "$metersOnline Meters Online"
                else -> "No Meter Data"
            }
            
            // Update data analytics with Google Sheets data
            updateGoogleSheetsAnalytics(gaugeData)
        }
    }

    private fun updateStatusBlocks(data: Map<String, Any>) {
        // Update Security Status with Alarm Detection
        val zones = mapOf(
            "Garage" to listOf("garage_motion", "garage_sensor"),
            "Garage Side" to listOf("garage_side_motion", "garage_side_sensor"),
            "South" to listOf("south_motion", "south_sensor"),
            "Back" to listOf("back_motion", "back_sensor"),
            "North" to listOf("north_motion", "north_sensor"),
            "Front" to listOf("front_motion", "front_sensor")
        )

        var activeZones = 0
        zones.forEach { (_, sensors) ->
            val sensor1 = data[sensors[0]] as? Long ?: 0
            val sensor2 = data[sensors[1]] as? Long ?: 0
            if (sensor1 == 1L || sensor2 == 1L) activeZones++
        }
        
        // Security Alarm Logic
        if (activeZones > 0) {
            securityStatus.text = "🔴 $activeZones Active Zones"
            securityCard.setCardBackgroundColor(android.graphics.Color.parseColor("#D32F2F")) // Red alarm
        } else {
            securityStatus.text = "🟢 6 Zones Armed"
            securityCard.setCardBackgroundColor(android.graphics.Color.parseColor("#2D5016")) // Normal green
        }
        
        // SCADA Alarm Logic - Check for sensor faults
        updateSCADAAlarms(data)
        
        // IDS Status - Check for network threats
        updateIDSAlarms(data)
        
        // IPERL Status - Check for water meter issues  
        updateIPERLAlarms(data)
    }
    
    private fun updateSCADAAlarms(data: Map<String, Any>) {
        // Check temperature sensors
        val tempIn = (data["temp in"] as? Double) ?: 20.0
        val tempOut = (data["temp out"] as? Double) ?: 15.0
        val dvrTemp = (data["dvr_temp"] as? Double) ?: 25.0
        val power = (data["kw"] as? Double) ?: 0.0
        val amps = (data["amps"] as? Double) ?: 0.0
        
        val hasAlarm = when {
            tempIn > 35.0 || tempIn < 5.0 -> true // Indoor temp alarm
            tempOut > 45.0 || tempOut < -10.0 -> true // Outdoor temp alarm  
            dvrTemp > 45.0 -> true // DVR overheating
            power > 15.0 -> true // Power consumption too high
            amps > 60.0 -> true // Current draw too high
            else -> false
        }
        
        if (hasAlarm) {
            scadaStatus.text = "🔴 System Fault"
            scadaCard.setCardBackgroundColor(android.graphics.Color.parseColor("#D32F2F")) // Red alarm
        } else {
            scadaStatus.text = "🟢 All Normal"
            scadaCard.setCardBackgroundColor(android.graphics.Color.parseColor("#1A237E")) // Normal blue
        }
    }
    
    private fun updateIDSAlarms(data: Map<String, Any>) {
        // Simulate IDS threat detection (you can connect real IDS data later)
        val networkThreats = (data["network_threats"] as? Long) ?: 0
        val suspiciousActivity = (data["suspicious_activity"] as? Boolean) ?: false
        
        if (networkThreats > 0 || suspiciousActivity) {
            idsStatus.text = "🔴 Threats Detected"
            idsCard.setCardBackgroundColor(android.graphics.Color.parseColor("#D32F2F")) // Red alarm
        } else {
            idsStatus.text = "🟢 Controller Ready"
            idsCard.setCardBackgroundColor(android.graphics.Color.parseColor("#BF360C")) // Normal orange
        }
    }
    
    private fun updateIPERLAlarms(data: Map<String, Any>) {
        // This will be updated when we integrate Google Sheets data
        // For now, simulate water meter alarms
        val waterPressure = (data["water_pressure"] as? Double) ?: 3.5
        val flowRate = (data["flow_rate"] as? Double) ?: 0.0
        
        val hasWaterAlarm = when {
            waterPressure < 1.5 || waterPressure > 6.0 -> true // Pressure alarm
            flowRate > 15.0 -> true // High flow alarm (possible leak)
            else -> false
        }
        
        if (hasWaterAlarm) {
            iperlStatus.text = "🔴 Water Alert"
            iperlCard.setCardBackgroundColor(android.graphics.Color.parseColor("#D32F2F")) // Red alarm
        } else {
            iperlStatus.text = "🟢 Meters Normal"
            iperlCard.setCardBackgroundColor(android.graphics.Color.parseColor("#4A148C")) // Normal purple
        }
    }
    
    private fun setupNavigation() {
        findViewById<androidx.cardview.widget.CardView>(R.id.securityCard).setOnClickListener {
            val intent = android.content.Intent(this, SecurityActivity::class.java)
            startActivity(intent)
        }
        
        findViewById<androidx.cardview.widget.CardView>(R.id.scadaCard).setOnClickListener {
            val intent = android.content.Intent(this, ScadaActivity::class.java)
            startActivity(intent)
        }
        
        findViewById<androidx.cardview.widget.CardView>(R.id.idsCard).setOnClickListener {
            android.widget.Toast.makeText(this, "IDS Controller - Coming Soon", android.widget.Toast.LENGTH_SHORT).show()
        }
        
        findViewById<androidx.cardview.widget.CardView>(R.id.iperlCard).setOnClickListener {
            val intent = android.content.Intent(this, IPERLActivity::class.java)
            startActivity(intent)
        }
        
        // Add long-press listeners to test alarms
        securityCard.setOnLongClickListener {
            testSecurityAlarm()
            true
        }
        
        scadaCard.setOnLongClickListener {
            testSCADAAlarm()
            true
        }
        
        idsCard.setOnLongClickListener {
            testIDSAlarm()
            true
        }
        
        iperlCard.setOnLongClickListener {
            testIPERLAlarm()
            true
        }
    }
    
    private fun updateDataAnalytics(data: Map<String, Any>?) {
        if (data == null) return
        
        // Count data points from Firebase sensors
        var newTemperaturePoints = 0
        var newHumidityPoints = 0  
        var newWindPoints = 0
        var newPowerPoints = 0
        var newDvrPoints = 0
        
        // Count temperature sensors
        if (data["temp in"] != null) newTemperaturePoints++
        if (data["temp out"] != null) newTemperaturePoints++
        
        // Count humidity sensors
        if (data["humidity"] != null) newHumidityPoints++
        
        // Count wind sensors
        if (data["wind_speed"] != null) newWindPoints++
        if (data["wind_direction"] != null) newWindPoints++
        
        // Count power sensors
        if (data["kw"] != null) newPowerPoints++
        if (data["amps"] != null) newPowerPoints++
        if (data["voltage"] != null) newPowerPoints++
        
        // Count DVR sensors
        if (data["dvr_temp"] != null) newDvrPoints++
        if (data["dvr_status"] != null) newDvrPoints++
        
        // Update totals
        temperaturePoints += newTemperaturePoints
        humidityPoints += newHumidityPoints
        windPoints += newWindPoints
        powerPoints += newPowerPoints
        dvrPoints += newDvrPoints
        totalDataPoints = temperaturePoints + humidityPoints + windPoints + powerPoints + dvrPoints
        
        // Calculate data rate (points per minute)
        val currentTime = System.currentTimeMillis()
        val timeDiff = (currentTime - lastUpdateTime) / 1000.0 / 60.0 // minutes
        if (timeDiff > 0) {
            val newPointsThisUpdate = newTemperaturePoints + newHumidityPoints + newWindPoints + newPowerPoints + newDvrPoints
            dataRate = newPointsThisUpdate / timeDiff
        }
        lastUpdateTime = currentTime
        
        // Count active sensors (sensors with recent data)
        var activeSensors = 0
        val sensorTypes = listOf("temp in", "temp out", "humidity", "wind_speed", "kw", "amps", "dvr_temp")
        sensorTypes.forEach { sensorType ->
            if (data[sensorType] != null) activeSensors++
        }
        
        // Update UI on main thread
        runOnUiThread {
            totalPointsValue.text = totalDataPoints.toString()
            activeSensorsValue.text = activeSensors.toString()
            dataRateValue.text = String.format("%.1f/min", dataRate)
            
            temperatureDataCount.text = "$temperaturePoints points"
            humidityDataCount.text = "$humidityPoints points"
            windDataCount.text = "$windPoints points"
            powerDataCount.text = "$powerPoints points"
            dvrDataCount.text = "$dvrPoints points"
            
            dataAnalyticsTimestamp.text = "Last Updated: ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}"
        }
    }
    
    private fun updateGoogleSheetsAnalytics(gaugeData: Map<String, Any>?) {
        if (gaugeData == null) return
        
        // Count additional data points from Google Sheets
        var additionalPoints = 0
        
        // Water meter data points
        if (gaugeData["MikeWaterReading"] != null) additionalPoints++
        if (gaugeData["AllenWaterReading"] != null) additionalPoints++
        if (gaugeData["MikeRSSI"] != null) additionalPoints++
        if (gaugeData["AllenRSSI"] != null) additionalPoints++
        
        // Additional weather data from Google Sheets
        if (gaugeData["outdoor_temp"] != null) {
            temperaturePoints++
            additionalPoints++
        }
        if (gaugeData["indoor_temp"] != null) {
            temperaturePoints++
            additionalPoints++
        }
        if (gaugeData["humidity"] != null) {
            humidityPoints++
            additionalPoints++
        }
        if (gaugeData["wind_speed"] != null) {
            windPoints++
            additionalPoints++
        }
        if (gaugeData["wind_direction"] != null) {
            windPoints++
            additionalPoints++
        }
        
        // Add to total
        totalDataPoints += additionalPoints
        
        // Update UI
        runOnUiThread {
            totalPointsValue.text = totalDataPoints.toString()
            temperatureDataCount.text = "$temperaturePoints points"
            humidityDataCount.text = "$humidityPoints points" 
            windDataCount.text = "$windPoints points"
        }
    }
    
    // Test alarm functions - Long press cards to test alarm states
    private fun testSecurityAlarm() {
        // Simulate motion detection
        val testData = mapOf(
            "garage_motion" to 1L,
            "south_motion" to 1L,
            "timestamp" to com.google.firebase.Timestamp.now()
        )
        updateStatusBlocks(testData)
        android.widget.Toast.makeText(this, "Security Alarm Test - Motion Detected", android.widget.Toast.LENGTH_SHORT).show()
        
        // Reset after 5 seconds
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            val normalData = mapOf(
                "garage_motion" to 0L,
                "south_motion" to 0L,
                "timestamp" to com.google.firebase.Timestamp.now()
            )
            updateStatusBlocks(normalData)
        }, 5000)
    }
    
    private fun testSCADAAlarm() {
        // Simulate high temperature alarm
        val testData = mapOf(
            "temp in" to 45.0, // High indoor temp
            "dvr_temp" to 50.0, // DVR overheating
            "kw" to 18.0, // High power consumption
            "timestamp" to com.google.firebase.Timestamp.now()
        )
        updateStatusBlocks(testData)
        android.widget.Toast.makeText(this, "SCADA Alarm Test - High Temperature", android.widget.Toast.LENGTH_SHORT).show()
        
        // Reset after 5 seconds
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            val normalData = mapOf(
                "temp in" to 22.0,
                "dvr_temp" to 25.0,
                "kw" to 5.0,
                "timestamp" to com.google.firebase.Timestamp.now()
            )
            updateStatusBlocks(normalData)
        }, 5000)
    }
    
    private fun testIDSAlarm() {
        // Simulate network threat
        val testData = mapOf(
            "network_threats" to 3L,
            "suspicious_activity" to true,
            "timestamp" to com.google.firebase.Timestamp.now()
        )
        updateStatusBlocks(testData)
        android.widget.Toast.makeText(this, "IDS Alarm Test - Threats Detected", android.widget.Toast.LENGTH_SHORT).show()
        
        // Reset after 5 seconds
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            val normalData = mapOf(
                "network_threats" to 0L,
                "suspicious_activity" to false,
                "timestamp" to com.google.firebase.Timestamp.now()
            )
            updateStatusBlocks(normalData)
        }, 5000)
    }
    
    private fun testIPERLAlarm() {
        // Simulate water pressure alarm
        val testData = mapOf(
            "water_pressure" to 0.8, // Low pressure alarm
            "flow_rate" to 20.0, // High flow rate (possible leak)
            "timestamp" to com.google.firebase.Timestamp.now()
        )
        updateStatusBlocks(testData)
        android.widget.Toast.makeText(this, "IPERL Alarm Test - Water Pressure Low", android.widget.Toast.LENGTH_SHORT).show()
        
        // Reset after 5 seconds
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            val normalData = mapOf(
                "water_pressure" to 3.5,
                "flow_rate" to 2.0,
                "timestamp" to com.google.firebase.Timestamp.now()
            )
            updateStatusBlocks(normalData)
        }, 5000)
    }
}