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
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*
import com.security.app.R

class MikeWaterGraphsActivity : AppCompatActivity() {

    private lateinit var mikeWaterChart: LineChart
    private lateinit var dateTitle: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mike_water_graphs)

        mikeWaterChart = findViewById(R.id.mikeWaterChart)
        dateTitle = findViewById(R.id.dateTitle)

        // Return button
        findViewById<TextView>(R.id.returnButton).setOnClickListener {
            finish()
        }

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        dateTitle.text = "📈 Mike's Water Meter - $today"

        setupChart()
        loadMikeWaterData()
    }

    private fun setupChart() {
        mikeWaterChart.description.isEnabled = false
        mikeWaterChart.setTouchEnabled(true)
        mikeWaterChart.isDragEnabled = true
        mikeWaterChart.setScaleEnabled(true)
        mikeWaterChart.setPinchZoom(true)
        mikeWaterChart.setBackgroundColor(Color.TRANSPARENT)

        val xAxis = mikeWaterChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(true)
        xAxis.textColor = Color.WHITE
        xAxis.gridColor = Color.GRAY
        xAxis.granularity = 60f
        xAxis.labelRotationAngle = -45f

        // Dynamic Y axis for Mike's Water Meter: remove min/max, enable auto-scale
        val leftAxis = mikeWaterChart.axisLeft
        mikeWaterChart.setAutoScaleMinMaxEnabled(true)
        leftAxis.removeAllLimitLines()
        leftAxis.resetAxisMinimum()
        leftAxis.resetAxisMaximum()
        // ...existing code for grid/label styling...
        leftAxis.setDrawGridLines(true)
        leftAxis.textColor = Color.WHITE
        leftAxis.gridColor = Color.GRAY

        val rightAxis = mikeWaterChart.axisRight
        rightAxis.isEnabled = false
    }

    private fun loadMikeWaterData() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val reader = IPERLGoogleSheetsReader()
                val readings = reader.fetchLatestWaterReadings(200)

                // Use the parsed 'date' field for filtering today's readings
                val calendar = Calendar.getInstance()
                calendar.time = Date()
                val todayYear = calendar.get(Calendar.YEAR)
                val todayMonth = calendar.get(Calendar.MONTH)
                val todayDay = calendar.get(Calendar.DAY_OF_MONTH)

                val todaysReadings = readings.filter { r ->
                    val cal = Calendar.getInstance()
                    cal.time = r.date
                    cal.get(Calendar.YEAR) == todayYear &&
                    cal.get(Calendar.MONTH) == todayMonth &&
                    cal.get(Calendar.DAY_OF_MONTH) == todayDay
                }

                android.util.Log.d("MikeWaterGraphs", "Today's readings count: ${todaysReadings.size}")
                todaysReadings.forEach { android.util.Log.d("MikeWaterGraphs", "TODAY: ${it.timestamp} Value: ${it.meterReading}") }

                if (todaysReadings.isNotEmpty()) {
                    val entries = mutableListOf<Entry>()
                    val labels = mutableListOf<Int>()

                    // Use the parsed date for minutes since midnight
                    val ordered = todaysReadings // already chronological
                    ordered.forEach { r ->
                        val minutes = r.date.let { d ->
                            val cal = Calendar.getInstance()
                            cal.time = d
                            cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
                        }
                        entries.add(Entry(minutes.toFloat(), r.meterReading))
                        labels.add(minutes)
                    }

                    entries.sortBy { it.x }

                    withContext(Dispatchers.Main) {
                        updateChart(entries, labels)
                        dateTitle.text = "\uD83D\uDCC8 Mike's Water Meter - Today's usage"
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        showEmpty()
                        dateTitle.text = "\uD83D\uDCC8 Mike's Water Meter - No data for today"
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    showEmpty()
                    dateTitle.text = "\uD83D\uDCC8 Mike's Water Meter - Error loading data"
                }
            }
        }
    }

    private fun updateChart(entries: List<Entry>, labels: List<Int>) {
        val ds = LineDataSet(entries, "Meter Reading")
        ds.color = Color.CYAN
        ds.setCircleColor(Color.CYAN)
        ds.lineWidth = 3f
        ds.circleRadius = 4f
        ds.setDrawCircleHole(false)
        ds.valueTextSize = 8f
        ds.valueTextColor = Color.WHITE

        mikeWaterChart.data = LineData(ds)

        // Dynamic Y axis for Mike's Water Meter: remove min/max, enable auto-scale
        val leftAxis = mikeWaterChart.axisLeft
        mikeWaterChart.setAutoScaleMinMaxEnabled(true)
        leftAxis.removeAllLimitLines()
        leftAxis.resetAxisMinimum()
        leftAxis.resetAxisMaximum()
        // ...existing code for grid/label styling...
        leftAxis.setDrawGridLines(true)
        leftAxis.textColor = Color.WHITE
        leftAxis.gridColor = Color.GRAY

        val timeFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val minutes = ((value.toInt() % (24 * 60)) + (24 * 60)) % (24 * 60)
                val h = minutes / 60
                val m = minutes % 60
                return String.format(Locale.getDefault(), "%02d:%02d", h, m)
            }
        }
        mikeWaterChart.xAxis.valueFormatter = timeFormatter
        mikeWaterChart.xAxis.axisMinimum = 0f
        mikeWaterChart.xAxis.axisMaximum = (24 * 60).toFloat()
        mikeWaterChart.data?.notifyDataChanged()
        mikeWaterChart.notifyDataSetChanged()
        mikeWaterChart.setVisibleXRangeMaximum((24 * 60).toFloat())
        mikeWaterChart.moveViewToX(0f)
        android.util.Log.d("MikeWaterGraphs", "Y axis set to dynamic (auto-scale)")
        mikeWaterChart.invalidate()
    }

    private fun showEmpty() {
        val emptyEntries = listOf(Entry(0f, 0f))
        val emptyLabels = listOf(0)
        updateChart(emptyEntries, emptyLabels)
    }
}
