package com.security.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

/**
 * IPERL-specific Google Sheets reader for water meter data
 * Reads from mike_data and mike_RSSI tabs
 */
class IPERLGoogleSheetsReader {
    
    companion object {
        // Your Google Sheets ID 
        private const val SHEET_ID = "1_GuECfJE0nFSGMS6-prrWAfLCR4gBPa3Qf_zzaKzAAA"
        
        // Tab-specific URLs with correct GIDs
        // mike_data tab URL
        private const val MIKE_DATA_URL = "https://docs.google.com/spreadsheets/d/$SHEET_ID/export?format=csv&gid=1172995861"
        
        // mike_RSSI tab URL
        private const val MIKE_RSSI_URL = "https://docs.google.com/spreadsheets/d/$SHEET_ID/export?format=csv&gid=1935643323"
    }
    
    data class WaterMeterReading(
        val timestamp: String,
        val meterReading: Float,
        val dailyUsage: Float,
        val monthlyUsage: Float,
        val totalUsage: Float
    )
    
    data class SignalReading(
        val timestamp: String,
        val rssi: Float,
        val signalQuality: String,
        val batteryLevel: Float
    )
    
    /**
     * Fetch the latest water meter readings from mike_data tab
     */
    suspend fun fetchLatestWaterReadings(maxRows: Int = 100): List<WaterMeterReading> {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(MIKE_DATA_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val csvData = reader.readText()
                reader.close()
                
                parseWaterMeterData(csvData, maxRows)
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }
    
    /**
     * Fetch the latest RSSI readings from mike_RSSI tab
     */
    suspend fun fetchLatestRSSIReadings(maxRows: Int = 100): List<SignalReading> {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(MIKE_RSSI_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val csvData = reader.readText()
                reader.close()
                
                parseSignalData(csvData, maxRows)
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }
    
    /**
     * Get usage statistics (day, month, total) from latest readings
     */
    suspend fun getUsageStatistics(): UsageStats {
        return withContext(Dispatchers.IO) {
            try {
                val readings = fetchLatestWaterReadings(1000)
                if (readings.isEmpty()) {
                    return@withContext UsageStats(0f, 0f, 0f, "No Data")
                }
                
                val latest = readings.first()
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val thisMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
                
                // Calculate day usage from readings today
                val todayReadings = readings.filter { it.timestamp.startsWith(today) }
                val dayUsage = if (todayReadings.size >= 2) {
                    val maxToday = todayReadings.maxOfOrNull { it.meterReading } ?: 0f
                    val minToday = todayReadings.minOfOrNull { it.meterReading } ?: 0f
                    maxToday - minToday
                } else {
                    latest.dailyUsage
                }
                
                // Calculate month usage from readings this month
                val monthReadings = readings.filter { it.timestamp.startsWith(thisMonth) }
                val monthUsage = if (monthReadings.size >= 2) {
                    val maxMonth = monthReadings.maxOfOrNull { it.meterReading } ?: 0f
                    val minMonth = monthReadings.minOfOrNull { it.meterReading } ?: 0f
                    maxMonth - minMonth
                } else {
                    latest.monthlyUsage
                }
                
                UsageStats(
                    dayUsage = dayUsage,
                    monthUsage = monthUsage, 
                    totalUsage = latest.totalUsage,
                    lastUpdate = latest.timestamp
                )
            } catch (e: Exception) {
                e.printStackTrace()
                UsageStats(0f, 0f, 0f, "Error")
            }
        }
    }
    
    data class UsageStats(
        val dayUsage: Float,
        val monthUsage: Float,
        val totalUsage: Float,
        val lastUpdate: String
    )
    
    /**
     * Parse CSV data into WaterMeterReading objects
     */
    private fun parseWaterMeterData(csvData: String, maxRows: Int): List<WaterMeterReading> {
        val readings = mutableListOf<WaterMeterReading>()
        val lines = csvData.split("\n")
        
        // Skip header row and parse data rows
        for (i in 1 until minOf(lines.size, maxRows + 1)) {
            val line = lines[i].trim()
            if (line.isEmpty()) continue
            
            try {
                val fields = parseCsvLine(line)
                if (fields.size >= 5) {
                    val reading = WaterMeterReading(
                        timestamp = fields[0].trim('"'),
                        meterReading = parseFloat(fields[1]),
                        dailyUsage = parseFloat(fields[2]),
                        monthlyUsage = parseFloat(fields[3]),
                        totalUsage = parseFloat(fields[4])
                    )
                    readings.add(reading)
                }
            } catch (e: Exception) {
                continue
            }
        }
        
        return readings.reversed() // Most recent first
    }
    
    /**
     * Parse CSV data into SignalReading objects
     */
    private fun parseSignalData(csvData: String, maxRows: Int): List<SignalReading> {
        val readings = mutableListOf<SignalReading>()
        val lines = csvData.split("\n")
        
        // Skip header row and parse data rows
        for (i in 1 until minOf(lines.size, maxRows + 1)) {
            val line = lines[i].trim()
            if (line.isEmpty()) continue
            
            try {
                val fields = parseCsvLine(line)
                if (fields.size >= 4) {
                    val reading = SignalReading(
                        timestamp = fields[0].trim('"'),
                        rssi = parseFloat(fields[1]),
                        signalQuality = fields[2].trim('"'),
                        batteryLevel = parseFloat(fields[3])
                    )
                    readings.add(reading)
                }
            } catch (e: Exception) {
                continue
            }
        }
        
        return readings.reversed() // Most recent first
    }
    
    /**
     * Parse a CSV line handling quoted fields
     */
    private fun parseCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val currentField = StringBuilder()
        var insideQuotes = false
        
        for (char in line) {
            when {
                char == '"' -> insideQuotes = !insideQuotes
                char == ',' && !insideQuotes -> {
                    fields.add(currentField.toString())
                    currentField.clear()
                }
                else -> currentField.append(char)
            }
        }
        
        fields.add(currentField.toString())
        return fields
    }
    
    /**
     * Safely parse float from string
     */
    private fun parseFloat(value: String): Float {
        return try {
            val cleanValue = value.trim().trim('"')
            if (cleanValue.isEmpty() || cleanValue == "null") 0.0f
            else cleanValue.toFloat()
        } catch (e: Exception) {
            0.0f
        }
    }
}