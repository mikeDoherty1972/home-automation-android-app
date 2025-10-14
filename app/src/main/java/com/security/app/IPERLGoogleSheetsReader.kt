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
        val timestamp: String, // original string
        val meterReading: Float,
        val date: Date // parsed date for easier calculations
    )
    
    data class SignalReading(
        val timestamp: String,
        val rssi: Float,
        val date: Date // parsed date for easier calculations
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

                // Log the raw CSV data for debugging
                android.util.Log.d("IPERLGoogleSheetsReader", "Raw mike_data CSV:\n$csvData")

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

                // Log the raw CSV data for debugging
                android.util.Log.d("IPERLGoogleSheetsReader", "Raw mike_RSSI CSV:\n$csvData")

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
                val latest = readings.lastOrNull { it.meterReading > 0f && it.date != null } ?: readings.last()
                android.util.Log.d("IPERLGoogleSheetsReader", "Latest reading used: ${latest.meterReading}, ${latest.timestamp}")
                val todayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val thisMonthFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
                // Find the most recent day and month in the data
                val mostRecentDate = readings.maxByOrNull { it.date }?.date
                val mostRecentDay = mostRecentDate?.let { todayFormat.format(it) } ?: ""
                val mostRecentMonth = mostRecentDate?.let { thisMonthFormat.format(it) } ?: ""
                // Calculate latest day usage
                val dayReadings = readings.filter { todayFormat.format(it.date) == mostRecentDay }
                android.util.Log.d("IPERLGoogleSheetsReader", "Latest day's readings: " + dayReadings.joinToString(" | ") { it.meterReading.toString() })
                val dayUsage: Float
                if (dayReadings.size >= 2) {
                    // Calculate usage as sum of positive differences between consecutive readings
                    var sum = 0f
                    for (i in 1 until dayReadings.size) {
                        val diff = dayReadings[i].meterReading - dayReadings[i-1].meterReading
                        android.util.Log.d("IPERLGoogleSheetsReader", "Day diff $i: ${dayReadings[i].meterReading} - ${dayReadings[i-1].meterReading} = $diff")
                        if (diff > 0) sum += diff
                    }
                    dayUsage = if (sum > 0) sum else kotlin.math.abs(dayReadings.maxOf { it.meterReading } - dayReadings.minOf { it.meterReading })
                    android.util.Log.d("IPERLGoogleSheetsReader", "Day usage calculation: sum_positive_diffs=$sum, abs(max-min)=${kotlin.math.abs(dayReadings.maxOf { it.meterReading } - dayReadings.minOf { it.meterReading })}, usage=$dayUsage")
                } else if (dayReadings.size == 1) {
                    val lastBeforeDay = readings.lastOrNull { todayFormat.format(it.date) < mostRecentDay }
                    dayUsage = if (lastBeforeDay != null) kotlin.math.abs(dayReadings[0].meterReading - lastBeforeDay.meterReading) else 0f
                    android.util.Log.d("IPERLGoogleSheetsReader", "Day usage calculation (single reading): current=${dayReadings[0].meterReading}, prev=${lastBeforeDay?.meterReading}, usage=$dayUsage")
                } else {
                    dayUsage = 0f
                }
                // Calculate latest month usage
                val monthReadings = readings.filter { thisMonthFormat.format(it.date) == mostRecentMonth }
                android.util.Log.d("IPERLGoogleSheetsReader", "Latest month's readings: " + monthReadings.joinToString(" | ") { it.meterReading.toString() })
                val monthUsage: Float
                if (monthReadings.size >= 2) {
                    // Use the difference between the first and last valid readings for the month
                    val first = monthReadings.first().meterReading
                    val last = monthReadings.last().meterReading
                    monthUsage = kotlin.math.abs(last - first)
                    android.util.Log.d("IPERLGoogleSheetsReader", "Month usage calculation: first=$first, last=$last, usage=$monthUsage")
                } else if (monthReadings.size == 1) {
                    val lastBeforeMonth = readings.lastOrNull { thisMonthFormat.format(it.date) < mostRecentMonth }
                    monthUsage = if (lastBeforeMonth != null) kotlin.math.abs(monthReadings[0].meterReading - lastBeforeMonth.meterReading) else 0f
                    android.util.Log.d("IPERLGoogleSheetsReader", "Month usage calculation (single reading): current=${monthReadings[0].meterReading}, prev=${lastBeforeMonth?.meterReading}, usage=$monthUsage")
                } else {
                    monthUsage = 0f
                }
                UsageStats(
                    dayUsage = dayUsage * 1000f,
                    monthUsage = monthUsage * 1000f,
                    totalUsage = latest.meterReading,
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
        val lines = csvData.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        val dateFormat1 = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) // primary format
        val dateFormat2 = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()) // legacy format
        val pattern = Regex("^([0-9.]+)(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2})$")
        val dataLines = lines.filter { !it.contains("Value", ignoreCase = true) }
        val startIdx = maxOf(0, dataLines.size - maxRows)
        // Log the last 10 raw lines for debugging
        val lastRawLines = dataLines.takeLast(10)
        android.util.Log.d("IPERLGoogleSheetsReader", "Last 10 raw data lines: " + lastRawLines.joinToString(" | "))
        for (i in startIdx until dataLines.size) {
            val line = dataLines[i]
            var value: String? = null
            var timestamp: String? = null
            if (line.contains(",")) {
                val fields = line.split(",").map { it.trim() }
                if (fields.size >= 2) {
                    value = fields[0]
                    timestamp = fields[1]
                }
            } else {
                val match = pattern.matchEntire(line)
                if (match != null) {
                    value = match.groupValues[1]
                    timestamp = match.groupValues[2]
                }
            }
            if (value.isNullOrEmpty() || timestamp.isNullOrEmpty()) continue
            try {
                val meterReading = parseFloat(value)
                val date = try { dateFormat1.parse(timestamp) } catch (_: Exception) {
                    try { dateFormat2.parse(timestamp) } catch (_: Exception) { null }
                }
                if (date != null) {
                    readings.add(WaterMeterReading(timestamp, meterReading, date))
                }
            } catch (_: Exception) { continue }
        }
        // Debug: Log all parsed readings (up to 20)
        if (readings.size > 0) {
            val allReadings = readings.takeLast(20)
            android.util.Log.d("IPERLGoogleSheetsReader", "All parsed readings: " + allReadings.joinToString(" | ") { it.meterReading.toString() + ", " + it.timestamp })
        }
        return readings // last() is the most recent
    }
    
    /**
     * Parse CSV data into SignalReading objects
     */
    private fun parseSignalData(csvData: String, maxRows: Int): List<SignalReading> {
        val readings = mutableListOf<SignalReading>()
        val lines = csvData.split("\n")
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        for (i in 0 until minOf(lines.size, maxRows)) {
            val line = lines[i].trim()
            if (line.isEmpty()) continue
            val fields = parseCsvLine(line)
            // Skip header or invalid rows
            if (fields.isEmpty() || fields[0].contains("RSSI", ignoreCase = true)) continue
            if (fields.size < 2) continue
            try {
                val rssi = parseFloat(fields[0])
                val timestamp = fields[1].trim('"')
                val date = try { dateFormat.parse(timestamp) } catch (e: Exception) { null }
                if (date != null) {
                    readings.add(SignalReading(timestamp, rssi, date))
                }
            } catch (_: Exception) { continue }
        }
        return readings // last() is the most recent
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

    /**
     * Fetch the latest value from column K (11th column, zero-based index 10) of the first sheet (gid=0)
     * for the Mikes Water Meter card.
     */
    suspend fun fetchLatestColumnKValue(): String? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("https://docs.google.com/spreadsheets/d/$SHEET_ID/export?format=csv&gid=0")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val csvData = reader.readText()
                reader.close()
                val lines = csvData.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                if (lines.size <= 1) return@withContext null // no data
                val dataLines = lines.filter { !it.contains("Value", ignoreCase = true) }
                if (dataLines.isEmpty()) return@withContext null
                val lastLine = dataLines.last()
                val fields = lastLine.split(",").map { it.trim() }
                if (fields.size > 10) fields[10] else null
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}