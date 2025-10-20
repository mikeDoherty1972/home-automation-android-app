package com.security.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

/**
 * IPERL-specific Google Sheets reader for water meter data
 * Reads from mike_data and mike_RSSI tabs
 */
class IPERLGoogleSheetsReader {
    companion object {
        // Google Sheets IDs and URLs
        private const val SHEET_ID = "1_GuECfJE0nFSGMS6-prrWAfLCR4gBPa3Qf_zzaKzAAA"
        // Water meters card: main sheet (gid=0)
        private const val MIKE_DATA_URL = "https://docs.google.com/spreadsheets/d/$SHEET_ID/export?format=csv&gid=0"
        // Water usage statistics card: stats sheet (gid=1172995861)
        private const val USAGE_STATS_URL = "https://docs.google.com/spreadsheets/d/$SHEET_ID/export?format=csv&gid=1172995861"
        // mike_RSSI tab URL (unchanged)
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
     * Mike's Water Meter chart: fetches from column K (value) and column A (timestamp)
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
                android.util.Log.d("IPERLGoogleSheetsReader", "Raw mike_data CSV size: ${csvData.length}")
                parseWaterMeterDataColumnAandK(csvData, maxRows)
            } catch (_: Exception) {
                // intentionally ignore network/parse errors here and return an empty list
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
                // Log the raw CSV data size for debugging
                android.util.Log.d("IPERLGoogleSheetsReader", "Raw mike_RSSI CSV size: ${csvData.length}")
                parseSignalData(csvData, maxRows)
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    data class UsageStats(
        val dayUsage: Double,
        val monthUsage: Double,
        val totalUsage: Double,
        val lastUpdate: String
    )

    /**
     * Water usage statistics card: reads from column A (timestamp) and column B (value)
     * Ensures all values are parsed as Double to avoid type mismatch errors.
     */
    suspend fun getUsageStatistics(): UsageStats {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(USAGE_STATS_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val csvData = reader.readText()
                reader.close()
                android.util.Log.d("IPERLGoogleSheetsReader", "Raw USAGE_STATS_URL CSV size: ${csvData.length}")
                val lines = csvData.lines().map { it.trim() }.filter { it.isNotEmpty() }

                // Debug: log last few raw CSV lines for usage stats
                try {
                    val lastRaw = lines.takeLast(10).joinToString("\n")
                    android.util.Log.d("IPERLGoogleSheetsReader", "[UsageStats] Last raw CSV lines:\n$lastRaw")
                } catch (_: Exception) {}

                if (lines.isEmpty()) return@withContext UsageStats(0.0, 0.0, 0.0, "No Data")
                val headerFields = parseCsvLine(lines[0]).map { it.trim().lowercase(Locale.getDefault()) }
                val isHeader = headerFields.getOrNull(0)?.contains("time") == true || headerFields.getOrNull(1)?.contains("value") == true || headerFields.any { it.contains("value") && it.contains("time").not() }
                val dataLines = if (isHeader) lines.drop(1) else lines
                val timeIdx = 0 // Column A (timestamp)
                val valueIdx = 1 // Column B (value)
                val dateFormatCandidates = listOf("dd/MM/yyyy HH:mm:ss", "dd/MM/yy HH:mm:ss", "MM/dd/yy HH:mm:ss", "MM/dd/yyyy HH:mm:ss", "yyyy-MM-dd HH:mm:ss")
                val todayFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                val monthFormat = SimpleDateFormat("MM/yyyy", Locale.getDefault())
                val todayStr = todayFormat.format(Date())
                val thisMonthStr = monthFormat.format(Date())

                val parsedReadings = dataLines.mapNotNull { line ->
                    val fields = parseCsvLine(line).map { it.trim() }
                    if (fields.isEmpty()) return@mapNotNull null

                    // detect numeric and date-like fields anywhere in the row
                    val numIdx = fields.indexOfLast { parseDoubleFlexible(it) != null }
                    val dfIdx = fields.indexOfFirst { parseDateFlexible(it, dateFormatCandidates) != null }

                    // choose value field: prefer column B (valueIdx) if it's numeric; otherwise use detected numeric index
                    var valueField: String? = fields.getOrNull(valueIdx)
                    if (valueField == null || parseDoubleFlexible(valueField) == null) {
                        if (numIdx >= 0) valueField = fields[numIdx]
                    }

                    // choose date field: prefer column A (timeIdx) if it's a date; otherwise use detected date index
                    var dateField: String? = fields.getOrNull(timeIdx)
                    if (dateField == null || parseDateFlexible(dateField, dateFormatCandidates) == null) {
                        if (dfIdx >= 0) dateField = fields[dfIdx]
                    }

                    if (valueField == null || dateField == null) return@mapNotNull null
                    val value = parseDoubleFlexible(valueField)
                    val date = parseDateFlexible(dateField, dateFormatCandidates)
                    if (value != null && date != null) Pair(date, value) else null
                }.sortedBy { it.first }

                // Fallback: if parsedReadings is empty, try the simple pattern value,timestamp,... (observed in logs)
                val finalReadings = if (parsedReadings.isEmpty()) {
                    val alt = dataLines.mapNotNull { line ->
                        val fields = parseCsvLine(line).map { it.trim() }
                        if (fields.size >= 2) {
                            val v = parseDoubleFlexible(fields[0])
                            val d = parseDateFlexible(fields[1], dateFormatCandidates)
                            if (v != null && d != null) Pair(d, v) else null
                        } else null
                    }.sortedBy { it.first }
                    alt
                } else parsedReadings

                // Scale readings by 1000 to move decimal 3 places to the right (user requested)
                val scaledReadings = finalReadings.map { Pair(it.first, it.second * 1000.0) }

                // Debug: log parsed usage stats summary (both raw and scaled)
                try {
                    android.util.Log.d("IPERLGoogleSheetsReader", "[UsageStats] Parsed UsageStats readings (raw): ${finalReadings.size} Last=${finalReadings.lastOrNull()?.second} ${finalReadings.lastOrNull()?.first}")
                    android.util.Log.d("IPERLGoogleSheetsReader", "[UsageStats] Parsed UsageStats readings (scaled x1000): ${scaledReadings.size} Last=${scaledReadings.lastOrNull()?.second} ${scaledReadings.lastOrNull()?.first}")
                } catch (_: Exception) {}

                if (scaledReadings.isEmpty()) return@withContext UsageStats(0.0, 0.0, 0.0, "No Data")
                val totalUsage = scaledReadings.last().second
                val lastUpdate = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(scaledReadings.last().first)
                val dayReadings = scaledReadings.filter { todayFormat.format(it.first) == todayStr }
                val monthReadings = scaledReadings.filter { monthFormat.format(it.first) == thisMonthStr }
                val dayUsage = if (dayReadings.size > 1) (dayReadings.last().second - dayReadings.first().second) else 0.0
                val monthUsage = if (monthReadings.size > 1) (monthReadings.last().second - monthReadings.first().second) else 0.0
                UsageStats(
                    dayUsage = dayUsage,
                    monthUsage = monthUsage,
                    totalUsage = totalUsage,
                    lastUpdate = lastUpdate
                )
            } catch (e: Exception) {
                e.printStackTrace()
                UsageStats(0.0, 0.0, 0.0, "Error")
            }
        }
    }

    /**
     * Parse CSV data for Water Meters Card (today's water usage)
     * Uses column K (index 10) for value and column A (index 0) for timestamp.
     * Skips header row robustly and supports multiple date formats.
     */
    private fun parseWaterMeterDataColumnAandK(csvData: String, maxRows: Int): List<WaterMeterReading> {
        val readings = mutableListOf<WaterMeterReading>()
        val lines = csvData.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return readings
        val timeIdx = 0 // Column A
        val waterIdx = 10 // Column K

        // Debug: log last few raw CSV lines to help diagnose format issues
        try {
            val lastRaw = lines.takeLast(10).joinToString("\n")
            android.util.Log.d("IPERLGoogleSheetsReader", "[WaterMeter] Last raw CSV lines:\n$lastRaw")
        } catch (_: Exception) {}

        val headerFields = parseCsvLine(lines[0]).map { it.trim().lowercase(Locale.getDefault()) }
        val isHeader = headerFields.getOrNull(0)?.contains("time") == true || headerFields.getOrNull(waterIdx)?.contains("water") == true || headerFields.any { it.contains("value") }
        val dataLines = if (isHeader) lines.drop(1) else lines

        val dateFormatCandidates = listOf("MM/dd/yy HH:mm:ss", "MM/dd/yyyy HH:mm:ss", "dd/MM/yyyy HH:mm:ss", "yyyy-MM-dd HH:mm:ss")
        val startIdx = maxOf(0, dataLines.size - maxRows)
        for (i in startIdx until dataLines.size) {
            val line = dataLines[i]
            val fields = parseCsvLine(line).map { it.trim() }
            if (fields.isEmpty()) continue
            // Try to get timestamp at timeIdx; fallback: find any date-like field
            var timestampField: String? = fields.getOrNull(timeIdx)
            if (timestampField == null || timestampField.isEmpty()) {
                val dfIdx = fields.indexOfFirst { parseDateFlexible(it, dateFormatCandidates) != null }
                if (dfIdx >= 0) timestampField = fields[dfIdx]
            }
            // Try to get water value at waterIdx; fallback: pick the last numeric-like field
            var valueField: String? = fields.getOrNull(waterIdx)
            if (valueField == null || valueField.isEmpty()) {
                val numIdx = fields.indexOfLast { parseFloatNullable(it) != null || it.trim().matches(Regex("[0-9]+(\\.[0-9]+)?(,[0-9]+)?")) }
                if (numIdx >= 0) valueField = fields[numIdx]
            }
            if (timestampField == null || valueField == null) continue
            val meterValue = parseFloatFlexible(valueField)
            val date = parseDateFlexible(timestampField, dateFormatCandidates)
            if (date != null) {
                readings.add(WaterMeterReading(timestampField, meterValue, date))
            }
        }
        // Debug: log last parsed reading (if any)
        try {
            val last = readings.lastOrNull()
            android.util.Log.d("IPERLGoogleSheetsReader", "[WaterMeter] Parsed WaterMeterReadings: ${readings.size} Last=${last?.timestamp} ${last?.meterReading}")
        } catch (_: Exception) {}
        return readings // last() is the most recent
    }

    /**
     * Parse CSV data into SignalReading objects (reads last N rows)
     */
    private fun parseSignalData(csvData: String, maxRows: Int): List<SignalReading> {
        val readings = mutableListOf<SignalReading>()
        val lines = csvData.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return readings
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        val startIdx = maxOf(0, lines.size - maxRows)
        for (i in startIdx until lines.size) {
            val line = lines[i]
            val fields = parseCsvLine(line).map { it.trim() }
            if (fields.isEmpty()) continue
            // Skip header or invalid rows
            if (fields.any { it.contains("RSSI", ignoreCase = true) || it.contains("timestamp", ignoreCase = true) }) continue
            if (fields.size < 2) continue
            try {
                val rssi = parseFloatFlexible(fields[0])
                val timestamp = fields[1].trim().trim('"')
                val date = try { dateFormat.parse(timestamp) } catch (_: Exception) { null }
                if (date != null) {
                    readings.add(SignalReading(timestamp, rssi, date))
                }
            } catch (_: Exception) { continue }
        }
        android.util.Log.d("IPERLGoogleSheetsReader", "Parsed SignalReadings: ${readings.size}")
        return readings
    }

    /**
     * Parse a CSV line handling quoted fields and escaped quotes
     */
    private fun parseCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var insideQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' -> {
                    // handle escaped double-quote "" inside a quoted field
                    if (insideQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        insideQuotes = !insideQuotes
                    }
                }
                ch == ',' && !insideQuotes -> {
                    fields.add(current.toString())
                    current.clear()
                }
                else -> current.append(ch)
            }
            i++
        }
        fields.add(current.toString())
        return fields
    }

    /**
     * Safely parse float from string and accept comma decimal separators
     */
    private fun parseFloatFlexible(value: String): Float {
        return try {
            var clean = value.trim().trim('"')
            if (clean.isEmpty() || clean.equals("null", ignoreCase = true)) return 0.0f
            // If contains both '.' and ',' assume '.' is decimal and ',' is thousand separators -> remove commas
            if (clean.contains(',') && clean.contains('.')) {
                clean = clean.replace(",", "")
            } else if (clean.count { it == ',' } == 1 && !clean.contains('.')) {
                // euro style decimal
                clean = clean.replace(',', '.')
            } else {
                // remove any stray spaces
                clean = clean.replace(" ", "")
            }
            val parsed = clean.toFloatOrNull()
            if (parsed != null) return parsed
            // fallback: extract first numeric substring like 123 or 123.45
            val numMatch = Regex("[-+]?[0-9]+(\\.[0-9]+)?").find(clean)
            numMatch?.value?.toFloatOrNull() ?: 0.0f
        } catch (_: Exception) {
            0.0f
        }
    }

    private fun parseFloatNullable(value: String): Float? {
        return try {
            var clean = value.trim().trim('"')
            if (clean.isEmpty() || clean.equals("null", ignoreCase = true)) return null
            if (clean.contains(',') && clean.contains('.')) {
                clean = clean.replace(",", "")
            } else if (clean.count { it == ',' } == 1 && !clean.contains('.')) {
                clean = clean.replace(',', '.')
            } else {
                clean = clean.replace(" ", "")
            }
            val parsed = clean.toFloatOrNull()
            if (parsed != null) return parsed
            // fallback: extract first numeric substring like 123 or 123.45
            val numMatch = Regex("[-+]?[0-9]+(\\.[0-9]+)?").find(clean)
            numMatch?.value?.toFloatOrNull()
        } catch (_: Exception) {
            null
        }
    }

    private fun parseDoubleFlexible(value: String): Double? {
        return try {
            var clean = value.trim().trim('"')
            if (clean.isEmpty() || clean.equals("null", ignoreCase = true)) return null
            if (clean.contains(',') && clean.contains('.')) {
                clean = clean.replace(",", "")
            } else if (clean.count { it == ',' } == 1 && !clean.contains('.')) {
                clean = clean.replace(',', '.')
            } else {
                clean = clean.replace(" ", "")
            }
            clean.toDoubleOrNull()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Try multiple common date formats to parse a timestamp string.
     */
    private fun parseDateFlexible(input: String, formats: List<String>): Date? {
        val trimmed = input.trim().trim('"')
        for (fmt in formats) {
            try {
                val sdf = SimpleDateFormat(fmt, Locale.getDefault())
                sdf.isLenient = false
                return sdf.parse(trimmed)
            } catch (_: ParseException) {
                // try next
            } catch (_: Exception) {
                // ignore
            }
        }
        return null
    }

    /**
     * Fetch the latest value from column K (11th column, zero-based index 10) of the first sheet
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
                val lines = csvData.lines().map { it.trim() }.filter { it.isNotEmpty() }
                if (lines.isEmpty()) return@withContext null
                val dataLines = lines.dropWhile { parseCsvLine(it).any { f -> f.lowercase(Locale.getDefault()).contains("value") } }
                if (dataLines.isEmpty()) return@withContext null
                val lastLine = dataLines.last()
                val fields = parseCsvLine(lastLine).map { it.trim() }
                if (fields.size > 10) {
                    val valueStr = fields[10]
                    val valueInt = parseFloatFlexible(valueStr).toInt()
                    valueInt.toString()
                } else null
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    /**
     * Municipality Water Meter Monitoring: fetches from column K (value) and column A (timestamp)
     */
    @Suppress("unused")
    suspend fun fetchMunicipalityWaterMeterReading(): Int {
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
                val lines = csvData.lines().map { it.trim() }.filter { it.isNotEmpty() }
                if (lines.isEmpty()) return@withContext 0
                val dataLines = lines.dropWhile { parseCsvLine(it).any { f -> f.lowercase(Locale.getDefault()).contains("value") } }
                if (dataLines.isEmpty()) return@withContext 0
                val lastLine = dataLines.last()
                val fields = parseCsvLine(lastLine).map { it.trim() }
                if (fields.size > 10) {
                    val valueStr = fields[10]
                    val valueInt = parseFloatFlexible(valueStr).toInt()
                    valueInt
                } else 0
            } catch (_: Exception) {
                0
            }
        }
    }

    /**
     * Debug helper: download both CSV sheets and save to /sdcard for inspection.
     * Use adb pull /sdcard/iperl_mike.csv and /sdcard/iperl_usage.csv to retrieve.
     */
    suspend fun dumpSheetsToSdcard(): Pair<Boolean, Boolean> {
        return withContext(Dispatchers.IO) {
            var okMike = false
            var okUsage = false
            try {
                try {
                    val urlMike = URL(MIKE_DATA_URL)
                    val connMike = urlMike.openConnection() as HttpURLConnection
                    connMike.requestMethod = "GET"
                    connMike.connectTimeout = 10000
                    connMike.readTimeout = 10000
                    val csvMike = connMike.inputStream.bufferedReader().use { it.readText() }
                    try {
                        val f = java.io.File("/sdcard/iperl_mike.csv")
                        f.writeText(csvMike)
                        android.util.Log.d("IPERLGoogleSheetsReader", "Wrote /sdcard/iperl_mike.csv, ${csvMike.length} bytes")
                        okMike = true
                    } catch (e: Exception) {
                        android.util.Log.e("IPERLGoogleSheetsReader", "Failed to write iperl_mike.csv", e)
                        okMike = false
                    }
                } catch (e: Exception) {
                    android.util.Log.e("IPERLGoogleSheetsReader", "Failed to download mike_data CSV", e)
                    okMike = false
                }
                try {
                    val urlUsage = URL(USAGE_STATS_URL)
                    val connUsage = urlUsage.openConnection() as HttpURLConnection
                    connUsage.requestMethod = "GET"
                    connUsage.connectTimeout = 10000
                    connUsage.readTimeout = 10000
                    val csvUsage = connUsage.inputStream.bufferedReader().use { it.readText() }
                    try {
                        val f = java.io.File("/sdcard/iperl_usage.csv")
                        f.writeText(csvUsage)
                        android.util.Log.d("IPERLGoogleSheetsReader", "Wrote /sdcard/iperl_usage.csv, ${csvUsage.length} bytes")
                        okUsage = true
                    } catch (e: Exception) {
                        android.util.Log.e("IPERLGoogleSheetsReader", "Failed to write iperl_usage.csv", e)
                        okUsage = false
                    }
                } catch (e: Exception) {
                    android.util.Log.e("IPERLGoogleSheetsReader", "Failed to download usage_stats CSV", e)
                    okUsage = false
                }
            } catch (_: Exception) {}
            Pair(okMike, okUsage)
        }
    }
}