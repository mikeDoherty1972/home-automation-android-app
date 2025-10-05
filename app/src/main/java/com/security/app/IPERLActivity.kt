package com.security.app

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class IPERLActivity : AppCompatActivity() {
    
    private lateinit var db: FirebaseFirestore
    private lateinit var iperlGoogleSheetsReader: IPERLGoogleSheetsReader
    private lateinit var iperlSystemStatus: TextView
    private lateinit var mikeWaterReading: TextView
    private lateinit var mikeWaterStatus: TextView
    private lateinit var mikeRSSI: TextView
    private lateinit var mikeSignalStatus: TextView
    private lateinit var dayUsage: TextView
    private lateinit var monthlyUsage: TextView
    private lateinit var totalUsage: TextView
    private lateinit var lastUpdate: TextView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_iperl)
        
        // Initialize Firebase
        db = FirebaseFirestore.getInstance()
        
        // Initialize Google Sheets reader
        iperlGoogleSheetsReader = IPERLGoogleSheetsReader()
        
        // Initialize views
        iperlSystemStatus = findViewById(R.id.iperlSystemStatus)
        mikeWaterReading = findViewById(R.id.mikeWaterReading)
        mikeWaterStatus = findViewById(R.id.mikeWaterStatus)
        mikeRSSI = findViewById(R.id.mikeRSSI)
        mikeSignalStatus = findViewById(R.id.mikeSignalStatus)
        dayUsage = findViewById(R.id.dayUsage)
        monthlyUsage = findViewById(R.id.monthlyUsage)
        totalUsage = findViewById(R.id.totalUsage)
        lastUpdate = findViewById(R.id.lastUpdate)
        
        // Setup back button
        findViewById<TextView>(R.id.iperlBackButton).setOnClickListener {
            finish()
        }
        
        // Load water meter data from Firebase (from PLC data with Google Sheets integration)
        loadWaterMeterDataFromFirebase()
        
        // Also load from Google Sheets for RSSI data (not in PLC)
        loadRSSIDataFromGoogleSheets()
    }
    
    private fun loadWaterMeterData() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Show loading state
                iperlSystemStatus.text = "🔄 Loading water meter data from Google Sheets..."
                
                // Fetch data from both tabs
                val waterReadings = iperlGoogleSheetsReader.fetchLatestWaterReadings(10)
                val signalReadings = iperlGoogleSheetsReader.fetchLatestRSSIReadings(10)
                val usageStats = iperlGoogleSheetsReader.getUsageStatistics()
                
                // Update UI with fetched data
                updateIPERLDisplay(waterReadings, signalReadings, usageStats)
                
            } catch (e: Exception) {
                // Handle errors
                iperlSystemStatus.text = "❌ Failed to load data from Google Sheets"
                android.util.Log.e("IPERLActivity", "Error loading data", e)
            }
        }
    }
    
    private fun updateIPERLDisplay(
        waterReadings: List<IPERLGoogleSheetsReader.WaterMeterReading>,
        signalReadings: List<IPERLGoogleSheetsReader.SignalReading>,
        usageStats: IPERLGoogleSheetsReader.UsageStats
    ) {
        // Update signal strength from mike_RSSI tab
        if (signalReadings.isNotEmpty()) {
            val latestSignal = signalReadings.first()
            val rssiValue = latestSignal.rssi
            
            mikeRSSI.text = "${rssiValue.toInt()} dBm"
            
            mikeSignalStatus.text = when {
                rssiValue > -50 -> "📶 Excellent"
                rssiValue > -70 -> "📶 Good"
                rssiValue > -85 -> "📶 Fair"
                else -> "📶 Poor"
            }
            
            mikeSignalStatus.setTextColor(
                when {
                    rssiValue > -70 -> getColor(android.R.color.holo_green_light)
                    rssiValue > -85 -> getColor(android.R.color.holo_orange_light)
                    else -> getColor(android.R.color.holo_red_light)
                }
            )
        } else {
            mikeRSSI.text = "-- dBm"
            mikeSignalStatus.text = "📶 No Signal Data"
            mikeSignalStatus.setTextColor(getColor(android.R.color.darker_gray))
        }
        
        // Update water readings from mike_data tab
        if (waterReadings.isNotEmpty()) {
            val latestReading = waterReadings.first()
            
            mikeWaterReading.text = "${String.format("%.1f", latestReading.meterReading)} L"
            mikeWaterStatus.text = "✅ Active Reading"
            mikeWaterStatus.setTextColor(getColor(android.R.color.holo_green_light))
            
            // Update usage statistics
            dayUsage.text = "${String.format("%.1f", usageStats.dayUsage)} L"
            monthlyUsage.text = "${String.format("%.1f", usageStats.monthUsage)} L"
            totalUsage.text = "${String.format("%.1f", usageStats.totalUsage)} L"
            
        } else {
            mikeWaterReading.text = "No Data"
            mikeWaterStatus.text = "⚠️ No Data"
            mikeWaterStatus.setTextColor(getColor(android.R.color.holo_orange_light))
            
            dayUsage.text = "-- L"
            monthlyUsage.text = "-- L"
            totalUsage.text = "-- L"
        }
        
        // Update system status
        val hasWaterData = waterReadings.isNotEmpty()
        val hasSignalData = signalReadings.isNotEmpty()
        
        iperlSystemStatus.text = when {
            hasWaterData && hasSignalData -> "🟢 Water Meter Online - Google Sheets Connected"
            hasWaterData -> "🟡 Meter Data Available - Signal Data Missing"
            hasSignalData -> "🟡 Signal Data Available - Meter Data Missing"
            else -> "🔴 No Data Available from Google Sheets"
        }
        
        // Update last update timestamp
        lastUpdate.text = "Last Update: ${usageStats.lastUpdate}"
    }
    
    private fun loadWaterMeterDataFromFirebase() {
        // Listen for water usage data from Firebase (sent by PLC script with Google Sheets integration)
        db.collection("security sensors").document("plc_status")
            .addSnapshotListener { documentSnapshot, e ->
                if (e != null) {
                    android.util.Log.w("IPERLActivity", "Firebase listen failed.", e)
                    return@addSnapshotListener
                }
                
                if (documentSnapshot != null && documentSnapshot.exists()) {
                    val data = documentSnapshot.data ?: return@addSnapshotListener
                    
                    runOnUiThread {
                        // Update water usage from Firebase (PLC data with Google Sheets integration)
                        val totalUsageValue = data["water_usage_total"] as? Double
                        val dailyUsageValue = data["water_usage_daily"] as? Double
                        val monthlyUsageValue = data["water_usage_monthly"] as? Double
                        
                        if (totalUsageValue != null) {
                            totalUsage.text = "${String.format("%.1f", totalUsageValue)} L"
                            mikeWaterReading.text = "${String.format("%.1f", totalUsageValue)} L"
                            mikeWaterStatus.text = "✅ Active Reading"
                            mikeWaterStatus.setTextColor(getColor(android.R.color.holo_green_light))
                        } else {
                            mikeWaterReading.text = "0,0 L"
                            mikeWaterStatus.text = "⚠️ No Data"
                            mikeWaterStatus.setTextColor(getColor(android.R.color.holo_orange_light))
                        }
                        
                        if (dailyUsageValue != null) {
                            dayUsage.text = "${String.format("%.1f", dailyUsageValue)} L"
                        } else {
                            dayUsage.text = "0,0 L"
                        }
                        
                        if (monthlyUsageValue != null) {
                            monthlyUsage.text = "${String.format("%.1f", monthlyUsageValue)} L"
                        } else {
                            monthlyUsage.text = "0,0 L"
                        }
                        
                        // Update system status
                        val hasWaterData = totalUsageValue != null
                        iperlSystemStatus.text = if (hasWaterData) {
                            "🟢 Water Meter Online - Firebase Connected"
                        } else {
                            "🟡 Waiting for Water Data from Firebase"
                        }
                        
                        // Update last update timestamp
                        val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                        lastUpdate.text = "Last Update: $currentTime"
                    }
                }
            }
    }
    
    private fun loadRSSIDataFromGoogleSheets() {
        // Keep the existing Google Sheets RSSI data loading for signal strength
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val signalReadings = iperlGoogleSheetsReader.fetchLatestRSSIReadings(10)
                
                if (signalReadings.isNotEmpty()) {
                    val latestSignal = signalReadings.first()
                    val rssiValue = latestSignal.rssi
                    
                    mikeRSSI.text = "${rssiValue.toInt()} dBm"
                    
                    mikeSignalStatus.text = when {
                        rssiValue > -50 -> "📶 Excellent"
                        rssiValue > -70 -> "📶 Good"
                        rssiValue > -85 -> "📶 Fair"
                        else -> "📶 Poor"
                    }
                    
                    mikeSignalStatus.setTextColor(
                        when {
                            rssiValue > -70 -> getColor(android.R.color.holo_green_light)
                            rssiValue > -85 -> getColor(android.R.color.holo_orange_light)
                            else -> getColor(android.R.color.holo_red_light)
                        }
                    )
                } else {
                    mikeRSSI.text = "-- dBm"
                    mikeSignalStatus.text = "📶 No Signal Data"
                    mikeSignalStatus.setTextColor(getColor(android.R.color.darker_gray))
                }
            } catch (e: Exception) {
                android.util.Log.e("IPERLActivity", "Error loading RSSI data", e)
                mikeRSSI.text = "-- dBm"
                mikeSignalStatus.text = "📶 No Signal Data"
                mikeSignalStatus.setTextColor(getColor(android.R.color.darker_gray))
            }
        }
    }
}