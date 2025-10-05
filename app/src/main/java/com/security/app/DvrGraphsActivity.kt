package com.security.app

import android.graphics.Color
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
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
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dvr_graphs)
        
        // Initialize views
        dvrTempChart = findViewById(R.id.dvrTempChart)
        currentDvrStatus = findViewById(R.id.currentDvrStatus)
        dateTitle = findViewById(R.id.dateTitle)
        
        // Setup return button
        findViewById<TextView>(R.id.returnButton).setOnClickListener {
            finish()
        }
        
        // Set today's date
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        dateTitle.text = "📅 DVR Temperature Monitoring - $today"
        
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
                
                // Get recent readings (last 24 hours)
                val readings = sheetsReader.fetchRecentReadings(24)
                
                if (readings.isNotEmpty()) {
                    val tempEntries = mutableListOf<Entry>()
                    val timeLabels = mutableListOf<String>()
                    var latestTemp = 0.0f
                    
                    readings.forEachIndexed { i, reading ->
                        if (i == readings.size - 1) {
                            latestTemp = reading.dvrTemp
                        }
                        
                        val timeLabel = try {
                            val timePart = reading.timestamp.split(" ").getOrNull(1)
                            if (timePart != null && timePart.length >= 5) {
                                timePart.substring(0, 5) // HH:MM
                            } else {
                                "${i * 5}min"
                            }
                        } catch (e: Exception) {
                            "${i * 5}min"
                        }
                        
                        tempEntries.add(Entry(i.toFloat(), reading.dvrTemp))
                        timeLabels.add(timeLabel)
                    }
                    
                    withContext(Dispatchers.Main) {
                        updateDvrChart(tempEntries, timeLabels)
                        updateStatus(latestTemp.toDouble())
                        dateTitle.text = "📅 DVR Temperature - ${readings.size} readings from Google Sheets"
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        showEmptyChart()
                        dateTitle.text = "📅 DVR Temperature - No data available"
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    showEmptyChart()
                    dateTitle.text = "📅 DVR Temperature - Error loading data"
                }
            }
        }
    }
    
    private fun updateDvrChart(tempEntries: List<Entry>, timeLabels: List<String>) {
        val tempDataSet = LineDataSet(tempEntries, "DVR Temperature °C")
        tempDataSet.color = Color.CYAN
        tempDataSet.setCircleColor(Color.CYAN)
        tempDataSet.lineWidth = 3f
        tempDataSet.circleRadius = 4f
        tempDataSet.setDrawCircleHole(false)
        tempDataSet.valueTextSize = 8f
        tempDataSet.valueTextColor = Color.WHITE
        
        dvrTempChart.data = LineData(tempDataSet)
        dvrTempChart.xAxis.valueFormatter = IndexAxisValueFormatter(timeLabels)
        dvrTempChart.invalidate()
    }
    
    private fun updateStatus(currentTemp: Double) {
        val statusText = when {
            currentTemp < 35.0 -> "🟢 DVR Running Normal - ${String.format("%.1f", currentTemp)}°C"
            currentTemp < 45.0 -> "🟡 DVR Running Warm - ${String.format("%.1f", currentTemp)}°C"
            else -> "🔴 DVR Running Hot - ${String.format("%.1f", currentTemp)}°C"
        }
        
        val statusColor = when {
            currentTemp < 35.0 -> Color.parseColor("#10B981")
            currentTemp < 45.0 -> Color.parseColor("#F59E0B")
            else -> Color.parseColor("#EF4444")
        }
        
        currentDvrStatus.text = statusText
        currentDvrStatus.setTextColor(statusColor)
    }
    
    private fun showEmptyChart() {
        val emptyEntries = listOf(Entry(0f, 0f))
        val emptyLabels = listOf("No Data")
        updateDvrChart(emptyEntries, emptyLabels)
        currentDvrStatus.text = "🔍 No DVR data available"
        currentDvrStatus.setTextColor(Color.GRAY)
    }
}