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

class WeatherGraphsActivity : AppCompatActivity() {
    
    private lateinit var temperatureChart: LineChart
    private lateinit var humidityChart: LineChart
    private lateinit var windChart: LineChart
    private lateinit var dateTitle: TextView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_weather_graphs)
        
        // Initialize views
        temperatureChart = findViewById(R.id.temperatureChart)
        humidityChart = findViewById(R.id.humidityChart)
        windChart = findViewById(R.id.windChart)
        dateTitle = findViewById(R.id.dateTitle)
        
        // Setup return button
        findViewById<TextView>(R.id.returnButton).setOnClickListener {
            finish()
        }
        
        // Set today's date
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        dateTitle.text = "📅 Weather Trends - $today"
        
        // Setup charts
        setupCharts()
        
        // Load weather data from Google Sheets
        loadWeatherData()
    }
    
    private fun setupCharts() {
        listOf(temperatureChart, humidityChart, windChart).forEach { chart ->
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
    
    private fun loadWeatherData() {
        // Use coroutine to load data from Google Sheets
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val sheetsReader = GoogleSheetsReader()
                
                // Get recent readings (last 24 hours for today's trends)
                val readings = sheetsReader.fetchRecentReadings(24)
                
                if (readings.isNotEmpty()) {
                    val indoorTempEntries = mutableListOf<Entry>()
                    val outdoorTempEntries = mutableListOf<Entry>()
                    val humidityEntries = mutableListOf<Entry>()
                    val windEntries = mutableListOf<Entry>()
                    val timeLabels = mutableListOf<String>()
                    
                    // Parse Google Sheets data
                    readings.forEachIndexed { i, reading ->
                        val timeLabel = try {
                            // Extract time from timestamp (assuming format: "yyyy-MM-dd HH:mm:ss")
                            val timePart = reading.timestamp.split(" ").getOrNull(1)
                            if (timePart != null && timePart.length >= 5) {
                                timePart.substring(0, 5) // HH:MM
                            } else {
                                "${i * 5}min" // Fallback
                            }
                        } catch (e: Exception) {
                            "${i * 5}min" // Fallback
                        }
                        
                        indoorTempEntries.add(Entry(i.toFloat(), reading.indoorTemp))
                        outdoorTempEntries.add(Entry(i.toFloat(), reading.outdoorTemp))
                        humidityEntries.add(Entry(i.toFloat(), reading.humidity))
                        windEntries.add(Entry(i.toFloat(), reading.windSpeed))
                        timeLabels.add(timeLabel)
                    }
                    
                    // Update UI on main thread
                    withContext(Dispatchers.Main) {
                        updateTemperatureChart(indoorTempEntries, outdoorTempEntries, timeLabels)
                        updateHumidityChart(humidityEntries, timeLabels)
                        updateWindChart(windEntries, timeLabels)
                        
                        // Update title with data count
                        dateTitle.text = "📅 Weather Trends - ${readings.size} readings from Google Sheets"
                    }
                } else {
                    // No data available, show empty charts
                    withContext(Dispatchers.Main) {
                        showEmptyCharts()
                        dateTitle.text = "📅 Weather Trends - No data available"
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    showEmptyCharts()
                    dateTitle.text = "📅 Weather Trends - Error loading data"
                }
            }
        }
    }
    
    private fun updateTemperatureChart(indoorEntries: List<Entry>, outdoorEntries: List<Entry>, timeLabels: List<String>) {
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
        temperatureChart.xAxis.valueFormatter = IndexAxisValueFormatter(timeLabels)
        temperatureChart.invalidate()
    }
    
    private fun updateHumidityChart(humidityEntries: List<Entry>, timeLabels: List<String>) {
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
        humidityChart.xAxis.valueFormatter = IndexAxisValueFormatter(timeLabels)
        humidityChart.invalidate()
    }
    
    private fun updateWindChart(windEntries: List<Entry>, timeLabels: List<String>) {
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
        windChart.xAxis.valueFormatter = IndexAxisValueFormatter(timeLabels)
        windChart.invalidate()
    }
    
    private fun showEmptyCharts() {
        // Show empty charts with placeholder data
        val emptyEntries = listOf(Entry(0f, 0f))
        val emptyLabels = listOf("No Data")
        
        updateTemperatureChart(emptyEntries, emptyEntries, emptyLabels)
        updateHumidityChart(emptyEntries, emptyLabels)
        updateWindChart(emptyEntries, emptyLabels)
    }
}