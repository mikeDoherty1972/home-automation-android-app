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

class GeyserGraphsActivity : AppCompatActivity() {
    
    private lateinit var geyserTempChart: LineChart
    private lateinit var pressureChart: LineChart
    private lateinit var dateTitle: TextView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_geyser_graphs)
        
        // Initialize views
        geyserTempChart = findViewById(R.id.geyserTempChart)
        pressureChart = findViewById(R.id.pressureChart)
        dateTitle = findViewById(R.id.dateTitle)
        
        // Setup return button
        findViewById<TextView>(R.id.returnButton).setOnClickListener {
            finish()
        }
        
        // Set today's date
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        dateTitle.text = "📅 Geyser Performance - $today"
        
        // Setup charts
        setupCharts()
        
        // Load geyser data from Google Sheets
        loadGeyserData()
    }
    
    private fun setupCharts() {
        listOf(geyserTempChart, pressureChart).forEach { chart ->
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
            
            // Y-axis setup
            val leftAxis = chart.axisLeft
            leftAxis.setDrawGridLines(true)
            leftAxis.textColor = Color.WHITE
            leftAxis.gridColor = Color.GRAY
            
            val rightAxis = chart.axisRight
            rightAxis.isEnabled = false
        }
    }
    
    private fun loadGeyserData() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val sheetsReader = GoogleSheetsReader()
                
                // Get recent readings (last 24 hours)
                val readings = sheetsReader.fetchRecentReadings(24)
                
                if (readings.isNotEmpty()) {
                    val tempEntries = mutableListOf<Entry>()
                    val pressureEntries = mutableListOf<Entry>()
                    val timeLabels = mutableListOf<String>()
                    
                    readings.forEachIndexed { i, reading ->
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
                        
                        tempEntries.add(Entry(i.toFloat(), reading.waterTemp))
                        pressureEntries.add(Entry(i.toFloat(), reading.waterPressure))
                        timeLabels.add(timeLabel)
                    }
                    
                    withContext(Dispatchers.Main) {
                        updateGeyserCharts(tempEntries, pressureEntries, timeLabels)
                        dateTitle.text = "📅 Geyser Performance - ${readings.size} readings from Google Sheets"
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        showEmptyCharts()
                        dateTitle.text = "📅 Geyser Performance - No data available"
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    showEmptyCharts()
                    dateTitle.text = "📅 Geyser Performance - Error loading data"
                }
            }
        }
    }
    
    private fun updateGeyserCharts(tempEntries: List<Entry>, pressureEntries: List<Entry>, timeLabels: List<String>) {
        // Temperature Chart
        val tempDataSet = LineDataSet(tempEntries, "Water Temperature °C")
        tempDataSet.color = Color.RED
        tempDataSet.setCircleColor(Color.RED)
        tempDataSet.lineWidth = 3f
        tempDataSet.circleRadius = 4f
        tempDataSet.setDrawCircleHole(false)
        tempDataSet.valueTextSize = 8f
        tempDataSet.valueTextColor = Color.WHITE
        
        geyserTempChart.data = LineData(tempDataSet)
        geyserTempChart.xAxis.valueFormatter = IndexAxisValueFormatter(timeLabels)
        geyserTempChart.invalidate()
        
        // Pressure Chart
        val pressureDataSet = LineDataSet(pressureEntries, "Water Pressure Bar")
        pressureDataSet.color = Color.BLUE
        pressureDataSet.setCircleColor(Color.BLUE)
        pressureDataSet.lineWidth = 3f
        pressureDataSet.circleRadius = 4f
        pressureDataSet.setDrawCircleHole(false)
        pressureDataSet.valueTextSize = 8f
        pressureDataSet.valueTextColor = Color.WHITE
        
        pressureChart.data = LineData(pressureDataSet)
        pressureChart.xAxis.valueFormatter = IndexAxisValueFormatter(timeLabels)
        pressureChart.invalidate()
    }
    
    private fun showEmptyCharts() {
        val emptyEntries = listOf(Entry(0f, 0f))
        val emptyLabels = listOf("No Data")
        updateGeyserCharts(emptyEntries, emptyEntries, emptyLabels)
    }
}