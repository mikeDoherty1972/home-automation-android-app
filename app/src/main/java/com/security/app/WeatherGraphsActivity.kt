package com.security.app

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class WeatherGraphsActivity : AppCompatActivity() {
    
    private lateinit var temperatureChart: LineChart
    private lateinit var humidityChart: LineChart
    private lateinit var windChart: LineChart
    private lateinit var windDirectionChart: LineChart
    private lateinit var dateTitle: TextView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_weather_graphs)
        
        // Initialize views
        temperatureChart = findViewById(R.id.temperatureChart)
        humidityChart = findViewById(R.id.humidityChart)
        windChart = findViewById(R.id.windChart)
        windDirectionChart = findViewById(R.id.windDirectionChart)
        dateTitle = findViewById(R.id.dateTitle)
        
        // Setup return button
        findViewById<TextView>(R.id.returnButton).setOnClickListener {
            finish()
        }
        
        // Set today's date
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        dateTitle.text = getString(R.string.weather_trends_date, today)

        // Setup charts
        setupCharts()
        
        // Load weather data from Google Sheets
        loadWeatherData()
    }
    
    private fun setupCharts() {
        listOf(temperatureChart, humidityChart, windChart, windDirectionChart).forEach { chart ->
            chart.description.isEnabled = false
            chart.setTouchEnabled(true)
            chart.isDragEnabled = true
            chart.setScaleEnabled(true)
            chart.setPinchZoom(true)
            chart.setBackgroundColor(Color.TRANSPARENT)
            
            // X-axis setup
            val xAxis = chart.xAxis
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.setDrawGridLines(true)
            xAxis.textColor = Color.WHITE
            xAxis.gridColor = Color.GRAY
            xAxis.granularity = 60f
            xAxis.labelRotationAngle = -45f

            // Y-axis setup
            val leftAxis = chart.axisLeft
            leftAxis.setDrawGridLines(true)
            leftAxis.textColor = Color.WHITE
            leftAxis.gridColor = Color.GRAY
            
            val rightAxis = chart.axisRight
            rightAxis.isEnabled = false
        }
    }
    
    private fun loadWeatherData() {
        // Use coroutine to load data from Google Sheets
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val sheetsReader = GoogleSheetsReader()
                
                // Get recent readings (last 72 hours to ensure early-day points are included)
                val readings = sheetsReader.fetchRecentReadings(72)

                // Also fetch a small preview of the raw CSV (gid=0) for debugging if needed
                try {
                    val rawCsv = sheetsReader.fetchRawCsvForGid(0)
                    if (!rawCsv.isNullOrEmpty()) {
                        val previewLines = rawCsv.split('\n').filter { it.isNotBlank() }.take(12)
                        Log.d("WeatherGraphs", "Raw CSV preview (gid=0) first ${previewLines.size} lines:\n${previewLines.joinToString("\n")}")
                    }
                } catch (e: Exception) {
                    Log.d("WeatherGraphs", "Failed to fetch raw CSV preview: ${e.message}")
                }

                if (readings.isNotEmpty()) {
                    val indoorTempEntries = mutableListOf<Entry>()
                    val outdoorTempEntries = mutableListOf<Entry>()
                    val humidityEntries = mutableListOf<Entry>()
                    val windEntries = mutableListOf<Entry>()
                    val windDirEntries = mutableListOf<Entry>()
                    // we'll use minutes-since-midnight for X values
                    val timeLabels = mutableListOf<Int>()

                    // Reverse readings so oldest -> newest only if readings are returned most-recent-first
                    fun parseEpoch(ts: String): Long? {
                        val patterns = listOf(
                            // Try common patterns; prefer two-digit year patterns last but handle them explicitly
                            "yyyy-MM-dd HH:mm:ss",
                            "yyyy-MM-dd HH:mm",
                            "yyyy/MM/dd HH:mm:ss",
                            "yyyy/MM/dd HH:mm",
                            "MM/dd/yyyy HH:mm:ss",
                            "MM/dd/yyyy HH:mm",
                            "yyyy-MM-dd'T'HH:mm:ss",
                            "yyyy-MM-dd'T'HH:mm:ss'Z'",
                            // two-digit year patterns (will be used if necessary)
                            "MM/dd/yy HH:mm:ss",
                            "MM/dd/yy HH:mm"
                        )

                        for (p in patterns) {
                            try {
                                val sdf = SimpleDateFormat(p, Locale.getDefault())
                                sdf.isLenient = false
                                val d = sdf.parse(ts)
                                if (d != null) {
                                    // If parsed year is unexpectedly small (e.g. year 25 instead of 2025),
                                    // detect two-digit-year input and repars with 'yy' patterns.
                                    val cal = Calendar.getInstance().apply { time = d }
                                    val year = cal.get(Calendar.YEAR)
                                    if (year < 1970) {
                                        // detect two-digit year in the input (e.g. \b\d{1,2}/\d{1,2}/\d{2}\b)
                                        val twoDigitYearRegex = Regex("\\b\\d{1,2}/\\d{1,2}/\\d{2}(?:\\b|\\s)")
                                        if (twoDigitYearRegex.containsMatchIn(ts)) {
                                            // try explicit two-digit-year patterns
                                            try {
                                                val sdf2 = SimpleDateFormat("MM/dd/yy HH:mm:ss", Locale.getDefault())
                                                sdf2.isLenient = false
                                                val d2 = sdf2.parse(ts)
                                                if (d2 != null) return d2.time
                                            } catch (_: Exception) {
                                            }
                                            try {
                                                val sdf2 = SimpleDateFormat("MM/dd/yy HH:mm", Locale.getDefault())
                                                sdf2.isLenient = false
                                                val d2 = sdf2.parse(ts)
                                                if (d2 != null) return d2.time
                                            } catch (_: Exception) {
                                            }
                                            // Deterministic manual parse for two-digit-year formats as a last resort
                                            // Pattern: MM/DD/YY HH:MM(:SS)?
                                            val manualRegex = Regex("\\b(\\d{1,2})/(\\d{1,2})/(\\d{2})[ T]+(\\d{1,2}):(\\d{2})(?::(\\d{2}))?\\b")
                                            val m = manualRegex.find(ts)
                                            if (m != null) {
                                                try {
                                                    val (mm, dd, yy, hh, min, ss) = m.destructured
                                                    val year2000 = 2000 + yy.toInt()
                                                    val cal2 = Calendar.getInstance()
                                                    cal2.set(Calendar.YEAR, year2000)
                                                    cal2.set(Calendar.MONTH, mm.toInt() - 1)
                                                    cal2.set(Calendar.DAY_OF_MONTH, dd.toInt())
                                                    cal2.set(Calendar.HOUR_OF_DAY, hh.toInt())
                                                    cal2.set(Calendar.MINUTE, min.toInt())
                                                    cal2.set(Calendar.SECOND, ss.takeIf { it.isNotEmpty() }?.toInt() ?: 0)
                                                    cal2.set(Calendar.MILLISECOND, 0)
                                                    return cal2.time.time
                                                } catch (_: Exception) {
                                                    // fall through
                                                }
                                            }
                                        }
                                    }
                                    return d.time
                                }
                            } catch (_: Exception) {
                                // try next pattern
                            }
                        }

                        return null
                    }

                    val firstParsed = parseEpoch(readings.first().timestamp)
                    val lastParsed = parseEpoch(readings.last().timestamp)
                    Log.d("WeatherGraphs", "firstTs=${readings.first().timestamp} -> $firstParsed, lastTs=${readings.last().timestamp} -> $lastParsed")
                    val orderedReadings = if (firstParsed != null && lastParsed != null) {
                        if (firstParsed > lastParsed) readings.reversed() else readings
                    } else {
                        // If we can't parse reliably, fall back to the old behavior (reverse)
                        readings.reversed()
                    }

                    // Parse Google Sheets data into epochs (ms). Use parseEpoch() when possible.
                    val epochs = orderedReadings.map { r -> parseEpoch(r.timestamp) }
                    val nonNullEpochs = epochs.filterNotNull()

                    if (nonNullEpochs.isNotEmpty()) {
                        // Use earliest epoch as base
                        val baseEpoch = nonNullEpochs.minOrNull() ?: nonNullEpochs.first()
                        // Build entries as minutes since baseEpoch
                        orderedReadings.forEachIndexed { i, reading ->
                            val epoch = parseEpoch(reading.timestamp) ?: (baseEpoch + parseTimeToMinutes(reading.timestamp) * 60_000L)
                            val minutesSinceBase = ((epoch - baseEpoch) / 60_000L).toFloat()
                            indoorTempEntries.add(Entry(minutesSinceBase, reading.indoorTemp))
                            outdoorTempEntries.add(Entry(minutesSinceBase, reading.outdoorTemp))
                            humidityEntries.add(Entry(minutesSinceBase, reading.humidity))
                            windEntries.add(Entry(minutesSinceBase, reading.windSpeed))
                            windDirEntries.add(Entry(minutesSinceBase, reading.windDirection))
                        }

                        // Diagnostics
                        val indoorSorted = indoorTempEntries.sortedBy { it.x }
                        val outdoorSorted = outdoorTempEntries.sortedBy { it.x }
                        val indoorCount = indoorSorted.size
                        val outdoorCount = outdoorSorted.size
                        val indoorMin = indoorSorted.firstOrNull()?.x?.toInt() ?: -1
                        val indoorMax = indoorSorted.lastOrNull()?.x?.toInt() ?: -1
                        val outdoorMin = outdoorSorted.firstOrNull()?.x?.toInt() ?: -1
                        val outdoorMax = outdoorSorted.lastOrNull()?.x?.toInt() ?: -1
                        val indoorZeroCount = indoorSorted.count { it.y == 0f }
                        val outdoorZeroCount = outdoorSorted.count { it.y == 0f }
                        Log.d("WeatherGraphs", "indoorCount=$indoorCount, indoorMin=$indoorMin, indoorMax=$indoorMax, indoorZeroCount=$indoorZeroCount")
                        Log.d("WeatherGraphs", "outdoorCount=$outdoorCount, outdoorMin=$outdoorMin, outdoorMax=$outdoorMax, outdoorZeroCount=$outdoorZeroCount")

                        // Prepare fallback detection (all X equal or many zeros)
                        val useFallbackXIndoor = indoorSorted.isNotEmpty() && (indoorSorted.all { it.x == indoorSorted.first().x } || indoorZeroCount >= (indoorCount * 0.8).toInt())
                        val useFallbackXOutdoor = outdoorSorted.isNotEmpty() && (outdoorSorted.all { it.x == outdoorSorted.first().x } || outdoorZeroCount >= (outdoorCount * 0.8).toInt())

                        val plottedIndoor = if (useFallbackXIndoor && indoorCount > 1) {
                            Log.d("WeatherGraphs", "Using fallback X spacing for indoor data")
                            indoorSorted.mapIndexed { idx, e ->
                                val span = (indoorCount - 1).coerceAtLeast(1)
                                val x = (idx.toFloat() / span) * (24 * 60)
                                Entry(x, e.y)
                            }
                        } else indoorSorted

                        val plottedOutdoor = if (useFallbackXOutdoor && outdoorCount > 1) {
                            Log.d("WeatherGraphs", "Using fallback X spacing for outdoor data")
                            outdoorSorted.mapIndexed { idx, e ->
                                val span = (outdoorCount - 1).coerceAtLeast(1)
                                val x = (idx.toFloat() / span) * (24 * 60)
                                Entry(x, e.y)
                            }
                        } else outdoorSorted

                        val spanMinutes = (nonNullEpochs.maxOrNull()!! - baseEpoch).toInt() / 60_000

                        // Update UI on main thread with baseEpoch and span
                        withContext(Dispatchers.Main) {
                            updateTemperatureChart(plottedIndoor, plottedOutdoor, baseEpoch, spanMinutes)
                            updateHumidityChart(humidityEntries, baseEpoch, spanMinutes)
                            updateWindChart(windEntries, baseEpoch, spanMinutes)
                            updateWindDirectionChart(windDirEntries, baseEpoch, spanMinutes)

                            // Update title with data count
                            dateTitle.text = getString(R.string.weather_trends_count, readings.size)
                        }
                    } else {
                        // Could not parse epoch for any reading: fallback to old minutes-based plotting
                        orderedReadings.forEachIndexed { i, reading ->
                            val minutes = parseTimeToMinutes(reading.timestamp)
                            indoorTempEntries.add(Entry(minutes.toFloat(), reading.indoorTemp))
                            outdoorTempEntries.add(Entry(minutes.toFloat(), reading.outdoorTemp))
                            humidityEntries.add(Entry(minutes.toFloat(), reading.humidity))
                            windEntries.add(Entry(minutes.toFloat(), reading.windSpeed))
                            windDirEntries.add(Entry(minutes.toFloat(), reading.windDirection))
                            timeLabels.add(minutes)
                        }

                        withContext(Dispatchers.Main) {
                            updateTemperatureChart(indoorTempEntries, outdoorTempEntries, 0L, 24 * 60)
                            updateHumidityChart(humidityEntries, 0L, 24 * 60)
                            updateWindChart(windEntries, 0L, 24 * 60)
                            updateWindDirectionChart(windDirEntries, 0L, 24 * 60)

                            dateTitle.text = getString(R.string.weather_trends_count, readings.size)
                        }
                    }
                } else {
                    // No data available, show empty charts
                    withContext(Dispatchers.Main) {
                        showEmptyCharts()
                        dateTitle.text = getString(R.string.weather_trends_no_data)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    showEmptyCharts()
                    dateTitle.text = getString(R.string.weather_trends_error)
                }
            }
        }
    }
    
    private fun updateTemperatureChart(indoorEntries: List<Entry>, outdoorEntries: List<Entry>, baseEpoch: Long, spanMinutes: Int) {
         // Indoor temperature dataset
         val indoorDataSet = LineDataSet(indoorEntries, "Indoor °C")
         indoorDataSet.color = Color.RED
         indoorDataSet.setCircleColor(Color.RED)
         indoorDataSet.lineWidth = 3f
         indoorDataSet.circleRadius = 4f
         indoorDataSet.setDrawCircleHole(false)
         indoorDataSet.valueTextSize = 8f
         indoorDataSet.valueTextColor = Color.WHITE

         // Outdoor temperature dataset
         val outdoorDataSet = LineDataSet(outdoorEntries, "Outdoor °C")
         outdoorDataSet.color = Color.CYAN
         outdoorDataSet.setCircleColor(Color.CYAN)
         outdoorDataSet.lineWidth = 3f
         outdoorDataSet.circleRadius = 4f
         outdoorDataSet.setDrawCircleHole(false)
         outdoorDataSet.valueTextSize = 8f
         outdoorDataSet.valueTextColor = Color.WHITE

         val lineData = LineData(indoorDataSet, outdoorDataSet)
         temperatureChart.data = lineData

         // Dynamic Y axis for temperature: remove min/max, enable auto-scale
         val leftAxis = temperatureChart.axisLeft
         temperatureChart.setAutoScaleMinMaxEnabled(true)
         leftAxis.removeAllLimitLines()
         // Remove any fixed min/max
         leftAxis.resetAxisMinimum()
         leftAxis.resetAxisMaximum()
         // Ensure grid/label styling (keeps behavior consistent)
         leftAxis.setDrawGridLines(true)
         leftAxis.textColor = Color.WHITE
         leftAxis.gridColor = Color.GRAY

         // Formatter for minutes -> HH:mm
         val timeFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val minutes = ((value.toInt() % (24 * 60)) + (24 * 60)) % (24 * 60)
                val h = minutes / 60
                val m = minutes % 60
                return String.format(Locale.getDefault(), "%02d:%02d", h, m)
            }
        }
        temperatureChart.xAxis.valueFormatter = timeFormatter
        temperatureChart.xAxis.axisMinimum = 0f
        temperatureChart.xAxis.axisMaximum = spanMinutes.toFloat()
        temperatureChart.data?.notifyDataChanged()
        temperatureChart.notifyDataSetChanged()
        temperatureChart.setVisibleXRangeMaximum(spanMinutes.toFloat())
        temperatureChart.moveViewToX(0f)
        android.util.Log.d("WeatherGraphs", "Temperature axis set to dynamic (auto-scale)")
        temperatureChart.invalidate()
     }

    private fun updateHumidityChart(humidityEntries: List<Entry>, baseEpoch: Long, spanMinutes: Int) {
         val humidityDataSet = LineDataSet(humidityEntries, "Humidity %")
         humidityDataSet.color = Color.BLUE
         humidityDataSet.setCircleColor(Color.BLUE)
         humidityDataSet.lineWidth = 3f
         humidityDataSet.circleRadius = 4f
         humidityDataSet.setDrawCircleHole(false)
         humidityDataSet.valueTextSize = 8f
         humidityDataSet.valueTextColor = Color.WHITE

         val lineData = LineData(humidityDataSet)
         humidityChart.data = lineData
         val leftAxis = humidityChart.axisLeft
         // Set Y axis 0-100%, granularity 10, label count 11
         leftAxis.setAxisMinimum(0f)
         leftAxis.setAxisMaximum(100f)
         leftAxis.granularity = 10f
         leftAxis.setLabelCount(11, true)
         leftAxis.isGranularityEnabled = true
         leftAxis.setDrawGridLines(true)
         leftAxis.textColor = Color.WHITE
         leftAxis.gridColor = Color.GRAY
         humidityChart.setAutoScaleMinMaxEnabled(false)
         val timeFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val minutes = ((value.toInt() % (24 * 60)) + (24 * 60)) % (24 * 60)
                val h = minutes / 60
                val m = minutes % 60
                return String.format(Locale.getDefault(), "%02d:%02d", h, m)
            }
        }
        humidityChart.xAxis.valueFormatter = timeFormatter
        humidityChart.xAxis.axisMinimum = 0f
        humidityChart.xAxis.axisMaximum = spanMinutes.toFloat()
        humidityChart.data?.notifyDataChanged()
        humidityChart.notifyDataSetChanged()
        humidityChart.setVisibleXRangeMaximum(spanMinutes.toFloat())
        humidityChart.moveViewToX(0f)
        humidityChart.invalidate()
     }

    private fun updateWindChart(windEntries: List<Entry>, baseEpoch: Long, spanMinutes: Int) {
         val windDataSet = LineDataSet(windEntries, "Wind km/h")
         windDataSet.color = Color.GREEN
         windDataSet.setCircleColor(Color.GREEN)
         windDataSet.lineWidth = 3f
         windDataSet.circleRadius = 4f
         windDataSet.setDrawCircleHole(false)
         windDataSet.valueTextSize = 8f
         windDataSet.valueTextColor = Color.WHITE

         val lineData = LineData(windDataSet)
         windChart.data = lineData
         val timeFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val minutes = ((value.toInt() % (24 * 60)) + (24 * 60)) % (24 * 60)
                val h = minutes / 60
                val m = minutes % 60
                return String.format(Locale.getDefault(), "%02d:%02d", h, m)
            }
        }
        windChart.xAxis.valueFormatter = timeFormatter
        windChart.xAxis.axisMinimum = 0f
        windChart.xAxis.axisMaximum = spanMinutes.toFloat()
        windChart.data?.notifyDataChanged()
        windChart.notifyDataSetChanged()
        windChart.fitScreen()
        windChart.setVisibleXRangeMaximum(spanMinutes.toFloat())
        windChart.moveViewToX(0f)
        windChart.invalidate()
     }

    private fun updateWindDirectionChart(dirEntries: List<Entry>, baseEpoch: Long, spanMinutes: Int) {
         val dirDataSet = LineDataSet(dirEntries, "Wind Dir °")
         dirDataSet.color = Color.MAGENTA
         dirDataSet.setCircleColor(Color.MAGENTA)
         dirDataSet.lineWidth = 2.5f
         dirDataSet.circleRadius = 3.5f
         dirDataSet.setDrawCircleHole(false)
         dirDataSet.valueTextSize = 8f
         dirDataSet.valueTextColor = Color.WHITE

         val lineData = LineData(dirDataSet)
         windDirectionChart.data = lineData
         val timeFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val minutes = ((value.toInt() % (24 * 60)) + (24 * 60)) % (24 * 60)
                val h = minutes / 60
                val m = minutes % 60
                return String.format(Locale.getDefault(), "%02d:%02d", h, m)
            }
        }
        windDirectionChart.xAxis.valueFormatter = timeFormatter
        windDirectionChart.xAxis.axisMinimum = 0f
        windDirectionChart.xAxis.axisMaximum = spanMinutes.toFloat()
        windDirectionChart.data?.notifyDataChanged()
        windDirectionChart.notifyDataSetChanged()
        windDirectionChart.fitScreen()
        windDirectionChart.setVisibleXRangeMaximum(spanMinutes.toFloat())
        windDirectionChart.moveViewToX(0f)

         // Constrain Y axis to 0..360 degrees for clarity
         val leftAxis = windDirectionChart.axisLeft
         leftAxis.axisMinimum = 0f
         leftAxis.axisMaximum = 360f
         leftAxis.granularity = 45f

         windDirectionChart.invalidate()
     }

     private fun showEmptyCharts() {
         // Show empty charts with placeholder data
         val emptyEntries = listOf(Entry(0f, 0f))
         val emptyLabels = listOf(0)

        updateTemperatureChart(emptyEntries, emptyEntries, 0L, 24 * 60)
        updateHumidityChart(emptyEntries, 0L, 24 * 60)
        updateWindChart(emptyEntries, 0L, 24 * 60)
        updateWindDirectionChart(emptyEntries, 0L, 24 * 60)
    }

    // Helper: parse a timestamp (expected like "YYYY-MM-DD HH:MM:SS" or "YYYY-MM-DD HH:MM") to minutes since midnight
     private fun parseTimeToMinutes(timestamp: String): Int {
        // Prefer a flexible regex that finds the first HH:MM occurrence (handles missing seconds and different separators)
        val regex = Regex("(\\d{1,2}):(\\d{2})")
        val match = regex.find(timestamp)
        if (match != null) {
            val h = match.groupValues[1].toIntOrNull() ?: 0
            val m = match.groupValues[2].toIntOrNull() ?: 0
            return ((h % 24) * 60) + (m % 60)
        }

        // Fallback: existing simple split-on-space approach
        val timePart = timestamp.split(" ").getOrNull(1) ?: return 0
        val parts = timePart.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return (hour * 60) + minute
     }
 }
