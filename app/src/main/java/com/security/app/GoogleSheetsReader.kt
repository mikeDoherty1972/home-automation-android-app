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
 * Utility class to read sensor data from Google Sheets
 * Uses CSV export format: /export?format=csv&gid=0
 */
class GoogleSheetsReader {
    
    companion object {
        // Your Google Sheets ID and CSV export URL
        private const val SHEET_ID = "1_GuECfJE0nFSGMS6-prrWAfLCR4gBPa3Qf_zzaKzAAA"
        private const val CSV_URL = "https://docs.google.com/spreadsheets/d/$SHEET_ID/export?format=csv&gid=0"
    }
    
    data class SensorReading(
        val timestamp: String,
        val indoorTemp: Float,
        val outdoorTemp: Float,
        val humidity: Float,
        val windSpeed: Float,
        val windDirection: Float,
        val currentPower: Float,
        val currentAmps: Float,
        val dailyPower: Float,
        val dvrTemp: Float,
        val waterTemp: Float,
        val waterPressure: Float
    )
    
    /**
     * Fetch the latest sensor data from Google Sheets
     * Returns the most recent readings for today
     */
    suspend fun fetchLatestReadings(maxRows: Int = 100): List<SensorReading> {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(CSV_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val csvData = reader.readText()
                reader.close()
                
                parseCsvData(csvData, maxRows)
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }
    
    /**
     * Fetch sensor readings for a specific date
     */
    suspend fun fetchReadingsForDate(date: String, maxRows: Int = 500): List<SensorReading> {
        return withContext(Dispatchers.IO) {
            try {
                val allReadings = fetchLatestReadings(1000) // Get more data for date filtering
                
                // Filter readings for the specific date
                allReadings.filter { reading ->
                    reading.timestamp.startsWith(date)
                }.take(maxRows)
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }
    
    /**
     * Parse CSV data into SensorReading objects - ALWAYS GET LAST ROW
     */
    private fun parseCsvData(csvData: String, maxRows: Int): List<SensorReading> {
        val readings = mutableListOf<SensorReading>()
        val lines = csvData.split("\n").filter { it.trim().isNotEmpty() }
        
        // Parse ALL rows first, then take the last ones
        for (i in 1 until lines.size) { // Skip header row (index 0)
            val line = lines[i].trim()
            if (line.isEmpty()) continue
            
            try {
                val fields = parseCsvLine(line)
                if (fields.size >= 12) { // Ensure we have enough columns
                    
                    val reading = SensorReading(
                        timestamp = fields[0].trim('"'), // Date/Time column - COLUMN A
                        indoorTemp = parseFloat(fields[1]), // Indoor temp - COLUMN B
                        outdoorTemp = parseFloat(fields[2]), // Outdoor temp - COLUMN C  
                        humidity = parseFloat(fields[3]), // Humidity - COLUMN D
                        windSpeed = parseFloat(fields[4]), // Wind speed - COLUMN E
                        windDirection = if (fields.size > 12) parseFloat(fields[12]) else 0.0f, // Wind direction - COLUMN M
                        currentPower = parseFloat(fields[7]), // Kilowatts today - COLUMN H
                        currentAmps = parseFloat(fields[8]), // Amps - COLUMN I
                        dailyPower = parseFloat(fields[9]), // Total kilowatts yesterday - COLUMN J
                        dvrTemp = if (fields.size > 13) parseFloat(fields[13]) else 0.0f, // DVR temp - COLUMN N
                        waterTemp = parseFloat(fields[5]), // Water temp - COLUMN F
                        waterPressure = parseFloat(fields[6]) // Water pressure - COLUMN G
                    )
                    
                    readings.add(reading)
                }
            } catch (e: Exception) {
                // Skip invalid rows
                continue
            }
        }
        
        // Return LAST rows only (most recent data)
        return if (readings.size > maxRows) {
            readings.takeLast(maxRows).reversed() // Take last maxRows, most recent first
        } else {
            readings.reversed() // Most recent first
        }
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
        
        // Add the final field
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
     * Get readings for the last N hours
     */
    suspend fun fetchRecentReadings(hours: Int = 24): List<SensorReading> {
        return withContext(Dispatchers.IO) {
            try {
                val allReadings = fetchLatestReadings(500)
                val cutoffTime = System.currentTimeMillis() - (hours * 60 * 60 * 1000)
                
                allReadings.filter { reading ->
                    try {
                        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                            .parse(reading.timestamp)
                        timestamp?.time ?: 0 > cutoffTime
                    } catch (e: Exception) {
                        true // Include if we can't parse timestamp
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }
}