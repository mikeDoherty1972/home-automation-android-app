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
        dateTitle.text = "📅 Power Consumption - $today"
        
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
                
                // Get recent readings (last 24 hours)
                val readings = sheetsReader.fetchRecentReadings(24)
                
                if (readings.isNotEmpty()) {
                    val powerEntries = mutableListOf<Entry>()
                    val ampsEntries = mutableListOf<Entry>()
                    val dailyEntries = mutableListOf<Entry>()
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
                        
                        powerEntries.add(Entry(i.toFloat(), reading.currentPower))
                        ampsEntries.add(Entry(i.toFloat(), reading.currentAmps))
                        dailyEntries.add(Entry(i.toFloat(), reading.dailyPower))
                        timeLabels.add(timeLabel)
                    }
                    
                    withContext(Dispatchers.Main) {
                        updatePowerCharts(powerEntries, ampsEntries, dailyEntries, timeLabels)
                        dateTitle.text = "📅 Power Consumption - ${readings.size} readings from Google Sheets"
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        showEmptyCharts()
                        dateTitle.text = "📅 Power Consumption - No data available"
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    showEmptyCharts()
                    dateTitle.text = "📅 Power Consumption - Error loading data"
                }
            }
        }
    }
    
    private fun updatePowerCharts(powerEntries: List<Entry>, ampsEntries: List<Entry>, dailyEntries: List<Entry>, timeLabels: List<String>) {
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
        currentPowerChart.xAxis.valueFormatter = IndexAxisValueFormatter(timeLabels)
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
        currentAmpsChart.xAxis.valueFormatter = IndexAxisValueFormatter(timeLabels)
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
        dailyTotalChart.xAxis.valueFormatter = IndexAxisValueFormatter(timeLabels)
        dailyTotalChart.invalidate()
    }
    
    private fun showEmptyCharts() {
        val emptyEntries = listOf(Entry(0f, 0f))
        val emptyLabels = listOf("No Data")
        updatePowerCharts(emptyEntries, emptyEntries, emptyEntries, emptyLabels)
    }
}