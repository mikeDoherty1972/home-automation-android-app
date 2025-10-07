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
import com.google.api.services.drive.model.FileList
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
import com.google.api.client.http.javanet.NetHttpTransport

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
    private val DRIVE_FILE_NAME = "messages_received.txt"

    // Cache for Drive folder IDs
    private var cachedFolderId: String? = null
    private val folderNames = listOf("python", "current", "sensors")
    
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
            Log.d("ScadaActivity", "Lights ON button clicked")
            sendLightsCommand("on")
        }
        lightsOffButton.setOnClickListener {
            Log.d("ScadaActivity", "Lights OFF button clicked")
            sendLightsCommand("off")
        }

        // Setup click listeners for graphs
        setupGraphClickListeners()
        
        // Firebase monitoring removed - all data now from Google Sheets
        
        // Start Google Sheets monitoring for geyser data
        monitorGeyserData()

        // Setup Google Drive sign-in
        setupGoogleDriveSignIn()

        // Test Drive Write Button
        findViewById<android.widget.Button>(R.id.testDriveWriteButton).setOnClickListener {
            Log.d("ScadaActivity", "Test Drive Write button clicked")
            testDriveWrite()
        }

        // Google Sign-In button
        val signInButton = findViewById<com.google.android.gms.common.SignInButton>(R.id.googleSignInButton)
        signInButton.setOnClickListener {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestScopes(Scope(DriveScopes.DRIVE_FILE))
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
                    sheetsReader.fetchLatestReadings(1) // Get just the latest reading
                }
                
                if (sensorReadings.isNotEmpty()) {
                    // Convert SensorReading to GeyserReading
                    val s = sensorReadings[0]
                    val latestReading = GeyserReading(
                        waterTemp = s.waterTemp.toDouble(),
                        waterPressure = s.waterPressure.toDouble(),
                        dvrTemp = s.dvrTemp.toDouble(),
                        currentAmps = s.currentAmps.toDouble(),
                        currentPower = s.currentPower.toDouble(),
                        indoorTemp = s.indoorTemp.toDouble(),
                        outdoorTemp = s.outdoorTemp.toDouble(),
                        humidity = s.humidity.toDouble(),
                        windSpeed = s.windSpeed.toDouble(),
                        windDirection = s.windDirection
                    )
                    // Update UI with REAL data from Google Sheets (LAST ROW)
                    geyserTempTextView.text = String.format("%.1f°C", latestReading.waterTemp)
                    geyserPressureTextView.text = String.format("%.1f bar", latestReading.waterPressure)
                    dvrTempTextView.text = String.format("%.1f°C", latestReading.dvrTemp)
                    ampsTextView.text = String.format("%.1f A", latestReading.currentAmps)
                    kwTextView.text = String.format("%.2f kW", latestReading.currentPower)
                    indoorTempTextView.text = String.format("%.1f°C", latestReading.indoorTemp)
                    outdoorTempTextView.text = String.format("%.1f°C", latestReading.outdoorTemp)
                    outdoorHumidityTextView.text = String.format("%.0f%%", latestReading.humidity)
                    windSpeedTextView.text = String.format("%.1f km/h", latestReading.windSpeed)
                    windDirectionTextView.text = getWindDirectionArrow(latestReading.windDirection)
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

    private fun sendLightsCommand(command: String) {
        Log.d("ScadaActivity", "sendLightsCommand called with command: $command")
        val drive = driveService ?: run {
            Log.w("ScadaActivity", "Drive service not initialized")
            runOnUiThread {
                android.widget.Toast.makeText(this, "Drive not initialized", android.widget.Toast.LENGTH_LONG).show()
            }
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("ScadaActivity", "Attempting to find or create Drive file for lights command: $command")
                val fileId = findOrCreateDriveFile(drive)
                Log.d("ScadaActivity", "Drive file ID for lights command: $fileId")
                when (command) {
                    "on" -> updateDriveFile(drive, fileId, "Lights on")
                    "off" -> updateDriveFile(drive, fileId, "")
                }
                withContext(Dispatchers.Main) {
                    Log.d("ScadaActivity", "Lights command sent to Drive: $command")
                    android.widget.Toast.makeText(this@ScadaActivity, "Lights command sent: $command", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: UserRecoverableAuthIOException) {
                withContext(Dispatchers.Main) {
                    Log.w("ScadaActivity", "UserRecoverableAuthIOException: ${e.message}")
                    startActivityForResult(e.intent, RC_SIGN_IN)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.w("ScadaActivity", "Error sending lights command to Drive", e)
                    android.widget.Toast.makeText(this@ScadaActivity, "Drive error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private suspend fun findOrCreateDriveFile(drive: Drive, fileName: String = DRIVE_FILE_NAME): String = withContext(Dispatchers.IO) {
        // Use cached folder ID if available
        var parentId = cachedFolderId
        if (parentId == null) {
            parentId = null
            for (folderName in folderNames) {
                // Search for all folders with the correct name and parent
                val folderResult = drive.files().list()
                    .setQ("mimeType = 'application/vnd.google-apps.folder' and name = '$folderName' and trashed = false and " +
                            (if (parentId != null) "'$parentId' in parents" else "'root' in parents"))
                    .setSpaces("drive")
                    .setFields("files(id, name, parents, createdTime)")
                    .execute()
                if (folderResult.files.size > 1) {
                    Log.w("ScadaActivity", "Duplicate folders found for '$folderName' under parent ${parentId ?: "root"}:")
                    folderResult.files.forEach { f ->
                        Log.w("ScadaActivity", "  id=${f.id}, createdTime=${f.createdTime}, parents=${f.parents}")
                    }
                }
                // Pick the oldest folder if duplicates exist, or the first if only one
                val folder = folderResult.files.minByOrNull { it.createdTime.value }
                parentId = if (folder != null) {
                    Log.d("ScadaActivity", "Using folder '$folderName' with id: ${folder.id}")
                    folder.id
                } else {
                    val metadata = File().apply {
                        name = folderName
                        mimeType = "application/vnd.google-apps.folder"
                        parents = listOf(parentId ?: "root")
                    }
                    val createdId = drive.files().create(metadata).setFields("id").execute().id
                    Log.d("ScadaActivity", "Created folder '$folderName' with id: $createdId")
                    createdId
                }
            }
            // Cache the final folder ID
            cachedFolderId = parentId
        }
        // Now parentId is the sensors folder ID
        val result: FileList = drive.files().list()
            .setQ("name = '$fileName' and trashed = false and '$parentId' in parents")
            .setSpaces("drive")
            .setFields("files(id, name)")
            .execute()
        val file = result.files.firstOrNull()
        return@withContext if (file != null) {
            Log.d("ScadaActivity", "Found file '$fileName' with id: ${file.id}")
            file.id
        } else {
            val metadata = File().apply {
                name = fileName
                mimeType = "text/plain"
                parents = listOf(parentId)
            }
            val created = drive.files().create(metadata)
                .setFields("id")
                .execute()
            Log.d("ScadaActivity", "Created file '$fileName' with id: ${created.id}")
            created.id
        }
    }

    private suspend fun updateDriveFile(drive: Drive, fileId: String, content: String) = withContext(Dispatchers.IO) {
        val contentStream = java.io.ByteArrayInputStream(content.toByteArray())
        val fileMetadata = File()
        drive.files().update(fileId, fileMetadata, com.google.api.client.http.InputStreamContent("text/plain", contentStream)).execute()
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
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
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
                    listenForLightsStatus()
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
            this, listOf(DriveScopes.DRIVE_FILE)
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
    }

    private fun listenForLightsStatus() {
        db.collection("scada_controls").document("lights_status")
            .addSnapshotListener { documentSnapshot: DocumentSnapshot?, e: FirebaseFirestoreException? ->
                if (e != null) {
                    android.util.Log.w("ScadaActivity", "Lights status listen failed.", e)
                    runOnUiThread {
                        android.widget.Toast.makeText(this, "Firestore listen error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                    }
                    return@addSnapshotListener
                }
                if (documentSnapshot != null && documentSnapshot.exists()) {
                    val data = documentSnapshot.data ?: return@addSnapshotListener
                    Log.d("ScadaActivity", "Firestore lights_status update: $data")
                    updateLightsDisplay(data)
                } else {
                    Log.d("ScadaActivity", "Firestore lights_status: No document or does not exist.")
                }
            }
    }
    private fun updateLightsDisplay(data: Map<String, Any>) {
        runOnUiThread {
            val lightsOn = data["lights_on"] as? Boolean ?: false
            val lastCommand = data["last_command"] as? String ?: "--"
            val m22Value = data["M22_write_sent"] as? Long ?: 0
            // Show Y21_read as "N/A" if missing, otherwise show value
            val y21Value = when (val y = data["Y21_read_status"]) {
                is Number -> y.toLong()
                is String -> y.toLongOrNull() ?: 0L
                else -> null
            }
            val y21Display = y21Value?.toString() ?: "N/A"
            val commandSuccess = data["command_success"] as? Boolean ?: false
            // Update status display
            if (lightsOn) {
                lightsStatus.text = "💡 ON"
                lightsStatus.setTextColor(getColor(android.R.color.holo_green_light))
            } else {
                lightsStatus.text = "💡 OFF"
                lightsStatus.setTextColor(getColor(android.R.color.holo_red_light))
            }
            // Update technical details
            val successIcon = if (commandSuccess) "✅" else "❌"
            lightsDetails.text = "Sent TXT: $m22Value | Y21_read: $y21Display | $successIcon Last: $lastCommand"
        }
    }

    private fun testDriveWrite() {
        val drive = driveService ?: run {
            Log.w("ScadaActivity", "Drive service not initialized for test write")
            runOnUiThread {
                android.widget.Toast.makeText(this, "Drive not initialized", android.widget.Toast.LENGTH_LONG).show()
            }
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val fileId = findOrCreateDriveFile(drive, "test_write.txt")
                updateDriveFile(drive, fileId, "Test file written at: ${java.util.Date()}")
                withContext(Dispatchers.Main) {
                    Log.d("ScadaActivity", "Test file written successfully")
                    android.widget.Toast.makeText(this@ScadaActivity, "Test file written to Drive!", android.widget.Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.w("ScadaActivity", "Error writing test file to Drive", e)
                    android.widget.Toast.makeText(this@ScadaActivity, "Drive test write error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
