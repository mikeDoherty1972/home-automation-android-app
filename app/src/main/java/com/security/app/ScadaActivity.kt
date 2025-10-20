package com.security.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.util.Timer
import java.util.TimerTask
import android.graphics.Color

class ScadaActivity : AppCompatActivity() {

    private lateinit var ampsTextView: TextView
    private lateinit var kwTextView: TextView
    private lateinit var geyserTempTextView: TextView
    private lateinit var geyserPressureTextView: TextView
    private lateinit var dvrTempTextView: TextView
    private lateinit var indoorTempTextView: TextView
    private lateinit var outdoorTempTextView: TextView
    private lateinit var outdoorHumidityTextView: TextView
    private lateinit var windSpeedTextView: TextView
    private lateinit var windDirectionTextView: TextView

    // Trend arrow TextViews for geyser
    private lateinit var waterTempTrendTextView: TextView
    private lateinit var waterPressureTrendTextView: TextView
    // Previous values for simple delta-based trend
    private var prevWaterTemp: Double? = null
    private var prevWaterPressure: Double? = null

    // Firestore reference for security sensors
    private lateinit var db: FirebaseFirestore

    // Google Sheets reader for geyser data
    private lateinit var sheetsReader: GoogleSheetsReader

    // Analytics variables
    private lateinit var totalPointsValue: TextView
    private lateinit var dataRateValue: TextView
    private lateinit var activeSensorsValue: TextView
    private lateinit var tempDataCount: TextView
    private lateinit var humidityDataCount: TextView
    private lateinit var windDataCount: TextView
    private lateinit var powerDataCount: TextView
    private lateinit var dvrDataCount: TextView
    private lateinit var dataUsageLastUpdate: TextView
    private lateinit var dailyPowerTextView: TextView
    private var lastUpdateTime: Long = 0L
    private var totalDataPoints: Int = 0
    private var dataRate: Double = 0.0
    private var activeSensors: Int = 0

    // Data class for Geyser readings
    data class GeyserReading(
        val waterTemp: Double = 0.0,
        val waterPressure: Double = 0.0,
        val dvrTemp: Double = 0.0,
        val currentAmps: Double = 0.0,
        val currentPower: Double = 0.0,
        val dailyPower: Double = 0.0,
        val indoorTemp: Double = 0.0,
        val outdoorTemp: Double = 0.0,
        val humidity: Double = 0.0,
        val windSpeed: Double = 0.0,
        val windDirection: Float = 0f
    )

    // --- Lights Control Fields ---
    private lateinit var lightsStatus: TextView
    private lateinit var lightsDetails: TextView

    // Google Drive API
    private lateinit var googleSignInClient: GoogleSignInClient
    private var driveService: Drive? = null
    private val RC_SIGN_IN = 4001
    // Google Drive file ID for messages_received.txt
    private val DRIVE_FILE_ID = "1rIlYuXnuITRT2Thm5o4yDBmCXktx05j6"
    private var lightsPollTimer: Timer? = null
    private val LIGHTS_TXT_FILE_ID = "1eEXzhHy_9ZQGWsnC-0wxge2PiJacho0i"

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("ScadaActivity", "onCreate called")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scada)

        // Initialize views with amps prominent, kW smaller as requested
        ampsTextView = findViewById(R.id.currentAmps)
        kwTextView = findViewById(R.id.currentPower)
        geyserTempTextView = findViewById(R.id.waterTempValue)
        geyserPressureTextView = findViewById(R.id.waterPressureValue)
        dvrTempTextView = findViewById(R.id.dvrTemp)
        indoorTempTextView = findViewById(R.id.indoorTemp)
        outdoorTempTextView = findViewById(R.id.outdoorTemp)
        outdoorHumidityTextView = findViewById(R.id.outdoorHumidity)
        windSpeedTextView = findViewById(R.id.windSpeed)
        windDirectionTextView = findViewById(R.id.windDirectionArrow)

        // Bind trend arrow TextViews
        waterTempTrendTextView = findViewById(R.id.waterTempTrend)
        waterPressureTrendTextView = findViewById(R.id.waterPressureTrend)

        // Initialize Firestore for security sensors
        db = FirebaseFirestore.getInstance()

        // Initialize Google Sheets reader for geyser data
        sheetsReader = GoogleSheetsReader()

        // Set initial values to show amps prominent, kW smaller layout
        ampsTextView.text = "-- A"  // Amps prominently displayed
        kwTextView.text = "-- kW"  // kW smaller on side
        geyserTempTextView.text = "Loading Sheets..."
        geyserPressureTextView.text = "Loading Sheets..."
        dvrTempTextView.text = "DVR: --°C"
        indoorTempTextView.text = "--°C"
        outdoorTempTextView.text = "--°C"
        outdoorHumidityTextView.text = "--%"
        windSpeedTextView.text = "-- km/h"
        windDirectionTextView.text = "↑"
        // Initialize trend arrows to neutral
        waterTempTrendTextView.text = "→"
        waterTempTrendTextView.setTextColor(Color.parseColor("#9FA8DA"))
        waterPressureTrendTextView.text = "→"
        waterPressureTrendTextView.setTextColor(Color.parseColor("#9FA8DA"))

        // Initialize analytics TextViews
        totalPointsValue = findViewById(R.id.totalPointsValue)
        dataRateValue = findViewById(R.id.dataRateValue)
        activeSensorsValue = findViewById(R.id.activeSensorsValue)
        tempDataCount = findViewById(R.id.tempDataCount)
        humidityDataCount = findViewById(R.id.humidityDataCount)
        windDataCount = findViewById(R.id.windDataCount)
        powerDataCount = findViewById(R.id.powerDataCount)
        dvrDataCount = findViewById(R.id.dvrDataCount)
        dataUsageLastUpdate = findViewById(R.id.dataUsageLastUpdate)
        // Bind SCADA daily power card so it displays the parsed daily total (column J)
        dailyPowerTextView = findViewById(R.id.dailyPower)
        dailyPowerTextView.text = "-- kWh"

        // Lights control views
        lightsStatus = findViewById(R.id.lightsStatus)
        lightsDetails = findViewById(R.id.lightsDetails)
        // Setup lights control buttons
        val lightsOnButton = findViewById<android.widget.Button>(R.id.lightsOnButton)
        val lightsOffButton = findViewById<android.widget.Button>(R.id.lightsOffButton)
        lightsOnButton.isEnabled = false
        lightsOffButton.isEnabled = false
        Log.d("ScadaActivity", "Lights buttons disabled at startup")
        lightsOnButton.setOnClickListener {
            Log.d("ScadaActivity", "[DEBUG] Lights ON button clicked")
            val drive = driveService
            val account = GoogleSignIn.getLastSignedInAccount(this)
            Log.d("ScadaActivity", "[DEBUG] Using Google account: \\${account?.email}")
            if (drive == null) {
                Log.w("ScadaActivity", "[DEBUG] Drive service not initialized for lights on")
                android.widget.Toast.makeText(this, "[DEBUG] Drive not initialized", android.widget.Toast.LENGTH_LONG).show()
            } else {
                CoroutineScope(Dispatchers.IO).launch {
                    val fileId = "1OvBr8BHa_v-H_W43utp7UyuY1uUeX_A2"
                    val fileExists = checkDriveFileExistsWithDebug(drive, fileId)
                    withContext(Dispatchers.Main) {
                        if (!fileExists) {
                            android.widget.Toast.makeText(this@ScadaActivity, "[DEBUG] Drive file not found or no access. Check sharing settings.", android.widget.Toast.LENGTH_LONG).show()
                            return@withContext
                        }
                    }
                    try {
                        Log.d("ScadaActivity", "[DEBUG] Attempting to write 'Lights on' to file: $fileId")
                        updateDriveFile(drive, fileId, "Lights on")
                        withContext(Dispatchers.Main) {
                            Log.d("ScadaActivity", "[DEBUG] 'Lights on' written to Drive file successfully")
                            android.widget.Toast.makeText(this@ScadaActivity, "[DEBUG] 'Lights on' sent to Drive!", android.widget.Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        Log.e("ScadaActivity", "[DEBUG] Error writing 'Lights on' to Drive", e)
                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(this@ScadaActivity, "[DEBUG] Drive write error: \\\${e.message}", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
        lightsOffButton.setOnClickListener {
            Log.d("ScadaActivity", "[DEBUG] Lights OFF button clicked")
            val drive = driveService
            val account = GoogleSignIn.getLastSignedInAccount(this)
            Log.d("ScadaActivity", "[DEBUG] Using Google account: \\${account?.email}")
            if (drive == null) {
                Log.w("ScadaActivity", "[DEBUG] Drive service not initialized for lights off")
                android.widget.Toast.makeText(this, "[DEBUG] Drive not initialized", android.widget.Toast.LENGTH_LONG).show()
            } else {
                CoroutineScope(Dispatchers.IO).launch {
                    val fileId = "1OvBr8BHa_v-H_W43utp7UyuY1uUeX_A2"
                    val fileExists = checkDriveFileExistsWithDebug(drive, fileId)
                    withContext(Dispatchers.Main) {
                        if (!fileExists) {
                            android.widget.Toast.makeText(this@ScadaActivity, "[DEBUG] Drive file not found or no access. Check sharing settings.", android.widget.Toast.LENGTH_LONG).show()
                            return@withContext
                        }
                    }
                    try {
                        Log.d("ScadaActivity", "[DEBUG] Attempting to write 'Lights off' to file: $fileId")
                        updateDriveFile(drive, fileId, "Lights off")
                        withContext(Dispatchers.Main) {
                            Log.d("ScadaActivity", "[DEBUG] 'Lights off' written to Drive file successfully")
                            android.widget.Toast.makeText(this@ScadaActivity, "[DEBUG] 'Lights off' sent to Drive!", android.widget.Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        Log.e("ScadaActivity", "[DEBUG] Error writing 'Lights off' to Drive", e)
                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(this@ScadaActivity, "[DEBUG] Drive write error: \\\${e.message}", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }

        // Setup click listeners for graphs
        setupGraphClickListeners()

        // Firebase monitoring removed - all data now from Google Sheets

        // Start Google Sheets monitoring for geyser data
        monitorGeyserData()

        // Setup Google Drive sign-in
        setupGoogleDriveSignIn()
        // Start polling for Y21_read_status from Drive file
        startY21ReadPolling()


        // Google Sign-In button
        val signInButton = findViewById<com.google.android.gms.common.SignInButton>(R.id.googleSignInButton)
        signInButton.setOnClickListener {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestScopes(Scope(DriveScopes.DRIVE)) // Changed from DRIVE_FILE to DRIVE
                .build()
            googleSignInClient = GoogleSignIn.getClient(this, gso)
            startActivityForResult(googleSignInClient.signInIntent, RC_SIGN_IN)
        }
    }

    private fun setupGraphClickListeners() {
        // Weather section click - show weather graphs
        findViewById<android.widget.LinearLayout>(R.id.weatherSection).setOnClickListener {
            val intent = Intent(this, WeatherGraphsActivity::class.java)
            startActivity(intent)
        }

        // Power section click - show power graphs
        findViewById<androidx.cardview.widget.CardView>(R.id.powerSection).setOnClickListener {
            val intent = Intent(this, PowerGraphsActivity::class.java)
            startActivity(intent)
        }

        // Geyser section click - show geyser graphs
        findViewById<androidx.cardview.widget.CardView>(R.id.geyserSection).setOnClickListener {
            val intent = Intent(this, GeyserGraphsActivity::class.java)
            startActivity(intent)
        }

        // DVR section click - show DVR graphs
        findViewById<androidx.cardview.widget.CardView>(R.id.dvrSection).setOnClickListener {
            val intent = Intent(this, DvrGraphsActivity::class.java)
            startActivity(intent)
        }

        // Back button functionality
        findViewById<android.widget.TextView>(R.id.scadaBackButton).setOnClickListener {
            finish()
        }
    }

    // Firebase monitoring removed - all data now comes from Google Sheets

    private fun updateAnalytics(latestReading: GeyserReading) {
        // Count data points (all fields are always present)
        val points = 10 // number of fields in GeyserReading
        val currentTime = System.currentTimeMillis()
        if (lastUpdateTime != 0L) {
            val timeDiffMin = (currentTime - lastUpdateTime) / 60000.0
            if (timeDiffMin > 0) {
                dataRate = points / timeDiffMin
            }
        }
        lastUpdateTime = currentTime
        totalDataPoints += points
        activeSensors = points
        // Update UI
        totalPointsValue.text = totalDataPoints.toString()
        dataRateValue.text = String.format("%.1f/min", dataRate)
        activeSensorsValue.text = activeSensors.toString()
        // Data Source Breakdown
        tempDataCount.text = if (latestReading.waterTemp != 0.0) "1 point" else "0 points"
        humidityDataCount.text = if (latestReading.humidity != 0.0) "1 point" else "0 points"
        windDataCount.text = if (latestReading.windSpeed != 0.0) "1 point" else "0 points"
        powerDataCount.text = if (latestReading.currentPower != 0.0) "1 point" else "0 points"
        dvrDataCount.text = if (latestReading.dvrTemp != 0.0) "1 point" else "0 points"
        // Last Updated
        val cal = java.util.Calendar.getInstance()
        val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val min = cal.get(java.util.Calendar.MINUTE)
        dataUsageLastUpdate.text = String.format("Last Updated: %02d:%02d", hour, min)
    }

    private fun monitorGeyserData() {
        // Launch coroutine to fetch geyser data from Google Sheets
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Fetch latest readings from Google Sheets (real geyser data)
                val sensorReadings = withContext(Dispatchers.IO) {
                    sheetsReader.fetchLatestReadings(2) // Get the two most recent readings so we can compute an initial trend immediately
                }

                if (sensorReadings.isNotEmpty()) {
                    // If we have two rows, set previous values from the older row (index 1) so the trend arrow can be computed immediately
                    if (sensorReadings.size >= 2) {
                        val older = sensorReadings[1]
                        prevWaterTemp = older.waterTemp.toDouble()
                        prevWaterPressure = older.waterPressure.toDouble()
                    }
                    // Convert SensorReading to GeyserReading
                    val s = sensorReadings[0] // most recent row
                    val latestReading = GeyserReading(
                        waterTemp = s.waterTemp.toDouble(),
                        waterPressure = s.waterPressure.toDouble(),
                        dvrTemp = s.dvrTemp.toDouble(),
                        currentAmps = s.currentAmps.toDouble(),
                        currentPower = s.currentPower.toDouble(),
                        dailyPower = s.dailyPower.toDouble(),
                        indoorTemp = s.indoorTemp.toDouble(),
                        outdoorTemp = s.outdoorTemp.toDouble(),
                        humidity = s.humidity.toDouble(),
                        windSpeed = s.windSpeed.toDouble(),
                        windDirection = s.windDirection
                    )
                    // Update UI with REAL data from Google Sheets (LAST ROW)
                    geyserTempTextView.text = String.format("%.1f°C", latestReading.waterTemp)
                    geyserPressureTextView.text = String.format("%.1f bar", latestReading.waterPressure)
                    // Show daily power (kWh) on the SCADA card
                    dailyPowerTextView.text = String.format("%.1f kWh", latestReading.dailyPower)
                    dvrTempTextView.text = String.format("%.1f°C", latestReading.dvrTemp)
                    ampsTextView.text = String.format("%.1f A", latestReading.currentAmps)
                    kwTextView.text = String.format("%.2f kW", latestReading.currentPower)
                    indoorTempTextView.text = String.format("%.1f°C", latestReading.indoorTemp)
                    outdoorTempTextView.text = String.format("%.1f°C", latestReading.outdoorTemp)
                    outdoorHumidityTextView.text = String.format("%.0f%%", latestReading.humidity)
                    windSpeedTextView.text = String.format("%.1f km/h", latestReading.windSpeed)
                    windDirectionTextView.text = getWindDirectionArrow(latestReading.windDirection)

                    // Simple delta-based trend for geyser temp + pressure
                    // small deadband to avoid flicker
                    val tempThreshold = 0.2
                    val pressureThreshold = 0.05

                    // Temperature trend
                    prevWaterTemp?.let { prev ->
                        when {
                            latestReading.waterTemp > prev + tempThreshold -> {
                                waterTempTrendTextView.text = "↑"
                                waterTempTrendTextView.setTextColor(Color.parseColor("#4CAF50")) // green
                            }
                            latestReading.waterTemp < prev - tempThreshold -> {
                                waterTempTrendTextView.text = "↓"
                                waterTempTrendTextView.setTextColor(Color.parseColor("#F44336")) // red
                            }
                            else -> {
                                waterTempTrendTextView.text = "→"
                                waterTempTrendTextView.setTextColor(Color.parseColor("#9FA8DA")) // neutral
                            }
                        }
                    }

                    // Pressure trend
                    prevWaterPressure?.let { prevP ->
                        when {
                            latestReading.waterPressure > prevP + pressureThreshold -> {
                                waterPressureTrendTextView.text = "↑"
                                waterPressureTrendTextView.setTextColor(Color.parseColor("#4CAF50"))
                            }
                            latestReading.waterPressure < prevP - pressureThreshold -> {
                                waterPressureTrendTextView.text = "↓"
                                waterPressureTrendTextView.setTextColor(Color.parseColor("#F44336"))
                            }
                            else -> {
                                waterPressureTrendTextView.text = "→"
                                waterPressureTrendTextView.setTextColor(Color.parseColor("#9FA8DA"))
                            }
                        }
                    }

                    // Update previous values after computing trend
                    prevWaterTemp = latestReading.waterTemp
                    prevWaterPressure = latestReading.waterPressure

                    // Update analytics with the latest reading
                    updateAnalytics(latestReading)
                } else {
                    geyserTempTextView.text = "No Sheets data"
                    geyserPressureTextView.text = "No Sheets data"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // Show error state
                runOnUiThread {
                    geyserTempTextView.text = "Error"
                    geyserPressureTextView.text = "Error"
                }
            }
            // Schedule next update in 30 seconds for fresh geyser data
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                monitorGeyserData()
            }, 30000) // Update every 30 seconds
        }
    }

    // Convert wind direction degrees to directional arrow
    private fun getWindDirectionArrow(degrees: Float): String {
        return when (degrees.toInt()) {
            in 0..22 -> "↑"      // North
            in 23..67 -> "↗"     // Northeast
            in 68..112 -> "→"    // East
            in 113..157 -> "↘"   // Southeast
            in 158..202 -> "↓"   // South
            in 203..247 -> "↙"   // Southwest
            in 248..292 -> "←"   // West
            in 293..337 -> "↖"   // Northwest
            in 338..359 -> "↑"   // North
            else -> "?"          // Invalid/unknown
        }
    }

    private fun ensureFreshGoogleSignIn() {
        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (account == null || account.idToken.isNullOrEmpty()) {
            // No account or token, start sign-in
            startActivityForResult(googleSignInClient.signInIntent, RC_SIGN_IN)
        } else {
            // Token may be stale, force a refresh
            googleSignInClient.signOut().addOnCompleteListener {
                startActivityForResult(googleSignInClient.signInIntent, RC_SIGN_IN)
            }
        }
    }

    private fun setupGoogleDriveSignIn() {
        Log.d("ScadaActivity", "setupGoogleDriveSignIn called")
        auth = FirebaseAuth.getInstance()
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE)) // Changed from DRIVE_FILE to DRIVE
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)
        ensureFreshGoogleSignIn()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.d("ScadaActivity", "onActivityResult called: requestCode=$requestCode, resultCode=$resultCode")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            if (resultCode == Activity.RESULT_OK) {
                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                if (task.isSuccessful) {
                    Log.d("ScadaActivity", "Google Sign-In successful, authenticating with Firebase")
                    val account = task.result
                    if (account?.idToken.isNullOrEmpty()) {
                        Log.w("ScadaActivity", "Google Sign-In account idToken is null or empty. Cannot authenticate.")
                        runOnUiThread {
                            android.widget.Toast.makeText(this, "Google Sign-In failed: Missing idToken. Please try again.", android.widget.Toast.LENGTH_LONG).show()
                        }
                        // Optionally, retry sign-in
                        startActivityForResult(googleSignInClient.signInIntent, RC_SIGN_IN)
                        return
                    }
                    firebaseAuthWithGoogle(account!!)
                    initializeDriveService(account)
                } else {
                    Log.w("ScadaActivity", "Google Sign-In failed: ", task.exception)
                    runOnUiThread {
                        android.widget.Toast.makeText(this, "Google Sign-In failed: ${task.exception?.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                Log.w("ScadaActivity", "Google Sign-In canceled or failed, resultCode=$resultCode")
                runOnUiThread {
                    android.widget.Toast.makeText(this, "Google Sign-In canceled or failed. Cannot control lights.", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun firebaseAuthWithGoogle(account: GoogleSignInAccount) {
        Log.d("ScadaActivity", "firebaseAuthWithGoogle called with account: ${account.email}")
        if (account.idToken.isNullOrEmpty()) {
            Log.e("ScadaActivity", "idToken is null or empty. Cannot authenticate with Firebase.")
            runOnUiThread {
                android.widget.Toast.makeText(this, "Google Sign-In failed: Missing idToken. Please try again.", android.widget.Toast.LENGTH_LONG).show()
            }
            // Optionally, retry sign-in
            startActivityForResult(googleSignInClient.signInIntent, RC_SIGN_IN)
            return
        }
        val credential = GoogleAuthProvider.getCredential(account.idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d("ScadaActivity", "Firebase authentication successful")
                    // Now that we are authenticated, we can start listening for Firestore updates
                    //listenForLightsStatus()
                } else {
                    Log.w("ScadaActivity", "Firebase authentication failed", task.exception)
                    runOnUiThread {
                        android.widget.Toast.makeText(this, "Firebase authentication failed.", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }
    }

    private fun initializeDriveService(account: GoogleSignInAccount?) {
        Log.d("ScadaActivity", "initializeDriveService called with account: $account")
        if (account == null) return
        Log.i("ScadaActivity", "Signed-in Google account: ${account.email}")
        val credential = GoogleAccountCredential.usingOAuth2(
            this, listOf(DriveScopes.DRIVE) // Changed from DRIVE_FILE to DRIVE
        )
        credential.selectedAccount = account.account
        driveService = Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("Home Automation").build()
        // Enable lights buttons now that Drive is ready
        runOnUiThread {
            findViewById<android.widget.Button>(R.id.lightsOnButton).isEnabled = true
            findViewById<android.widget.Button>(R.id.lightsOffButton).isEnabled = true
            Log.d("ScadaActivity", "Lights buttons enabled after Drive initialization")
        }
        // List all accessible files for debugging
        CoroutineScope(Dispatchers.IO).launch {
            listAllDriveFilesForDebug()
        }
        // Start polling the lights_on.txt file for status updates
        startPollingLightsFile()
    }

    // List all accessible files and log their names and IDs
    private suspend fun listAllDriveFilesForDebug() = withContext(Dispatchers.IO) {
        val drive = driveService ?: return@withContext
        try {
            val result = drive.files().list().setPageSize(100).setFields("files(id, name)").execute()
            val files = result.files
            if (files == null || files.isEmpty()) {
                Log.d("ScadaActivity", "[DEBUG] No files found in Drive.")
            } else {
                Log.d("ScadaActivity", "[DEBUG] Files accessible to app:")
                for (file in files) {
                    Log.d("ScadaActivity", "[DEBUG] File: ${file.name} (ID: ${file.id})")
                }
            }
        } catch (e: Exception) {
            Log.e("ScadaActivity", "[DEBUG] Error listing Drive files", e)
        }
    }

    private fun startPollingLightsFile() {
        lightsPollTimer?.cancel()
        lightsPollTimer = Timer()
        lightsPollTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                readLightsFileFromDrive()
            }
        }, 0, 30_000)
    }

    private fun readLightsFileFromDrive() {
        val drive = driveService ?: return
        Thread {
            try {
                val inputStream = drive.files().get(LIGHTS_TXT_FILE_ID).executeMediaAsInputStream()
                val content = inputStream.bufferedReader().use { it.readText().trim() }
                Log.d("ScadaActivity", "[DEBUG] Read lights_on.txt value: '$content'")
                runOnUiThread {
                    when (content) {
                        "1" -> {
                            lightsStatus.text = "\uD83D\uDCA1 ON"
                            lightsStatus.setTextColor(getColor(android.R.color.holo_green_light))
                            lightsDetails.text = "Google Drive: 1 (ON)"
                        }
                        "0" -> {
                            lightsStatus.text = "\uD83D\uDCA1 OFF"
                            lightsStatus.setTextColor(getColor(android.R.color.holo_red_light))
                            lightsDetails.text = "Google Drive: 0 (OFF)"
                        }
                        else -> {
                            lightsStatus.text = "\uD83D\uDCA1 Unknown"
                            lightsDetails.text = "Google Drive: '$content'"
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    lightsStatus.text = "\uD83D\uDCA1 Error"
                    lightsDetails.text = "Drive read error"
                }
                Log.e("ScadaActivity", "Drive read error", e)
            }
        }.start()
    }

    override fun onDestroy() {
        lightsPollTimer?.cancel()
        super.onDestroy()
    }

    // Update a file in Google Drive by file ID
    private suspend fun updateDriveFile(drive: Drive, fileId: String, content: String) = withContext(Dispatchers.IO) {
        val contentStream = java.io.ByteArrayInputStream(content.toByteArray())
        val fileMetadata = com.google.api.services.drive.model.File()
        drive.files().update(fileId, fileMetadata, com.google.api.client.http.InputStreamContent("text/plain", contentStream)).execute()
    }

    // Deletes a file from Google Drive by file ID
    private fun deleteDriveFile(drive: Drive, fileId: String) {
        try {
            drive.files().delete(fileId).execute()
            Log.d("ScadaActivity", "[DEBUG] Deleted Drive file: $fileId")
        } catch (e: Exception) {
            Log.e("ScadaActivity", "[DEBUG] Error deleting Drive file: $fileId", e)
        }
    }

    // Creates a new file in Google Drive with the given name and content
    private fun createDriveFile(drive: Drive, fileName: String, content: String, parentId: String? = null): String? {
        return try {
            val fileMetadata = File().apply {
                name = fileName
                parentId?.let { parents = listOf(it) }
            }
            val contentStream = ByteArrayContent.fromString("text/plain", content)
            val file = drive.files().create(fileMetadata, contentStream)
                .setFields("id")
                .execute()
            Log.d("ScadaActivity", "[DEBUG] Created new Drive file: ${file.id}")
            file.id
        } catch (e: Exception) {
            Log.e("ScadaActivity", "[DEBUG] Error creating Drive file", e)
            null
        }
    }

    // Reads the content of a Google Drive file by file ID
    private suspend fun readDriveFileContent(drive: Drive, fileId: String): String? = withContext(Dispatchers.IO) {
        return@withContext try {
            val inputStream = drive.files().get(fileId).executeMediaAsInputStream()
            inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.e("ScadaActivity", "[DEBUG] Error reading Drive file: $fileId", e)
            null
        }
    }

    // Polls the Drive file every 30 seconds and updates Firestore Y21_read_status
    private fun startY21ReadPolling() {
        val drive = driveService ?: return
        val firestoreDoc = db.collection("scada_controls").document("lights_status")
        val fileId = "1eEXzhHy_9ZQGWsnC-0wxge2PiJacho0i"
        CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                val content = readDriveFileContent(drive, fileId)?.trim() ?: "N/A"
                try {
                    firestoreDoc.update("Y21_read_status", content)
                    Log.d("ScadaActivity", "[DEBUG] Updated Firestore Y21_read_status to: $content")
                } catch (e: Exception) {
                    Log.e("ScadaActivity", "[DEBUG] Error updating Firestore Y21_read_status", e)
                }
                delay(30000) // 30 seconds
            }
        }
    }

    // Check if the Drive file is accessible before writing, with detailed logging
    private suspend fun checkDriveFileExistsWithDebug(drive: Drive, fileId: String): Boolean = withContext(Dispatchers.IO) {
        Log.d("ScadaActivity", "[DEBUG] Attempting to access Drive file with ID: $fileId")
        try {
            val file = drive.files().get(fileId).execute()
            Log.d("ScadaActivity", "[DEBUG] File found: name=${file.name}, id=${file.id}, mimeType=${file.mimeType}")
            withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(this@ScadaActivity, "[DEBUG] File found: ${file.name}", android.widget.Toast.LENGTH_LONG).show()
            }
            true
        } catch (e: Exception) {
            Log.e("ScadaActivity", "[DEBUG] Drive file not found or inaccessible: $fileId", e)
            withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(this@ScadaActivity, "[DEBUG] File not found or inaccessible: $fileId\n${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
            false
        }
    }
}
