package com.security.app

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class IPERLActivity : AppCompatActivity() {
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

        // Load all water meter data and RSSI from Google Sheets
        loadAllIPERLDataFromGoogleSheets()
    }

    private fun loadAllIPERLDataFromGoogleSheets() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                iperlSystemStatus.text = "\uD83D\uDD04 Loading water meter and signal data from Google Sheets..."
                // Fetch usage statistics (total, daily, monthly, last update)
                val usageStats = withContext(Dispatchers.IO) { iperlGoogleSheetsReader.getUsageStatistics() }
                // Fetch all RSSI readings
                val signalReadings = withContext(Dispatchers.IO) { iperlGoogleSheetsReader.fetchLatestRSSIReadings(1000) }
                // Fetch latest value from column K for Mikes Water Meter card
                val latestColumnKValue = withContext(Dispatchers.IO) { iperlGoogleSheetsReader.fetchLatestColumnKValue() }

                // Debug logging
                android.util.Log.d("IPERLActivity", "usageStats: $usageStats")
                android.util.Log.d("IPERLActivity", "signalReadings: $signalReadings")
                android.util.Log.d("IPERLActivity", "latestColumnKValue: $latestColumnKValue")

                // Update water meter readings (use 3 decimal places)
                totalUsage.text = String.format("%.3f L", usageStats.totalUsage)
                // mikeWaterReading now uses column K value
                if (latestColumnKValue != null && latestColumnKValue.isNotEmpty()) {
                    mikeWaterReading.text = "${latestColumnKValue} Liters"
                } else {
                    mikeWaterReading.text = "No Data"
                }
                dayUsage.text = String.format("%.1f L", usageStats.dayUsage)
                monthlyUsage.text = String.format("%.1f L", usageStats.monthUsage)
                lastUpdate.text = "Last Update: ${usageStats.lastUpdate}"

                if (usageStats.totalUsage > 0f) {
                    mikeWaterStatus.text = "\u2705 Active Reading"
                    mikeWaterStatus.setTextColor(getColor(android.R.color.holo_green_light))
                } else {
                    mikeWaterStatus.text = "\u26A0\uFE0F No Data"
                    mikeWaterStatus.setTextColor(getColor(android.R.color.holo_orange_light))
                }

                // Update RSSI/signal (use last value, not first)
                if (signalReadings.isNotEmpty()) {
                    val latestSignal = signalReadings.last() // last is most recent
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

                // Update system status
                val hasWaterData = usageStats.totalUsage > 0f
                val hasSignalData = signalReadings.isNotEmpty()
                iperlSystemStatus.text = when {
                    hasWaterData && hasSignalData -> "🟢 Water Meter Online - Google Sheets Connected"
                    hasWaterData -> "🟡 Meter Data Available - Signal Data Missing"
                    hasSignalData -> "🟡 Signal Data Available - Meter Data Missing"
                    else -> "🔴 No Data Available from Google Sheets"
                }
            } catch (e: Exception) {
                val errorMsg = "❌ Failed to load data from Google Sheets: ${e.message}"
                iperlSystemStatus.text = errorMsg
                mikeWaterReading.text = "No Data"
                mikeWaterStatus.text = "⚠️ No Data"
                mikeWaterStatus.setTextColor(getColor(android.R.color.holo_orange_light))
                dayUsage.text = "-- L"
                monthlyUsage.text = "-- L"
                totalUsage.text = "-- L"
                lastUpdate.text = "Last Update: --"
                mikeRSSI.text = "-- dBm"
                mikeSignalStatus.text = "📶 No Signal Data"
                mikeSignalStatus.setTextColor(getColor(android.R.color.darker_gray))
                android.util.Log.e("IPERLActivity", errorMsg, e)
            }
        }
    }
}