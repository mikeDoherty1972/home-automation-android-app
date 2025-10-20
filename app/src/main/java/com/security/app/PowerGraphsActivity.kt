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
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class PowerGraphsActivity : AppCompatActivity() {
    
    private lateinit var currentPowerChart: LineChart
    private lateinit var currentAmpsChart: LineChart
    private lateinit var dailyTotalChart: LineChart
    private lateinit var dateTitle: TextView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_power_graphs)
        
        // Initialize views
        currentPowerChart = findViewById(R.id.currentPowerChart)
        currentAmpsChart = findViewById(R.id.currentAmpsChart)
        dailyTotalChart = findViewById(R.id.dailyTotalChart)
        dateTitle = findViewById(R.id.dateTitle)
        
        // Setup return button
        findViewById<TextView>(R.id.returnButton).setOnClickListener {
            finish()
        }
        
        // Set today's date
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        dateTitle.text = getString(R.string.power_consumption_date, today)

        // Setup charts
        setupCharts()
        
        // Load power data from Google Sheets
        loadPowerData()
    }
    
    private fun setupCharts() {
        listOf(currentPowerChart, currentAmpsChart, dailyTotalChart).forEach { chart ->
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
            xAxis.granularity = 60f // label every hour by default
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
    
    private fun loadPowerData() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val sheetsReader = GoogleSheetsReader()
                
                // Get recent readings (last 72 hours to include earlier points)
                val readings = sheetsReader.fetchRecentReadings(72)

                // optional raw CSV preview for debugging
                try {
                    val rawCsv = sheetsReader.fetchRawCsvForGid(0)
                    if (!rawCsv.isNullOrEmpty()) {
                        val previewLines = rawCsv.split('\n').filter { it.isNotBlank() }.take(10)
                        Log.d("PowerGraphs", "Raw CSV preview (gid=0) first ${previewLines.size} lines:\n${previewLines.joinToString("\n")}")
                    }
                } catch (_: Exception) {
                }

                if (readings.isNotEmpty()) {
                    val powerEntries = mutableListOf<Entry>()
                    val ampsEntries = mutableListOf<Entry>()
                    val dailyEntries = mutableListOf<Entry>()

                    // Determine epochs and build epoch-based X values
                    fun parseEpoch(ts: String): Long? {
                        val patterns = listOf(
                            "yyyy-MM-dd HH:mm:ss",
                            "yyyy-MM-dd HH:mm",
                            "yyyy/MM/dd HH:mm:ss",
                            "yyyy/MM/dd HH:mm",
                            "MM/dd/yyyy HH:mm:ss",
                            "MM/dd/yyyy HH:mm",
                            "yyyy-MM-dd'T'HH:mm:ss",
                            "yyyy-MM-dd'T'HH:mm:ss'Z'",
                            "MM/dd/yy HH:mm:ss",
                            "MM/dd/yy HH:mm"
                        )
                        for (p in patterns) {
                            try {
                                val sdf = SimpleDateFormat(p, Locale.getDefault())
                                sdf.isLenient = false
                                val d = sdf.parse(ts)
                                if (d != null) {
                                    val cal = Calendar.getInstance().apply { time = d }
                                    val year = cal.get(Calendar.YEAR)
                                    if (year < 1970) {
                                        val twoDigitYearRegex = Regex("\\b\\d{1,2}/\\d{1,2}/\\d{2}(?:\\b|\\s)")
                                        if (twoDigitYearRegex.containsMatchIn(ts)) {
                                            try {
                                                val sdf2 = SimpleDateFormat("MM/dd/yy HH:mm:ss", Locale.getDefault())
                                                sdf2.isLenient = false
                                                val d2 = sdf2.parse(ts)
                                                if (d2 != null) return d2.time
                                            } catch (_: Exception) {}
                                            try {
                                                val sdf2 = SimpleDateFormat("MM/dd/yy HH:mm", Locale.getDefault())
                                                sdf2.isLenient = false
                                                val d2 = sdf2.parse(ts)
                                                if (d2 != null) return d2.time
                                            } catch (_: Exception) {}
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
                                                } catch (_: Exception) {}
                                            }
                                        }
                                    }
                                    return d.time
                                }
                            } catch (_: Exception) {
                            }
                        }
                        return null
                    }

                    val orderedReadings = readings.reversed()
                    val epochs = orderedReadings.map { r -> parseEpoch(r.timestamp) }
                    val nonNullEpochs = epochs.filterNotNull()

                    if (nonNullEpochs.isNotEmpty()) {
                        val baseEpoch = nonNullEpochs.minOrNull() ?: nonNullEpochs.first()
                        orderedReadings.forEach { reading ->
                            val epoch = parseEpoch(reading.timestamp) ?: (baseEpoch + parseTimeToMinutes(reading.timestamp) * 60_000L)
                            val minutesSinceBase = ((epoch - baseEpoch) / 60_000L).toFloat()
                            powerEntries.add(Entry(minutesSinceBase, reading.currentPower))
                            ampsEntries.add(Entry(minutesSinceBase, reading.currentAmps))
                            dailyEntries.add(Entry(minutesSinceBase, reading.dailyPower))
                        }

                        val spanMinutes = ((nonNullEpochs.maxOrNull()!! - baseEpoch) / 60_000L).toInt()

                        powerEntries.sortBy { it.x }
                        ampsEntries.sortBy { it.x }
                        dailyEntries.sortBy { it.x }

                        withContext(Dispatchers.Main) {
                            updatePowerCharts(powerEntries, ampsEntries, dailyEntries, baseEpoch, spanMinutes)
                            dateTitle.text = getString(R.string.power_consumption_count, readings.size)
                        }
                    } else {
                        // fallback old behavior (minutes-since-midnight)
                        orderedReadings.forEach { reading ->
                            val minutes = parseTimeToMinutes(reading.timestamp)
                            powerEntries.add(Entry(minutes.toFloat(), reading.currentPower))
                            ampsEntries.add(Entry(minutes.toFloat(), reading.currentAmps))
                            dailyEntries.add(Entry(minutes.toFloat(), reading.dailyPower))
                        }
                        withContext(Dispatchers.Main) {
                            updatePowerCharts(powerEntries, ampsEntries, dailyEntries, 0L, 24 * 60)
                            dateTitle.text = getString(R.string.power_consumption_count, readings.size)
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        showEmptyCharts()
                        dateTitle.text = getString(R.string.power_consumption_no_data)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    showEmptyCharts()
                    dateTitle.text = getString(R.string.power_consumption_error)
                }
            }
        }
    }

    private fun updatePowerCharts(powerEntries: List<Entry>, ampsEntries: List<Entry>, dailyEntries: List<Entry>, baseEpoch: Long, spanMinutes: Int) {
        // Formatter to convert minutes-since-base to HH:mm (wraps every 24h)
        val timeFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val minutes = ((value.toInt() % (24 * 60)) + (24 * 60)) % (24 * 60)
                val h = minutes / 60
                val m = minutes % 60
                return String.format(Locale.getDefault(), "%02d:%02d", h, m)
            }
        }

        // Current Power Chart
        val powerDataSet = LineDataSet(powerEntries, "Power (kW)")
        powerDataSet.color = Color.YELLOW
        powerDataSet.setCircleColor(Color.YELLOW)
        powerDataSet.lineWidth = 3f
        powerDataSet.circleRadius = 4f
        powerDataSet.setDrawCircleHole(false)
        powerDataSet.valueTextSize = 8f
        powerDataSet.valueTextColor = Color.WHITE
        
        currentPowerChart.data = LineData(powerDataSet)
        currentPowerChart.xAxis.valueFormatter = timeFormatter
        currentPowerChart.xAxis.axisMinimum = 0f
        currentPowerChart.xAxis.axisMaximum = spanMinutes.toFloat()
        currentPowerChart.isAutoScaleMinMaxEnabled = false
        currentPowerChart.xAxis.setLabelCount(6, true)
        currentPowerChart.setVisibleXRangeMinimum(spanMinutes.toFloat())
        currentPowerChart.setVisibleXRangeMaximum(spanMinutes.toFloat())
        currentPowerChart.data?.notifyDataChanged()
        currentPowerChart.notifyDataSetChanged()
        currentPowerChart.moveViewToX(0f)
        currentPowerChart.viewPortHandler.refresh(currentPowerChart.viewPortHandler.matrixTouch, currentPowerChart, true)
        currentPowerChart.invalidate()
        
        // Current Amps Chart
        val ampsDataSet = LineDataSet(ampsEntries, "Current (A)")
        ampsDataSet.color = Color.MAGENTA
        ampsDataSet.setCircleColor(Color.MAGENTA)
        ampsDataSet.lineWidth = 3f
        ampsDataSet.circleRadius = 4f
        ampsDataSet.setDrawCircleHole(false)
        ampsDataSet.valueTextSize = 8f
        ampsDataSet.valueTextColor = Color.WHITE
        
        currentAmpsChart.data = LineData(ampsDataSet)
        currentAmpsChart.xAxis.valueFormatter = timeFormatter
        currentAmpsChart.xAxis.axisMinimum = 0f
        currentAmpsChart.xAxis.axisMaximum = spanMinutes.toFloat()
        currentAmpsChart.isAutoScaleMinMaxEnabled = false
        currentAmpsChart.xAxis.setLabelCount(6, true)
        currentAmpsChart.setVisibleXRangeMinimum(spanMinutes.toFloat())
        currentAmpsChart.setVisibleXRangeMaximum(spanMinutes.toFloat())
        currentAmpsChart.data?.notifyDataChanged()
        currentAmpsChart.notifyDataSetChanged()
        currentAmpsChart.moveViewToX(0f)
        currentAmpsChart.viewPortHandler.refresh(currentAmpsChart.viewPortHandler.matrixTouch, currentAmpsChart, true)
        currentAmpsChart.invalidate()
        
        // Daily Total Chart
        val dailyDataSet = LineDataSet(dailyEntries, "Daily Total (kWh)")
        dailyDataSet.color = Color.GREEN
        dailyDataSet.setCircleColor(Color.GREEN)
        dailyDataSet.lineWidth = 3f
        dailyDataSet.circleRadius = 4f
        dailyDataSet.setDrawCircleHole(false)
        dailyDataSet.valueTextSize = 8f
        dailyDataSet.valueTextColor = Color.WHITE
        
        dailyTotalChart.data = LineData(dailyDataSet)
        dailyTotalChart.xAxis.valueFormatter = timeFormatter
        dailyTotalChart.xAxis.axisMinimum = 0f
        dailyTotalChart.xAxis.axisMaximum = spanMinutes.toFloat()
        dailyTotalChart.isAutoScaleMinMaxEnabled = false
        dailyTotalChart.xAxis.setLabelCount(6, true)
        dailyTotalChart.setVisibleXRangeMinimum(spanMinutes.toFloat())
        dailyTotalChart.setVisibleXRangeMaximum(spanMinutes.toFloat())
        dailyTotalChart.data?.notifyDataChanged()
        dailyTotalChart.notifyDataSetChanged()
        dailyTotalChart.moveViewToX(0f)
        dailyTotalChart.viewPortHandler.refresh(dailyTotalChart.viewPortHandler.matrixTouch, dailyTotalChart, true)
        dailyTotalChart.invalidate()
    }
    
    private fun showEmptyCharts() {
        val emptyEntries = listOf(Entry(0f, 0f))
        updatePowerCharts(emptyEntries, emptyEntries, emptyEntries, 0L, 24 * 60)
    }

    // Helper: parse a timestamp (expected like "YYYY-MM-DD HH:MM:SS" or "YYYY-MM-DD HH:MM") to minutes since midnight
    private fun parseTimeToMinutes(timestamp: String): Int {
        val timePart = timestamp.split(" ").getOrNull(1) ?: return 0
        val parts = timePart.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return (hour * 60) + minute
    }
}