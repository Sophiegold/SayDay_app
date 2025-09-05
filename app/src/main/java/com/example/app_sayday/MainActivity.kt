package com.example.app_sayday

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.prolificinteractive.materialcalendarview.MaterialCalendarView
import com.prolificinteractive.materialcalendarview.CalendarDay
import com.prolificinteractive.materialcalendarview.DayViewDecorator
import com.prolificinteractive.materialcalendarview.DayViewFacade
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var logoImageView: ImageView
    private lateinit var dateButton: TextView
    private lateinit var recordButton: ImageButton
    private lateinit var recordingsList: LinearLayout
    private lateinit var dayTitle: EditText
    private lateinit var recordingsScroll: ScrollView
    private lateinit var calendarView: MaterialCalendarView
    private lateinit var calendarContainer: LinearLayout

    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var isRecording = false
    private var currentFilePath: String = ""
    private var selectedDate: String = "" // yyyy-MM-dd

    private val recordingsByDate = mutableMapOf<String, MutableList<String>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        savedInstanceState?.let {
            val map = it.getSerializable("recordingsByDate") as? HashMap<String, MutableList<String>>
            if (map != null) recordingsByDate.putAll(map)
            selectedDate = it.getString("selectedDate", "")
        }

        // Initialize views
        logoImageView = findViewById(R.id.tapeLogo)
        dateButton = findViewById(R.id.dateButton)
        recordButton = findViewById(R.id.recordButton)
        recordingsList = findViewById(R.id.recordingsList)
        dayTitle = findViewById(R.id.dayTitle)
        recordingsScroll = findViewById(R.id.recordingsScroll)
        calendarView = findViewById(R.id.materialCalendarView)
        calendarContainer = findViewById(R.id.calendarContainer)

        recordButton.isEnabled = selectedDate.isNotEmpty()

        // Initially hide calendar
        calendarContainer.visibility = View.GONE

        // Date button click toggles calendar visibility
        dateButton.setOnClickListener {
            calendarContainer.visibility =
                if (calendarContainer.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        // Handle date selection in calendar
        calendarView.setOnDateChangedListener { widget, date, selected ->
            val cal = Calendar.getInstance()
            cal.set(date.year, date.month - 1, date.day)
            val dayNumber = cal.get(Calendar.DAY_OF_MONTH)
            val monthName = SimpleDateFormat("MMM", Locale.getDefault()).format(cal.time)
            val yearNumber = cal.get(Calendar.YEAR)
            dateButton.text = "$dayNumber\n$monthName\n$yearNumber"

            selectedDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
            recordButton.isEnabled = true

            updateRecordingsList()

            calendarContainer.visibility = View.GONE
        }

        recordButton.setOnClickListener {
            if (isRecording) stopRecording() else startRecording()
        }

        dayTitle.setOnFocusChangeListener { _, hasFocus -> dayTitle.isCursorVisible = hasFocus }

        if (selectedDate.isNotEmpty()) updateRecordingsList()


    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putSerializable("recordingsByDate", HashMap(recordingsByDate))
        outState.putString("selectedDate", selectedDate)
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

    private fun startRecording() {
        if (ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.RECORD_AUDIO), 0)
            return
        }

        val cacheDir = externalCacheDir ?: filesDir
        currentFilePath = "${cacheDir.absolutePath}/recording_${System.currentTimeMillis()}.3gp"

        @Suppress("DEPRECATION")
        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            setOutputFile(currentFilePath)
        }

        try {
            mediaRecorder?.prepare()
            mediaRecorder?.start()
            isRecording = true
            recordButton.setImageResource(R.drawable.ic_stoprecord)
        } catch (e: Exception) {
            e.printStackTrace()
            mediaRecorder?.release()
            mediaRecorder = null
            isRecording = false
            recordButton.setImageResource(R.drawable.ic_mic)
        }
    }

    private fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }

            val file = File(currentFilePath)
            if (file.exists() && file.length() > 0 && selectedDate.isNotEmpty()) {
                val list = recordingsByDate.getOrPut(selectedDate) { mutableListOf() }
                list.add(currentFilePath)
                updateRecordingsList()

            }

        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            mediaRecorder = null
            isRecording = false
            recordButton.setImageResource(R.drawable.ic_mic)
        }
    }

    private fun updateRecordingsList() {
        recordingsList.removeAllViews()
        val todaysRecordings = recordingsByDate[selectedDate] ?: emptyList()

        if (todaysRecordings.isEmpty()) {
            val noText = TextView(this).apply {
                text = "No recordings yet"
                textSize = 16f
                setPadding(8, 8, 8, 8)
            }
            recordingsList.addView(noText)
            return
        }

        for (recording in todaysRecordings) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(8, 8, 8, 8)
            }

            val textView = TextView(this).apply {
                text = File(recording).name
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val playPauseButton = ImageView(this).apply {
                setImageResource(R.drawable.ic_play)
                setPadding(16, 0, 16, 0)
                setOnClickListener { togglePlayPause(recording, this) }
            }

            val deleteButton = ImageView(this).apply {
                setImageResource(R.drawable.ic_delete)
                setPadding(16, 0, 16, 0)
                setOnClickListener {
                    if (mediaPlayer != null) stopPlaying()
                    deleteRecording(recording)
                }
            }

            row.addView(textView)
            row.addView(playPauseButton)
            row.addView(deleteButton)
            recordingsList.addView(row)
        }

        recordingsScroll.post { recordingsScroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun togglePlayPause(filePath: String, button: ImageView) {
        if (mediaPlayer != null && mediaPlayer!!.isPlaying) {
            stopPlaying()
            button.setImageResource(R.drawable.ic_play)
        } else {
            playRecording(filePath, button)
        }
    }

    private fun playRecording(filePath: String, button: ImageView) {
        stopPlaying()
        val file = File(filePath)
        if (!file.exists() || file.length() == 0L) return

        mediaPlayer = MediaPlayer().apply {
            setDataSource(filePath)
            setOnPreparedListener {
                start()
                button.setImageResource(R.drawable.ic_pause)
            }
            setOnCompletionListener {
                stopPlaying()
                button.setImageResource(R.drawable.ic_play)
            }
            try {
                prepareAsync()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun stopPlaying() {
        mediaPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
    }

    private fun deleteRecording(filePath: String) {
        val file = File(filePath)
        if (file.exists()) file.delete()
        recordingsByDate[selectedDate]?.remove(filePath)
        updateRecordingsList()
       }

    // Updates CalendarView dots for dates with recordings


    // Decorator class for dot indicators

}