package com.security.app

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ScadaActivity : AppCompatActivity() {

    private lateinit var ampsTextView: TextView
    private lateinit var kwTextView: TextView
    private lateinit var geyserTempTextView: TextView
    private lateinit var geyserPressureTextView: TextView
    private lateinit var dvrTempTextView: TextView
    private lateinit var indoorTempTextView: TextView
    private lateinit var outdoorTempTextView: TextView
    private lateinit var outdoorHumidityTextView: TextView
    private lateinit var windSpeedTextView: TextView
    private lateinit var windDirectionTextView: TextView
    
    // Firestore reference for security sensors
    private lateinit var db: FirebaseFirestore
    
    // Google Sheets reader for geyser data
    private lateinit var sheetsReader: GoogleSheetsReader
    
    // Analytics variables
    private lateinit var totalPointsValue: TextView
    private lateinit var dataRateValue: TextView
    private lateinit var activeSensorsValue: TextView
    private lateinit var tempDataCount: TextView
    private lateinit var humidityDataCount: TextView
    private lateinit var windDataCount: TextView
    private lateinit var powerDataCount: TextView
    private lateinit var dvrDataCount: TextView
    private lateinit var dataUsageLastUpdate: TextView
    private var lastUpdateTime: Long = 0L
    private var totalDataPoints: Int = 0
    private var dataRate: Double = 0.0
    private var activeSensors: Int = 0

    // Data class for Geyser readings
    data class GeyserReading(
        val waterTemp: Double = 0.0,
        val waterPressure: Double = 0.0,
        val dvrTemp: Double = 0.0,
        val currentAmps: Double = 0.0,
        val currentPower: Double = 0.0,
        val indoorTemp: Double = 0.0,
        val outdoorTemp: Double = 0.0,
        val humidity: Double = 0.0,
        val windSpeed: Double = 0.0,
        val windDirection: Float = 0f
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scada)
        
        // Initialize views with amps prominent, kW smaller as requested
        ampsTextView = findViewById(R.id.currentAmps)
        kwTextView = findViewById(R.id.currentPower)
        geyserTempTextView = findViewById(R.id.waterTempValue) 
        geyserPressureTextView = findViewById(R.id.waterPressureValue)
        dvrTempTextView = findViewById(R.id.dvrTemp)
        indoorTempTextView = findViewById(R.id.indoorTemp)
        outdoorTempTextView = findViewById(R.id.outdoorTemp)
        outdoorHumidityTextView = findViewById(R.id.outdoorHumidity)
        windSpeedTextView = findViewById(R.id.windSpeed)
        windDirectionTextView = findViewById(R.id.windDirectionArrow)
        
        // Initialize Firestore for security sensors
        db = FirebaseFirestore.getInstance()
        
        // Initialize Google Sheets reader for geyser data
        sheetsReader = GoogleSheetsReader()
        
        // Set initial values to show amps prominent, kW smaller layout
        ampsTextView.text = "-- A"  // Amps prominently displayed
        kwTextView.text = "-- kW"  // kW smaller on side  
        geyserTempTextView.text = "Loading Sheets..."
        geyserPressureTextView.text = "Loading Sheets..."
        dvrTempTextView.text = "DVR: --°C"
        indoorTempTextView.text = "--°C"
        outdoorTempTextView.text = "--°C"
        outdoorHumidityTextView.text = "--%"
        windSpeedTextView.text = "-- km/h"
        windDirectionTextView.text = "↑"
        
        // Initialize analytics TextViews
        totalPointsValue = findViewById(R.id.totalPointsValue)
        dataRateValue = findViewById(R.id.dataRateValue)
        activeSensorsValue = findViewById(R.id.activeSensorsValue)
        tempDataCount = findViewById(R.id.tempDataCount)
        humidityDataCount = findViewById(R.id.humidityDataCount)
        windDataCount = findViewById(R.id.windDataCount)
        powerDataCount = findViewById(R.id.powerDataCount)
        dvrDataCount = findViewById(R.id.dvrDataCount)
        dataUsageLastUpdate = findViewById(R.id.dataUsageLastUpdate)

        // Setup click listeners for graphs
        setupGraphClickListeners()
        
        // Firebase monitoring removed - all data now from Google Sheets
        
        // Start Google Sheets monitoring for geyser data
        monitorGeyserData()
    }
    
    private fun setupGraphClickListeners() {
        // Weather section click - show weather graphs
        findViewById<android.widget.LinearLayout>(R.id.weatherSection).setOnClickListener {
            val intent = Intent(this, WeatherGraphsActivity::class.java)
            startActivity(intent)
        }
        
        // Power section click - show power graphs  
        findViewById<androidx.cardview.widget.CardView>(R.id.powerSection).setOnClickListener {
            val intent = Intent(this, PowerGraphsActivity::class.java)
            startActivity(intent)
        }
        
        // Geyser section click - show geyser graphs
        findViewById<androidx.cardview.widget.CardView>(R.id.geyserSection).setOnClickListener {
            val intent = Intent(this, GeyserGraphsActivity::class.java)
            startActivity(intent)
        }
        
        // DVR section click - show DVR graphs
        findViewById<androidx.cardview.widget.CardView>(R.id.dvrSection).setOnClickListener {
            val intent = Intent(this, DvrGraphsActivity::class.java)
            startActivity(intent)
        }
        
        // Back button functionality
        findViewById<android.widget.TextView>(R.id.scadaBackButton).setOnClickListener {
            finish()
        }
    }
    
    // Firebase monitoring removed - all data now comes from Google Sheets
    
    private fun updateAnalytics(latestReading: GeyserReading) {
        // Count data points (all fields are always present)
        val points = 10 // number of fields in GeyserReading
        val currentTime = System.currentTimeMillis()
        if (lastUpdateTime != 0L) {
            val timeDiffMin = (currentTime - lastUpdateTime) / 60000.0
            if (timeDiffMin > 0) {
                dataRate = points / timeDiffMin
            }
        }
        lastUpdateTime = currentTime
        totalDataPoints += points
        activeSensors = points
        // Update UI
        totalPointsValue.text = totalDataPoints.toString()
        dataRateValue.text = String.format("%.1f/min", dataRate)
        activeSensorsValue.text = activeSensors.toString()
        // Data Source Breakdown
        tempDataCount.text = if (latestReading.waterTemp != 0.0) "1 point" else "0 points"
        humidityDataCount.text = if (latestReading.humidity != 0.0) "1 point" else "0 points"
        windDataCount.text = if (latestReading.windSpeed != 0.0) "1 point" else "0 points"
        powerDataCount.text = if (latestReading.currentPower != 0.0) "1 point" else "0 points"
        dvrDataCount.text = if (latestReading.dvrTemp != 0.0) "1 point" else "0 points"
        // Last Updated
        val cal = java.util.Calendar.getInstance()
        val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val min = cal.get(java.util.Calendar.MINUTE)
        dataUsageLastUpdate.text = String.format("Last Updated: %02d:%02d", hour, min)
    }

    private fun monitorGeyserData() {
        // Launch coroutine to fetch geyser data from Google Sheets
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Fetch latest readings from Google Sheets (real geyser data)
                val sensorReadings = withContext(Dispatchers.IO) {
                    sheetsReader.fetchLatestReadings(1) // Get just the latest reading
                }
                
                if (sensorReadings.isNotEmpty()) {
                    // Convert SensorReading to GeyserReading
                    val s = sensorReadings[0]
                    val latestReading = GeyserReading(
                        waterTemp = s.waterTemp.toDouble(),
                        waterPressure = s.waterPressure.toDouble(),
                        dvrTemp = s.dvrTemp.toDouble(),
                        currentAmps = s.currentAmps.toDouble(),
                        currentPower = s.currentPower.toDouble(),
                        indoorTemp = s.indoorTemp.toDouble(),
                        outdoorTemp = s.outdoorTemp.toDouble(),
                        humidity = s.humidity.toDouble(),
                        windSpeed = s.windSpeed.toDouble(),
                        windDirection = s.windDirection
                    )
                    // Update UI with REAL data from Google Sheets (LAST ROW)
                    geyserTempTextView.text = String.format("%.1f°C", latestReading.waterTemp)
                    geyserPressureTextView.text = String.format("%.1f bar", latestReading.waterPressure)
                    dvrTempTextView.text = String.format("%.1f°C", latestReading.dvrTemp)
                    ampsTextView.text = String.format("%.1f A", latestReading.currentAmps)
                    kwTextView.text = String.format("%.2f kW", latestReading.currentPower)
                    indoorTempTextView.text = String.format("%.1f°C", latestReading.indoorTemp)
                    outdoorTempTextView.text = String.format("%.1f°C", latestReading.outdoorTemp)
                    outdoorHumidityTextView.text = String.format("%.0f%%", latestReading.humidity)
                    windSpeedTextView.text = String.format("%.1f km/h", latestReading.windSpeed)
                    windDirectionTextView.text = getWindDirectionArrow(latestReading.windDirection)
                    // Update analytics with the latest reading
                    updateAnalytics(latestReading)
                } else {
                    geyserTempTextView.text = "No Sheets data"
                    geyserPressureTextView.text = "No Sheets data"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // Show error state
                runOnUiThread {
                    geyserTempTextView.text = "Error"
                    geyserPressureTextView.text = "Error"
                }
            }
            // Schedule next update in 30 seconds for fresh geyser data
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                monitorGeyserData()
            }, 30000) // Update every 30 seconds
        }
    }
    
    // Convert wind direction degrees to directional arrow
    private fun getWindDirectionArrow(degrees: Float): String {
        return when (degrees.toInt()) {
            in 0..22 -> "↑"      // North
            in 23..67 -> "↗"     // Northeast
            in 68..112 -> "→"    // East
            in 113..157 -> "↘"   // Southeast
            in 158..202 -> "↓"   // South
            in 203..247 -> "↙"   // Southwest
            in 248..292 -> "←"   // West
            in 293..337 -> "↖"   // Northwest
            in 338..359 -> "↑"   // North
            else -> "?"          // Invalid/unknown
        }
    }
}