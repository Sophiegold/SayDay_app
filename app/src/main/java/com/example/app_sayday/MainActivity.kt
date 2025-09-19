package com.example.app_sayday

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.*
import android.view.MotionEvent
import android.view.View
import android.view.animation.AnimationUtils
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.prolificinteractive.materialcalendarview.MaterialCalendarView
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import com.bumptech.glide.Glide

class MainActivity : AppCompatActivity() {

    // UI Components
    private lateinit var tapeLogoImageView: ImageView
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

    // Day Titles Management
    private val dayTitlesByDate = mutableMapOf<String, String>()

    // Data Persistence
    private lateinit var prefs: SharedPreferences

    // Threading
    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
        private const val PREFS_NAME = "recordings_prefs"
        private const val RECORDINGS_KEY = "recordings_data"
        private const val DAY_TITLES_KEY = "day_titles_data"
        private const val SELECTED_DATE_KEY = "selected_date"
        private const val TAPE_LOGO_KEY = "selected_tape_logo"
        private const val MAX_RECORDING_DURATION = 2_400_000L
        private const val SELECTED_DAYTITLE_COLOR_KEY = "selected_daytitle_color"
    }

    data class RecordingInfo(
        val filePath: String,
        val fileName: String,
        val timestamp: Long,
        val duration: Long = 0L,
        var customTitle: String = ""
    )

    private val logoToColorMap = mapOf(
        R.drawable.taia to R.color.colorTaia,
        R.drawable.des_chin to R.color.colorChin,
        R.drawable.des_tuti to R.color.colorTuti,
        R.drawable.des_sea to R.color.colorSea,
        R.drawable.des_ufo to R.color.colorUfo
    )

    // Tape logo selector launcher for activity result
    private val selectLogoLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val selectedLogoResId = result.data?.getIntExtra("selected_logo", -1) ?: -1
            if (selectedLogoResId != -1) {
                tapeLogoImageView.setImageResource(selectedLogoResId)
                // Save selection for persistence
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putInt(TAPE_LOGO_KEY, selectedLogoResId)
                    .apply()

                // Change the color of dayTitle text
                val colorResId = logoToColorMap[selectedLogoResId] ?: R.color.defaultDayTitleColor
                dayTitle.setTextColor(ContextCompat.getColor(this, colorResId))
                // Save selected color for persistence
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putInt(SELECTED_DAYTITLE_COLOR_KEY, colorResId)
                    .apply()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tapeLogoImageView = findViewById(R.id.tapeLogo)
        // Restore the saved tape logo on app launch, default to logo1 if not set
        val savedLogoResId = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getInt(TAPE_LOGO_KEY, R.drawable.taia)
        tapeLogoImageView.setImageResource(savedLogoResId)

        findViewById<ImageView>(R.id.brushIcon)?.setOnClickListener {
            val intent = Intent(this, TapeDesignsActivity::class.java)
            selectLogoLauncher.launch(intent)
        }

        initializeComponents()

        val savedDayTitleColorResId = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getInt(SELECTED_DAYTITLE_COLOR_KEY, R.color.defaultDayTitleColor)
        dayTitle.setTextColor(ContextCompat.getColor(this, savedDayTitleColorResId))

        setupUI()
        loadData()
        checkPermissions()

        // Always update the UI after loading data
        updateDateButton()
        updateDayTitle()
        updateRecordingsList()

        // Start the periodic button pulsing
        startButtonAnimations()
    }

    private fun initializeComponents() {
        // Initialize UI components
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
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

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

        // Day title focus handling and save on text change
        dayTitle.setOnFocusChangeListener { _, hasFocus ->
            dayTitle.isCursorVisible = hasFocus
            if (!hasFocus) {
                saveDayTitle()
            }
        }

        // Save day title when text changes
        dayTitle.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                // Save after a short delay to avoid saving on every character
                handler.removeCallbacks(saveDayTitleRunnable)
                handler.postDelayed(saveDayTitleRunnable, 1000)
            }
        })

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

    // Simplified methods in MainActivity:
    private fun startButtonAnimations() {
        // Start periodic pulsing after app is fully loaded (reduced initial delay)
        handler.postDelayed({
            AnimationHelper.startPeriodicPulsing(this, recordButton, dateButton)
        }, 1500) // Wait only 1.5 seconds after app loads (was 3 seconds)
    }

    private val saveDayTitleRunnable = Runnable {
        saveDayTitle()
    }

    private fun saveDayTitle() {
        if (selectedDate.isNotEmpty()) {
            val currentTitle = dayTitle.text.toString().trim()
            if (currentTitle.isNotEmpty()) {
                dayTitlesByDate[selectedDate] = currentTitle
            } else {
                dayTitlesByDate.remove(selectedDate)
            }
            saveData()
        }
    }

    private fun updateDayTitle() {
        if (selectedDate.isEmpty()) {
            dayTitle.setText("")
            return
        }

        val savedTitle = dayTitlesByDate[selectedDate] ?: ""
        dayTitle.setText(savedTitle)

        // Simple hint
        dayTitle.hint = "Add a title here..."
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
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
                        duration = recordingObj.optLong("duration", 0L),
                        customTitle = recordingObj.optString("customTitle", "")
                    )
                    recordingsList.add(recording)
                }
                recordingsByDate[dateKey] = recordingsList
            }

            // Clean up non-existent files
            cleanupInvalidRecordings()
        } catch (e: Exception) {
            // If parsing fails, start with empty data
            android.util.Log.w("MainActivity", "Failed to parse recordings data", e)
            recordingsByDate.clear()
        }

        // Load day titles data
        val dayTitlesJson = prefs.getString(DAY_TITLES_KEY, "{}")
        try {
            val titlesObject = JSONObject(dayTitlesJson!!)
            dayTitlesByDate.clear()

            val titleKeys = titlesObject.keys()
            while (titleKeys.hasNext()) {
                val dateKey = titleKeys.next()
                val title = titlesObject.getString(dateKey)
                dayTitlesByDate[dateKey] = title
            }
        } catch (e: Exception) {
            // If parsing fails, start with empty data
            android.util.Log.w("MainActivity", "Failed to parse day titles data", e)
            dayTitlesByDate.clear()
        }

        // Load selected date
        selectedDate = prefs.getString(SELECTED_DATE_KEY, "") ?: ""
        recordButton.isEnabled = selectedDate.isNotEmpty()

        if (selectedDate.isEmpty()) {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            selectedDate = today

            // Also set the calendar view to today's date
            val cal = Calendar.getInstance()
            calendarView.selectedDate =
                com.prolificinteractive.materialcalendarview.CalendarDay.from(
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH), // No +1 needed based on your fix
                    cal.get(Calendar.DAY_OF_MONTH)
                )
        }

        recordButton.isEnabled = selectedDate.isNotEmpty()

        // Update calendar indicators after loading data
        updateCalendarIndicators()
    }

    private fun saveData() {
        executor.execute {
            try {
                // Save recordings data
                val recordingsObject = JSONObject()
                recordingsByDate.forEach { (date, recordings) ->
                    val recordingsArray = JSONArray()
                    recordings.forEach { recording ->
                        val recordingObj = JSONObject().apply {
                            put("filePath", recording.filePath)
                            put("fileName", recording.fileName)
                            put("timestamp", recording.timestamp)
                            put("duration", recording.duration)
                            put("customTitle", recording.customTitle)
                        }
                        recordingsArray.put(recordingObj)
                    }
                    recordingsObject.put(date, recordingsArray)
                }

                // Save day titles data
                val titlesObject = JSONObject()
                dayTitlesByDate.forEach { (date, title) ->
                    titlesObject.put(date, title)
                }

                prefs.edit()
                    .putString(RECORDINGS_KEY, recordingsObject.toString())
                    .putString(DAY_TITLES_KEY, titlesObject.toString())
                    .putString(SELECTED_DATE_KEY, selectedDate)
                    .apply()
            } catch (_: Exception) {
                // Handle save error gracefully
            }
        }
    }

    private fun cleanupInvalidRecordings() {
        executor.execute {
            val keysToRemove = mutableListOf<String>()

            recordingsByDate.forEach { (date, recordings) ->
                val validRecordings =
                    recordings.filter { File(it.filePath).exists() }.toMutableList()
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
        // Save current day title before switching dates
        saveDayTitle()

        val cal = Calendar.getInstance()
        cal.set(date.year, date.month, date.day)

        selectedDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
        recordButton.isEnabled = true

        updateDateButton()
        updateDayTitle() // Update day title for the new selected date
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

    private fun showEditTitleDialog(recording: RecordingInfo) {
        val editText = EditText(this).apply {
            setText(recording.customTitle.ifEmpty {
                // Default to time-based name if no custom title
                recording.fileName.replace("recording_${selectedDate}_", "").replace(".3gp", "")
            })
            hint = "Enter recording title"
            setSingleLine(true)
            selectAll()
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Edit Recording Title")
            .setView(editText)
            .setPositiveButton("Save") { _, _ ->
                val newTitle = editText.text.toString().trim()
                updateRecordingTitle(recording, newTitle)
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()

        // Show keyboard automatically
        editText.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun updateRecordingTitle(recording: RecordingInfo, newTitle: String) {
        recording.customTitle = newTitle
        updateRecordingsList() // Refresh the list to show new title
        saveData() // Save changes
        showSuccess("Title updated successfully!")
    }

    private fun startRecording() {
        //  Check audio recording permission
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            showError("Please grant audio recording permission")
            checkPermissions()
            return
        }

        //  Check that a date has been selected
        if (selectedDate.isEmpty()) {
            showError("Please select a date first")
            return
        }

        // Pause animations immediately when starting to record
        AnimationHelper.pauseAnimations()

        val fileName = generateFileName()

        // Play start recording sound
        val mediaPlayer = MediaPlayer.create(this, R.raw.start_record)

        mediaPlayer.setOnCompletionListener {
            // Start actual recording AFTER sound finishes
            audioManager.startRecording(fileName)
            mediaPlayer.release()
        }
        mediaPlayer.start()
    }

    private fun stopRecording() {
        audioManager.stopRecording()
        AnimationHelper.resumeAnimations(recordButton, dateButton)
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
            background = ContextCompat.getDrawable(
                this@MainActivity,
                android.R.drawable.list_selector_background
            )
        }

        // Recording info
        val infoLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val localTimeZone = TimeZone.getDefault()

        val displayTitle = recording.customTitle.ifEmpty {
            val dateFormat = SimpleDateFormat("MMM d, yyyy, HH:mm", Locale.getDefault())
            dateFormat.timeZone = localTimeZone
            "Recording " + dateFormat.format(Date(recording.timestamp))
        }

        val nameText = TextView(this).apply {
            text = displayTitle
            typeface = ResourcesCompat.getFont(this@MainActivity, R.font.playpen_medium)
            textSize = 14f
            setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.black))
            // Make it clickable for editing
            setOnClickListener { showEditTitleDialog(recording) }
            background = ContextCompat.getDrawable(
                this@MainActivity,
                android.R.drawable.list_selector_background
            )
            setPadding(8, 8, 8, 8)
        }

        infoLayout.addView(nameText)

        // Edit button (optional - you can also just tap the title)
        val editButton = ImageButton(this).apply {
            setImageResource(R.drawable.ic_pencil)// Use built-in edit icon
            background = null
            setPadding(12, 12, 12, 12)
            setOnClickListener { showEditTitleDialog(recording) }
        }

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
        row.addView(editButton) // Add edit button
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
            val playButton = row?.getChildAt(2) as? ImageButton // Changed from 1 to 2
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
        if (isRecording) {
            // Load blinking red GIF into record button
            Glide.with(this)
                .asGif()
                .load(R.drawable.red_blink)
                .into(recordButton)

            recordingStatusText.text = "Recording..."
            recordingStatusText.visibility = View.VISIBLE
        } else {
            // Clear GIF / reset to empty or default
            recordButton.setImageDrawable(null)
            Glide.with(this).clear(recordButton)

            recordingStatusText.text = ""
            recordingStatusText.visibility = View.GONE
        }
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
                        recordingStatusText.text =
                            "Recording... ${minutes}:${String.format("%02d", secs)}"
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
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(v.windowToken, 0)
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRecordingTimer()
        AnimationHelper.cleanup()
        audioManager.cleanup()
        executor.shutdown()
    }

    override fun onPause() {
        super.onPause()
        saveDayTitle() // Save current day title when app goes to background
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

        // Add background color for today's date
        val today = com.prolificinteractive.materialcalendarview.CalendarDay.today()
        calendarView.addDecorator(TodayDecorator(this, today))

        // Get all dates that have recordings (filter out empty lists)
        val datesWithRecordings =
            recordingsByDate.filterValues { it.isNotEmpty() }.keys.mapNotNull { dateString ->
                try {
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    val date = dateFormat.parse(dateString)
                    date?.let {
                        val cal = Calendar.getInstance()
                        cal.time = it
                        com.prolificinteractive.materialcalendarview.CalendarDay.from(
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH), // No +1 needed based on your fix
                            cal.get(Calendar.DAY_OF_MONTH)
                        )
                    }
                } catch (_: Exception) {
                    null
                }
            }

        // Add red dot decorator for dates with recordings
        if (datesWithRecordings.isNotEmpty()) {
            calendarView.addDecorator(RecordingDotDecorator(this, datesWithRecordings))
        }
    }
}

// Custom decorator class for showing red dots on dates with recordings