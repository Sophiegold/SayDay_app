package com.example.app_sayday

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.*
import android.view.MotionEvent
import android.view.View
import android.view.animation.AnimationUtils
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.prolificinteractive.materialcalendarview.MaterialCalendarView
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    // UI Components
    private lateinit var logoImageView: ImageView
    private lateinit var dateButton: TextView
    private lateinit var recordButton: ImageButton
    private lateinit var recordingsList: LinearLayout
    private lateinit var dayTitle: EditText
    private lateinit var recordingsScroll: ScrollView
    private lateinit var calendarView: MaterialCalendarView
    private lateinit var calendarContainer: LinearLayout
    private lateinit var recordingStatusText: TextView

    // Recording Management
    private lateinit var audioManager: AudioRecordingManager
    private var selectedDate: String = ""
    private val recordingsByDate = mutableMapOf<String, MutableList<RecordingInfo>>()

    // Data Persistence
    private lateinit var prefs: SharedPreferences

    // Threading
    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
        private const val PREFS_NAME = "recordings_prefs"
        private const val RECORDINGS_KEY = "recordings_data"
        private const val SELECTED_DATE_KEY = "selected_date"
        private const val MAX_RECORDING_DURATION = 300000L // 5 minutes
    }

    data class RecordingInfo(
        val filePath: String,
        val fileName: String,
        val timestamp: Long,
        val duration: Long = 0L
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeComponents()
        setupUI()
        loadData()
        checkPermissions()

        if (selectedDate.isNotEmpty()) {
            updateDateButton()
            updateRecordingsList()
        }
    }

    private fun initializeComponents() {
        // Initialize UI components
        logoImageView = findViewById(R.id.tapeLogo)
        dateButton = findViewById(R.id.dateButton)
        recordButton = findViewById(R.id.recordButton)
        recordingsList = findViewById(R.id.recordingsList)
        dayTitle = findViewById(R.id.dayTitle)
        recordingsScroll = findViewById(R.id.recordingsScroll)
        calendarView = findViewById(R.id.materialCalendarView)
        calendarContainer = findViewById(R.id.calendarContainer)
        recordingStatusText = findViewById(R.id.recordingStatusText)

        // Initialize managers and preferences
        audioManager = AudioRecordingManager(this)
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Set initial states
        recordButton.isEnabled = false
        calendarContainer.visibility = View.GONE
    }

    private fun setupUI() {
        // Date button click toggles calendar
        dateButton.setOnClickListener {
            toggleCalendarVisibility()
        }

        // Calendar date selection
        calendarView.setOnDateChangedListener { _, date, _ ->
            handleDateSelection(date)
        }

        // Recording button
        recordButton.setOnClickListener {
            if (audioManager.isRecording()) {
                stopRecording()
            } else {
                startRecording()
            }
        }

        // Day title focus handling
        dayTitle.setOnFocusChangeListener { _, hasFocus ->
            dayTitle.isCursorVisible = hasFocus
        }

        // Audio manager callbacks
        audioManager.setRecordingCallback(object : AudioRecordingManager.RecordingCallback {
            override fun onRecordingStarted() {
                runOnUiThread {
                    updateRecordingUI(true)
                    startRecordingTimer()
                }
            }

            override fun onRecordingStopped(filePath: String?, error: String?) {
                runOnUiThread {
                    updateRecordingUI(false)
                    stopRecordingTimer()

                    if (filePath != null && error == null) {
                        addNewRecording(filePath)
                    } else {
                        showError(error ?: "Recording failed")
                    }
                }
            }

            override fun onPlaybackStarted() {
                // Handle playback UI updates if needed
            }

            override fun onPlaybackStopped() {
                runOnUiThread { updatePlayButtonsState() }
            }
        })
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.RECORD_AUDIO),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showSuccess("Permission granted! You can now record audio.")
            } else {
                showError("Audio recording permission is required for this app to work.")
            }
        }
    }

    private fun loadData() {
        // Load recordings data using JSON
        val recordingsJson = prefs.getString(RECORDINGS_KEY, "{}")
        try {
            val jsonObject = JSONObject(recordingsJson!!)
            recordingsByDate.clear()

            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                val dateKey = keys.next()
                val recordingsArray = jsonObject.getJSONArray(dateKey)
                val recordingsList = mutableListOf<RecordingInfo>()

                for (i in 0 until recordingsArray.length()) {
                    val recordingObj = recordingsArray.getJSONObject(i)
                    val recording = RecordingInfo(
                        filePath = recordingObj.getString("filePath"),
                        fileName = recordingObj.getString("fileName"),
                        timestamp = recordingObj.getLong("timestamp"),
                        duration = recordingObj.optLong("duration", 0L)
                    )
                    recordingsList.add(recording)
                }
                recordingsByDate[dateKey] = recordingsList
            }

            // Clean up non-existent files
            cleanupInvalidRecordings()
        } catch (e: Exception) {
            // If parsing fails, start with empty data
            recordingsByDate.clear()
        }

        // Load selected date
        selectedDate = prefs.getString(SELECTED_DATE_KEY, "") ?: ""
        recordButton.isEnabled = selectedDate.isNotEmpty()

        // Update calendar indicators after loading data
        updateCalendarIndicators()
    }

    private fun saveData() {
        executor.execute {
            try {
                val jsonObject = JSONObject()

                recordingsByDate.forEach { (date, recordings) ->
                    val recordingsArray = JSONArray()
                    recordings.forEach { recording ->
                        val recordingObj = JSONObject().apply {
                            put("filePath", recording.filePath)
                            put("fileName", recording.fileName)
                            put("timestamp", recording.timestamp)
                            put("duration", recording.duration)
                        }
                        recordingsArray.put(recordingObj)
                    }
                    jsonObject.put(date, recordingsArray)
                }

                prefs.edit()
                    .putString(RECORDINGS_KEY, jsonObject.toString())
                    .putString(SELECTED_DATE_KEY, selectedDate)
                    .apply()
            } catch (e: Exception) {
                // Handle save error gracefully
            }
        }
    }

    private fun cleanupInvalidRecordings() {
        executor.execute {
            val keysToRemove = mutableListOf<String>()

            recordingsByDate.forEach { (date, recordings) ->
                val validRecordings = recordings.filter { File(it.filePath).exists() }.toMutableList()
                if (validRecordings.size != recordings.size) {
                    if (validRecordings.isEmpty()) {
                        keysToRemove.add(date)
                    } else {
                        recordingsByDate[date] = validRecordings
                    }
                }
            }

            keysToRemove.forEach { recordingsByDate.remove(it) }

            handler.post {
                if (selectedDate.isNotEmpty()) updateRecordingsList()
                updateCalendarIndicators()
                saveData()
            }
        }
    }

    private fun toggleCalendarVisibility() {
        val isVisible = calendarContainer.visibility == View.VISIBLE
        calendarContainer.visibility = if (isVisible) View.GONE else View.VISIBLE

        if (!isVisible) {
            val slideIn = AnimationUtils.loadAnimation(this, android.R.anim.slide_in_left)
            calendarContainer.startAnimation(slideIn)
        }
    }

    private fun handleDateSelection(date: com.prolificinteractive.materialcalendarview.CalendarDay) {
        val cal = Calendar.getInstance()
        cal.set(date.year, date.month - 1, date.day)

        selectedDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
        recordButton.isEnabled = true

        updateDateButton()
        updateRecordingsList()
        toggleCalendarVisibility()
        saveData()
    }

    private fun updateDateButton() {
        if (selectedDate.isEmpty()) return

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val date = dateFormat.parse(selectedDate) ?: return
        val cal = Calendar.getInstance()
        cal.time = date

        val dayNumber = cal.get(Calendar.DAY_OF_MONTH)
        val monthName = SimpleDateFormat("MMM", Locale.getDefault()).format(cal.time)
        val yearNumber = cal.get(Calendar.YEAR)

        dateButton.text = "$dayNumber\n$monthName\n$yearNumber"
    }

    private fun startRecording() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            showError("Please grant audio recording permission")
            checkPermissions()
            return
        }

        if (selectedDate.isEmpty()) {
            showError("Please select a date first")
            return
        }

        val fileName = generateFileName()
        audioManager.startRecording(fileName)
    }

    private fun stopRecording() {
        audioManager.stopRecording()
    }

    private fun generateFileName(): String {
        val timeFormat = SimpleDateFormat("HH-mm-ss", Locale.getDefault())
        return "recording_${selectedDate}_${timeFormat.format(Date())}.3gp"
    }

    private fun addNewRecording(filePath: String) {
        val file = File(filePath)
        if (!file.exists() || file.length() == 0L) {
            showError("Recording file is invalid")
            return
        }

        val recording = RecordingInfo(
            filePath = filePath,
            fileName = file.name,
            timestamp = System.currentTimeMillis(),
            duration = 0L
        )

        val recordings = recordingsByDate.getOrPut(selectedDate) { mutableListOf() }
        recordings.add(recording)

        updateRecordingsList()
        updateCalendarIndicators() // Update calendar dots when new recording is added
        saveData()
        showSuccess("Recording saved successfully!")
    }

    private fun updateRecordingsList() {
        recordingsList.removeAllViews()
        val todaysRecordings = recordingsByDate[selectedDate] ?: emptyList()

        if (todaysRecordings.isEmpty()) {
            val noText = TextView(this).apply {
                text = "No recordings yet for this date"
                textSize = 16f
                setPadding(16, 24, 16, 24)
                gravity = android.view.Gravity.CENTER
                setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.darker_gray))
            }
            recordingsList.addView(noText)
            return
        }

        todaysRecordings.sortedByDescending { it.timestamp }.forEach { recording ->
            createRecordingRow(recording)
        }

        recordingsScroll.post { recordingsScroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun createRecordingRow(recording: RecordingInfo) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 12, 16, 12)
            background = ContextCompat.getDrawable(this@MainActivity, android.R.drawable.list_selector_background)
        }

        // Recording info
        val infoLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val nameText = TextView(this).apply {
            text = recording.fileName.replace("recording_${selectedDate}_", "").replace(".3gp", "")
            textSize = 14f
            setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.black))
        }

        val timeText = TextView(this).apply {
            text = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(recording.timestamp))
            textSize = 12f
            setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.darker_gray))
        }

        infoLayout.addView(nameText)
        infoLayout.addView(timeText)

        // Play/Pause button
        val playButton = ImageButton(this).apply {
            setImageResource(R.drawable.ic_play)
            background = null
            setPadding(12, 12, 12, 12)
            setOnClickListener {
                togglePlayPause(recording.filePath, this)
            }
        }

        // Delete button
        val deleteButton = ImageButton(this).apply {
            setImageResource(R.drawable.ic_delete)
            background = null
            setPadding(12, 12, 12, 12)
            setOnClickListener {
                showDeleteConfirmation(recording)
            }
        }

        row.addView(infoLayout)
        row.addView(playButton)
        row.addView(deleteButton)
        recordingsList.addView(row)
    }

    private fun togglePlayPause(filePath: String, button: ImageButton) {
        if (audioManager.isPlaying()) {
            audioManager.stopPlayback()
            button.setImageResource(R.drawable.ic_play)
        } else {
            audioManager.playRecording(filePath) { isPlaying ->
                runOnUiThread {
                    button.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
                }
            }
        }
    }

    private fun updatePlayButtonsState() {
        // Reset all play buttons to play state
        for (i in 0 until recordingsList.childCount) {
            val row = recordingsList.getChildAt(i) as? LinearLayout
            val playButton = row?.getChildAt(1) as? ImageButton
            playButton?.setImageResource(R.drawable.ic_play)
        }
    }

    private fun showDeleteConfirmation(recording: RecordingInfo) {
        AlertDialog.Builder(this)
            .setTitle("Delete Recording")
            .setMessage("Are you sure you want to delete this recording? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                deleteRecording(recording)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteRecording(recording: RecordingInfo) {
        executor.execute {
            val file = File(recording.filePath)
            val deleted = if (file.exists()) file.delete() else true

            handler.post {
                if (deleted) {
                    recordingsByDate[selectedDate]?.remove(recording)

                    // Remove date entry if no recordings left for that date
                    if (recordingsByDate[selectedDate]?.isEmpty() == true) {
                        recordingsByDate.remove(selectedDate)
                    }

                    updateRecordingsList()
                    updateCalendarIndicators() // Update calendar dots when recording is deleted
                    saveData()
                    showSuccess("Recording deleted")
                } else {
                    showError("Failed to delete recording")
                }
            }
        }
    }

    private fun updateRecordingUI(isRecording: Boolean) {
        recordButton.setImageResource(if (isRecording) R.drawable.ic_stoprecord else R.drawable.ic_mic)

        if (isRecording) {
            val pulseAnimation = AnimationUtils.loadAnimation(this, android.R.anim.slide_in_left)
            recordButton.startAnimation(pulseAnimation)
        } else {
            recordButton.clearAnimation()
        }

        recordingStatusText.text = if (isRecording) "Recording..." else ""
        recordingStatusText.visibility = if (isRecording) View.VISIBLE else View.GONE
    }

    private var recordingTimer: Timer? = null
    private var recordingStartTime = 0L

    private fun startRecordingTimer() {
        recordingStartTime = System.currentTimeMillis()
        recordingTimer = Timer().apply {
            scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    val elapsed = System.currentTimeMillis() - recordingStartTime
                    if (elapsed >= MAX_RECORDING_DURATION) {
                        handler.post { stopRecording() }
                        return
                    }

                    handler.post {
                        val seconds = elapsed / 1000
                        val minutes = seconds / 60
                        val secs = seconds % 60
                        recordingStatusText.text = "Recording... ${minutes}:${String.format("%02d", secs)}"
                    }
                }
            }, 1000, 1000)
        }
    }

    private fun stopRecordingTimer() {
        recordingTimer?.cancel()
        recordingTimer = null
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (currentFocus is EditText) {
            val v = currentFocus as EditText
            val outRect = android.graphics.Rect()
            v.getGlobalVisibleRect(outRect)
            if (!outRect.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                v.clearFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(v.windowToken, 0)
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRecordingTimer()
        audioManager.cleanup()
        executor.shutdown()
    }

    override fun onPause() {
        super.onPause()
        saveData()
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showSuccess(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    // Update calendar indicators for dates with recordings
    private fun updateCalendarIndicators() {
        // Remove existing decorators
        calendarView.removeDecorators()

        // Get all dates that have recordings (filter out empty lists)
        val datesWithRecordings = recordingsByDate.filterValues { it.isNotEmpty() }.keys.mapNotNull { dateString ->
            try {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val date = dateFormat.parse(dateString)
                date?.let {
                    val cal = Calendar.getInstance()
                    cal.time = it
                    com.prolificinteractive.materialcalendarview.CalendarDay.from(
                        cal.get(Calendar.YEAR),
                        cal.get(Calendar.MONTH) + 1, // MaterialCalendarView uses 1-based months
                        cal.get(Calendar.DAY_OF_MONTH)
                    )
                }
            } catch (e: Exception) {
                null
            }
        }

        // Add red dot decorator for dates with recordings
        if (datesWithRecordings.isNotEmpty()) {
            calendarView.addDecorator(RecordingDotDecorator(this, datesWithRecordings))
        }
    }

    // Custom decorator class for showing red dots on dates with recordings
    private class RecordingDotDecorator(
        private val context: Context,
        private val dates: Collection<com.prolificinteractive.materialcalendarview.CalendarDay>
    ) : com.prolificinteractive.materialcalendarview.DayViewDecorator {

        override fun shouldDecorate(day: com.prolificinteractive.materialcalendarview.CalendarDay): Boolean {
            return dates.contains(day)
        }

        override fun decorate(view: com.prolificinteractive.materialcalendarview.DayViewFacade) {
            view.addSpan(DotSpan(8f, ContextCompat.getColor(context, android.R.color.holo_red_dark)))
        }
    }

    // Custom span class for drawing dots
    private class DotSpan(private val radius: Float, private val color: Int) :
        android.text.style.LineBackgroundSpan {

        override fun drawBackground(
            canvas: android.graphics.Canvas,
            paint: android.graphics.Paint,
            left: Int,
            right: Int,
            top: Int,
            baseline: Int,
            bottom: Int,
            text: CharSequence,
            start: Int,
            end: Int,
            lineNumber: Int
        ) {
            val oldColor = paint.color
            paint.color = color

            // Draw dot at the bottom center of the date
            val centerX = (left + right) / 2f
            val centerY = bottom + radius + 4f // Slightly below the date text

            canvas.drawCircle(centerX, centerY, radius, paint)
            paint.color = oldColor
        }
    }
}