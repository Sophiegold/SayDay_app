package com.sophiegold.app_sayday

import androidx.core.content.FileProvider
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.os.*
import android.util.Log
import android.view.Gravity
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
import com.prolificinteractive.materialcalendarview.DayViewDecorator
import com.prolificinteractive.materialcalendarview.DayViewFacade
import com.prolificinteractive.materialcalendarview.CalendarDay
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
    private lateinit var calendarClose: ImageView
    private lateinit var recordingStatusText: TextView
    private lateinit var bdayImage: ImageView
    private lateinit var addImageButton: ImageButton
    private lateinit var myImage: FrameLayout // For image feature

    // Recording Management
    private lateinit var audioManager: AudioRecordingManager
    private var selectedDate: String = ""
    private val recordingsByDate = mutableMapOf<String, MutableList<RecordingInfo>>()
    private val dayTitlesByDate = mutableMapOf<String, String>()
    private val bdayDates = mutableSetOf<String>()
    private lateinit var prefs: SharedPreferences
    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    // Per-date image URIs (serialized as JSON string map)
    private val imageUrisByDate = mutableMapOf<String, String>()
    private val IMAGE_URIS_KEY = "image_uris_by_date"

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
        private const val PREFS_NAME = "recordings_prefs"
        private const val RECORDINGS_KEY = "recordings_data"
        private const val DAY_TITLES_KEY = "day_titles_data"
        private const val SELECTED_DATE_KEY = "selected_date"
        private const val TAPE_LOGO_KEY = "selected_tape_logo"
        private const val MAX_RECORDING_DURATION = 2_400_000L
        private const val SELECTED_DAYTITLE_COLOR_KEY = "selected_daytitle_color"
        private const val BDAY_DATES_KEY = "bday_dates"
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

    private val selectLogoLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val selectedLogoResId = result.data?.getIntExtra("selected_logo", -1) ?: -1
            if (selectedLogoResId != -1) {
                tapeLogoImageView.setImageResource(selectedLogoResId)
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putInt(TAPE_LOGO_KEY, selectedLogoResId)
                    .apply()
                val colorResId = logoToColorMap[selectedLogoResId] ?: R.color.defaultDayTitleColor
                val color = ContextCompat.getColor(this, colorResId)
                dayTitle.setTextColor(color)
                dayTitle.setHintTextColor(color) // <-- this sets the hint text color to match

                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putInt(SELECTED_DAYTITLE_COLOR_KEY, colorResId)
                    .apply()
            }
        }
    }

    // Image picker for gallery
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        Log.d("ImagePicker", "Received URI: $uri")
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: SecurityException) {
                Log.w("ImagePicker", "Persist permission failed: ${e.message}")
            }
            // Check MIME type
            val mimeType = contentResolver.getType(uri)
            Log.d("ImagePicker", "MIME type: $mimeType")
            if (mimeType?.startsWith("image/") == true) {
                try {
                    val inputStream = contentResolver.openInputStream(uri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()
                    Log.d("ImagePicker", "Bitmap loaded: ${bitmap != null}")
                    if (bitmap != null) {
                        // Good! Save and show as before
                        imageUrisByDate[selectedDate] = uri.toString()
                        saveImageUris()
                        updateMyImage()
                    } else {
                        Toast.makeText(this, "Failed to load image from URI", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e("ImagePicker", "Error opening URI", e)
                    Toast.makeText(this, "Error opening image", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Selected file is not an image", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "No image selected", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // --- TOP APP BAR / TOOLBAR SETUP ---
        val topAppBar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.topAppBar)
        setSupportActionBar(topAppBar)

// Show your custom popup menu on hamburger click
        topAppBar.setNavigationOnClickListener { view ->
            val popup = PopupMenu(this, view)
            popup.menuInflater.inflate(R.menu.top_appbar_menu, popup.menu)

            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.menu_about -> {
                        Toast.makeText(this, "About clicked!", Toast.LENGTH_SHORT).show()
                        true
                    }

                    R.id.menu_howto -> {
                        startActivity(Intent(this, HowtoActivity::class.java))
                        true
                    }


                    R.id.menu_archive -> {
                        archiveRecordings(this)
                        true
                    }
                    R.id.menu_rate -> {
                        Toast.makeText(this, "Rate clicked!", Toast.LENGTH_SHORT).show()
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }

// Optionally set the title programmatically
        topAppBar.title = "SayDay"

        tapeLogoImageView = findViewById(R.id.tapeLogo)
        val savedLogoResId = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getInt(TAPE_LOGO_KEY, R.drawable.taia)
        tapeLogoImageView.setImageResource(savedLogoResId)

        findViewById<ImageView>(R.id.brushIcon)?.setOnClickListener {
            val intent = Intent(this, TapeDesignsActivity::class.java)
            selectLogoLauncher.launch(intent)
        }

        initializeComponents()
        setupUI()
        loadData()
        loadImageUris() // Load image URIs per date
        checkPermissions()
        updateDateButton()
        updateDayTitle()
        updateRecordingsList()
        startButtonAnimations()
        updateMyImage() // Show image for current date


    }


    private fun initializeComponents() {
        dateButton = findViewById(R.id.dateButton)
        recordButton = findViewById(R.id.recordButton)
        recordingsList = findViewById(R.id.recordingsList)
        dayTitle = findViewById(R.id.dayTitle)
        recordingsScroll = findViewById(R.id.recordingsScroll)
        calendarView = findViewById(R.id.materialCalendarView)
        calendarContainer = findViewById(R.id.calendarContainer)
        calendarClose = findViewById(R.id.calendarClose)
        recordingStatusText = findViewById(R.id.recordingStatusText)
        bdayImage = findViewById(R.id.check_Bday)
        addImageButton = findViewById(R.id.addImageButton)
        myImage = findViewById(R.id.myImage) // For image feature

        audioManager = AudioRecordingManager(this)
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        bdayImage.setOnClickListener {
            if (selectedDate.isNotEmpty()) {
                if (!bdayDates.contains(selectedDate)) {
                    bdayDates.add(selectedDate)
                    Toast.makeText(this, "This day is marked as a Birthday!", Toast.LENGTH_SHORT).show()
                } else {
                    bdayDates.remove(selectedDate)
                }
                saveData()
                updateCalendarIndicators()
                updateBdayIconState()
            }
        }

        calendarClose.setOnClickListener {
            calendarContainer.visibility = View.GONE
        }

        recordButton.isEnabled = false
        calendarContainer.visibility = View.GONE

        // Add image picker logic
        addImageButton.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        // Image view click for deletion
        myImage.setOnClickListener {
            if (imageUrisByDate.containsKey(selectedDate)) {
                AlertDialog.Builder(this)
                    .setTitle("Delete Image")
                    .setMessage("Are you sure you want to delete this image?")
                    .setPositiveButton("Yes") { _, _ ->
                        imageUrisByDate.remove(selectedDate)
                        saveImageUris()
                        updateMyImage()
                    }
                    .setNegativeButton("No", null)
                    .show()
            }
        }
        val minusIcon = findViewById<ImageButton>(R.id.minusIcon)
        minusIcon.setOnClickListener {
            if (imageUrisByDate.containsKey(selectedDate)) {
                AlertDialog.Builder(this)
                    .setTitle("Delete Image")
                    .setMessage("Are you sure you want to delete this image?")
                    .setPositiveButton("Yes") { _, _ ->
                        imageUrisByDate.remove(selectedDate)
                        saveImageUris()
                        updateMyImage()
                    }
                    .setNegativeButton("No", null)
                    .show()
            }
        }
    }

    private fun setupUI() {
        dateButton.setOnClickListener {
            toggleCalendarVisibility()
        }

        calendarView.setOnDateChangedListener { _, date, _ ->
            handleDateSelection(date)
        }

        recordButton.setOnClickListener {
            if (audioManager.isRecording()) {
                stopRecording()
            } else {
                startRecording()
            }
        }

        dayTitle.setOnFocusChangeListener { _, hasFocus ->
            dayTitle.isCursorVisible = hasFocus
            if (!hasFocus) {
                saveDayTitle()
            }
        }

        dayTitle.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                handler.removeCallbacks(saveDayTitleRunnable)
                handler.postDelayed(saveDayTitleRunnable, 1000)
            }
        })

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

            override fun onPlaybackStarted() { }
            override fun onPlaybackStopped() {
                runOnUiThread { updatePlayButtonsState() }
            }
        })
    }

    private fun updateBdayIconState() {
        if (bdayDates.contains(selectedDate)) {
            bdayImage.alpha = 1f
        } else {
            bdayImage.alpha = 0.6f
        }
    }

    private fun startButtonAnimations() {
        handler.postDelayed({
            AnimationHelper.startPeriodicPulsing(this, recordButton, dateButton)
        }, 1500)
    }

    private val saveDayTitleRunnable = Runnable { saveDayTitle() }

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
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE),
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
                showSuccess("Permission granted! You can now record audio and pick images.")
            } else {
                showError("Audio and storage permissions are required for this app to work.")
            }
        }
    }

    private fun loadData() {
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
            cleanupInvalidRecordings()
        } catch (e: Exception) {
            Log.w("MainActivity", "Failed to parse recordings data", e)
            recordingsByDate.clear()
        }

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
            Log.w("MainActivity", "Failed to parse day titles data", e)
            dayTitlesByDate.clear()
        }

        selectedDate = prefs.getString(SELECTED_DATE_KEY, "") ?: ""
        recordButton.isEnabled = selectedDate.isNotEmpty()

        val bdayJson = prefs.getString(BDAY_DATES_KEY, "[]")
        try {
            val arr = JSONArray(bdayJson!!)
            bdayDates.clear()
            for (i in 0 until arr.length()) {
                bdayDates.add(arr.getString(i))
            }
        } catch (_: Exception) {
            bdayDates.clear()
        }

        if (selectedDate.isEmpty()) {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            selectedDate = today
            val cal = Calendar.getInstance()
            calendarView.selectedDate =
                CalendarDay.from(
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH)
                )
        }
        recordButton.isEnabled = selectedDate.isNotEmpty()
        updateCalendarIndicators()
        updateBdayIconState()
        updateMyImage() // Update image for current date after loading data
    }

    private fun saveData() {
        executor.execute {
            try {
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

                val titlesObject = JSONObject()
                dayTitlesByDate.forEach { (date, title) ->
                    titlesObject.put(date, title)
                }

                val bdayArr = JSONArray()
                bdayDates.forEach { date -> bdayArr.put(date) }

                prefs.edit()
                    .putString(RECORDINGS_KEY, recordingsObject.toString())
                    .putString(DAY_TITLES_KEY, titlesObject.toString())
                    .putString(SELECTED_DATE_KEY, selectedDate)
                    .putString(BDAY_DATES_KEY, bdayArr.toString())
                    .apply()
            } catch (_: Exception) {
                // Handle save error
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

    private fun handleDateSelection(date: CalendarDay) {
        saveDayTitle()
        val cal = Calendar.getInstance()
        cal.set(date.year, date.month, date.day)
        selectedDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
        recordButton.isEnabled = true
        updateDateButton()
        updateDayTitle()
        updateRecordingsList()
        toggleCalendarVisibility()
        saveData()
        updateBdayIconState()
        updateMyImage() // Update image when date changes
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
        editText.requestFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun updateRecordingTitle(recording: RecordingInfo, newTitle: String) {
        recording.customTitle = newTitle
        updateRecordingsList()
        saveData()
        showSuccess("Title updated successfully!")
    }

    private fun startRecording() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            showError("Please grant audio recording permission")
            checkPermissions()
            return
        }
        if (selectedDate.isEmpty()) {
            showError("Please select a date first")
            return
        }
        AnimationHelper.pauseAnimations()
        val fileName = generateFileName()
        val mediaPlayer = MediaPlayer.create(this, R.raw.start_record)
        mediaPlayer.setOnCompletionListener {
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
        updateCalendarIndicators()
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
                gravity = Gravity.CENTER
                setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.darker_gray))
                typeface = ResourcesCompat.getFont(this@MainActivity, R.font.playpen_medium)
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
            setTextColor(android.graphics.Color.BLACK)
            setOnClickListener { showEditTitleDialog(recording) }
            background = ContextCompat.getDrawable(
                this@MainActivity,
                android.R.drawable.list_selector_background
            )
            setPadding(8, 8, 8, 8)
        }
        infoLayout.addView(nameText)
        val editButton = ImageButton(this).apply {
            setImageResource(R.drawable.ic_pencil)
            background = null
            setPadding(12, 12, 12, 12)
            setOnClickListener { showEditTitleDialog(recording) }
        }
        val playButton = ImageButton(this).apply {
            setImageResource(R.drawable.ic_play)
            background = null
            setPadding(12, 12, 12, 12)
            setOnClickListener {
                togglePlayPause(recording.filePath, this)
            }
        }
        val deleteButton = ImageButton(this).apply {
            setImageResource(R.drawable.ic_delete)
            background = null
            setPadding(12, 12, 12, 12)
            setOnClickListener {
                showDeleteConfirmation(recording)
            }
        }
        row.addView(infoLayout)
        row.addView(editButton)
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
        for (i in 0 until recordingsList.childCount) {
            val row = recordingsList.getChildAt(i) as? LinearLayout
            val playButton = row?.getChildAt(2) as? ImageButton
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
                    if (recordingsByDate[selectedDate]?.isEmpty() == true) {
                        recordingsByDate.remove(selectedDate)
                    }
                    updateRecordingsList()
                    updateCalendarIndicators()
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
            Glide.with(this)
                .asGif()
                .load(R.drawable.red_blink)
                .into(recordButton)
            recordingStatusText.text = "Recording..."
            recordingStatusText.visibility = View.VISIBLE
        } else {
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
        saveDayTitle()
        saveData()
        saveImageUris() // Also persist images when paused
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showSuccess(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }


    fun archiveRecordings(context: Context) {
        val allRecordingFiles = recordingsByDate.values.flatten().map { File(it.filePath) }.filter { it.exists() }
        if (allRecordingFiles.isEmpty()) {
            Toast.makeText(context, "No recordings found!", Toast.LENGTH_SHORT).show()
            return
        }

        val zipFile = File(context.filesDir, "SayDayRecordings.zip")
        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
            allRecordingFiles.forEach { file ->
                FileInputStream(file).use { fis ->
                    val entry = ZipEntry(file.name)
                    zos.putNextEntry(entry)
                    fis.copyTo(zos)
                    zos.closeEntry()
                }
            }
        }

        // Use FileProvider to get a URI for the ZIP file
        val zipUri = FileProvider.getUriForFile(
            context,
            context.packageName + ".provider",
            zipFile
        )

        AlertDialog.Builder(context)
            .setTitle("Archive Created")
            .setMessage("Your recordings have been archived. What would you like to do?")
            .setPositiveButton("Open Location") { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(zipUri, "application/zip")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                try {
                    context.startActivity(Intent.createChooser(intent, "Open with"))
                } catch (e: Exception) {
                    Toast.makeText(context, "No app found to open ZIP file.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Share ZIP") { _, _ ->
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, zipUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                try {
                    context.startActivity(Intent.createChooser(shareIntent, "Share ZIP"))
                } catch (e: Exception) {
                    Toast.makeText(context, "No app found to share ZIP.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton("Close", null)
            .show()
    }

    private fun updateCalendarIndicators() {
        calendarView.removeDecorators()
        val today = CalendarDay.today()
        calendarView.addDecorator(TodayDecorator(this, today))
        val datesWithRecordings =
            recordingsByDate.filterValues { it.isNotEmpty() }.keys.mapNotNull { dateString ->
                try {
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    val date = dateFormat.parse(dateString)
                    date?.let {
                        val cal = Calendar.getInstance()
                        cal.time = it
                        CalendarDay.from(
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH),
                            cal.get(Calendar.DAY_OF_MONTH)
                        )
                    }
                } catch (_: Exception) {
                    null
                }
            }
        if (datesWithRecordings.isNotEmpty()) {
            calendarView.addDecorator(RecordingDotDecorator(this, datesWithRecordings))
        }
        val birthdayDays = bdayDates.mapNotNull { dateString ->
            try {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val date = dateFormat.parse(dateString)
                date?.let {
                    val cal = Calendar.getInstance()
                    cal.time = it
                    CalendarDay.from(
                        cal.get(Calendar.YEAR),
                        cal.get(Calendar.MONTH),
                        cal.get(Calendar.DAY_OF_MONTH)
                    )
                }
            } catch (_: Exception) {
                null
            }
        }
        if (birthdayDays.isNotEmpty()) {
            calendarView.addDecorator(BirthdayDecorator(this, birthdayDays))
        }
    }

    // --- IMAGE PICKER AND DISPLAY LOGIC ---

    private fun handlePickedImage(uri: Uri) {
        imageUrisByDate[selectedDate] = uri.toString()
        saveImageUris()
        updateMyImage()
    }

    private fun loadImageUris() {
        val json = prefs.getString(IMAGE_URIS_KEY, "{}") ?: "{}"
        imageUrisByDate.clear()
        try {
            val obj = JSONObject(json)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                imageUrisByDate[k] = obj.getString(k)
            }
        } catch (_: Exception) {}
    }

    private fun saveImageUris() {
        val obj = JSONObject()
        imageUrisByDate.forEach { (date, uri) -> obj.put(date, uri) }
        prefs.edit().putString(IMAGE_URIS_KEY, obj.toString()).apply()
    }



    private fun updateMyImage() {
        val uriStr = imageUrisByDate[selectedDate]
        val myImage = findViewById<FrameLayout>(R.id.myImage)
        val myImageView = myImage.findViewById<ImageView>(R.id.myImageView)

        if (uriStr != null) {
            val uri = Uri.parse(uriStr)
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                if (bitmap != null) {
                    myImageView.setImageBitmap(bitmap)
                    myImage.visibility = View.VISIBLE

                    // Update myImageView to be 80% of parent size and centered
                    myImage.post {
                        val parentWidth = myImage.width
                        val parentHeight = myImage.height

                        val params = myImageView.layoutParams as FrameLayout.LayoutParams
                        params.width = (parentWidth * 0.8).toInt()
                        params.height = (parentHeight * 0.8).toInt()
                        params.gravity = Gravity.CENTER
                        myImageView.layoutParams = params
                    }
                } else {
                    myImage.visibility = View.GONE
                    val params = myImage.layoutParams
                    params.height = 0
                    myImage.layoutParams = params
                    myImageView.setImageDrawable(null)
                }
            } catch (e: Exception) {
                myImage.visibility = View.GONE
                val params = myImage.layoutParams
                params.height = 0
                myImage.layoutParams = params
                myImageView.setImageDrawable(null)
            }
        } else {
            myImage.visibility = View.GONE
            val params = myImage.layoutParams
            params.height = 0
            myImage.layoutParams = params
            myImageView.setImageDrawable(null)
        }
    }
}

// Decorators for calendar dots and birthdays
class BirthdayDecorator(
    private val context: Context,
    private val dates: Collection<CalendarDay>
) : DayViewDecorator {
    override fun shouldDecorate(day: CalendarDay): Boolean = dates.contains(day)
    override fun decorate(view: DayViewFacade) {
        view.setSelectionDrawable(ContextCompat.getDrawable(context, R.drawable.ic_cake_l)!!)
    }
}

