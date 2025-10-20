package com.security.app

import android.graphics.Color
import android.os.Bundle
import android.widget.TextView
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class DvrGraphsActivity : AppCompatActivity() {
    
    private lateinit var dvrTempChart: LineChart
    private lateinit var currentDvrStatus: TextView
    private lateinit var dateTitle: TextView
    private lateinit var dvrRawCsvPreview: TextView
    private lateinit var dvrRawCsvCard: android.view.View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dvr_graphs)
        
        // Initialize views
        dvrTempChart = findViewById(R.id.dvrTempChart)
        currentDvrStatus = findViewById(R.id.currentDvrStatus)
        dateTitle = findViewById(R.id.dateTitle)
        dvrRawCsvPreview = findViewById(R.id.dvrRawCsvPreview)
        dvrRawCsvCard = findViewById(R.id.dvrRawCsvCard)

        // Setup return button
        findViewById<TextView>(R.id.returnButton).setOnClickListener {
            finish()
        }
        
        // Set today's date
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        dateTitle.text = getString(R.string.dvr_trends_date, today)

        // Setup charts
        setupChart()
        
        // Load DVR data from Google Sheets
        loadDvrData()
    }
    
    private fun setupChart() {
        dvrTempChart.description.isEnabled = false
        dvrTempChart.setTouchEnabled(true)
        dvrTempChart.isDragEnabled = true
        dvrTempChart.setScaleEnabled(true)
        dvrTempChart.setPinchZoom(true)
        dvrTempChart.setBackgroundColor(Color.TRANSPARENT)
        
        // X-axis setup
        val xAxis = dvrTempChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(true)
        xAxis.textColor = Color.WHITE
        xAxis.gridColor = Color.GRAY
        xAxis.granularity = 60f
        xAxis.labelRotationAngle = -45f

        // Y-axis setup
        val leftAxis = dvrTempChart.axisLeft
        leftAxis.setDrawGridLines(true)
        leftAxis.textColor = Color.WHITE
        leftAxis.gridColor = Color.GRAY
        
        val rightAxis = dvrTempChart.axisRight
        rightAxis.isEnabled = false
    }
    
    private fun loadDvrData() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val sheetsReader = GoogleSheetsReader()

                // 1) Get current DVR status from the original source (default CSV_URL gid=0)
                val currentReadings = sheetsReader.fetchLatestReadings(1)
                val latestTempForStatus = if (currentReadings.isNotEmpty()) {
                    currentReadings[0].dvrTemp.toDouble()
                } else {
                    0.0
                }
                Log.d("DvrGraphsActivity", "latestTempForStatus=$latestTempForStatus")

                // 2) Get trend data from the provided sheet gid (the user-specified tab)
                val trendGid = 2109322930
                // Request last 30 days for trend data to ensure older trend tabs are included
                val readings = sheetsReader.fetchRecentReadingsFromGid(trendGid, 720)
                Log.d("DvrGraphsActivity", "trendGid=$trendGid, readings.size=${readings.size}")
                if (readings.isNotEmpty()) {
                    // Hide raw CSV preview if visible
                    withContext(Dispatchers.Main) {
                        dvrRawCsvCard.visibility = android.view.View.GONE
                    }
                    val tempEntries = mutableListOf<Entry>()
                    val timeLabels = mutableListOf<String>()

                    // Reverse readings so oldest -> newest and newest will be plotted on the right
                    val orderedReadings = readings.reversed()

                    orderedReadings.forEachIndexed { i, reading ->
                        val timeLabel = try {
                            val timePart = reading.timestamp.split(" ").getOrNull(1)
                            if (timePart != null && timePart.length >= 5) {
                                timePart.substring(0, 5) // HH:MM
                            } else {
                                // fallback to entire timestamp or empty string
                                reading.timestamp
                            }
                        } catch (_: Exception) {
                            reading.timestamp
                        }
                        timeLabels.add(timeLabel)

                        // safely parse temperature into Float for chart entry
                        // `reading.dvrTemp` is already a Float (see GoogleSheetsReader.SensorReading)
                        val tempFloat: Float = reading.dvrTemp

                        tempEntries.add(Entry(i.toFloat(), tempFloat))
                    }

                    // Prepare chart on main thread
                    withContext(Dispatchers.Main) {
                        // Create dataset
                        val dataSet = LineDataSet(tempEntries, "DVR Temp")
                        dataSet.color = "#FF5722".toColorInt()
                        dataSet.setDrawCircles(false)
                        dataSet.lineWidth = 1.5f
                        dataSet.valueTextColor = Color.WHITE
                        dataSet.setDrawValues(false)

                        val lineData = LineData(dataSet)
                        dvrTempChart.data = lineData

                        // Use time labels on the X axis
                        dvrTempChart.xAxis.valueFormatter = IndexAxisValueFormatter(timeLabels)
                        dvrTempChart.xAxis.labelCount = timeLabels.size.coerceAtMost(6)

                        dvrTempChart.invalidate()

                        // Update current DVR status simple text
                        // Use a localized string resource; format temperature with one decimal
                        val tempStr = String.format(Locale.getDefault(), "%.1f", latestTempForStatus)
                        currentDvrStatus.text = getString(R.string.dvr_running_normal, tempStr)
                     }
                 } else {
                    // No readings found - show raw CSV card so user can see data/source
                    withContext(Dispatchers.Main) {
                        dvrRawCsvCard.visibility = android.view.View.VISIBLE
                        dvrRawCsvPreview.text = getString(R.string.dvr_no_dvr_data)
                    }
                }
            } catch (e: Exception) {
                Log.e("DvrGraphsActivity", "Error loading DVR data", e)
                withContext(Dispatchers.Main) {
                    dvrRawCsvCard.visibility = android.view.View.VISIBLE
                    dvrRawCsvPreview.text = e.message ?: e.toString()
                }
            }
        }
    }
}
