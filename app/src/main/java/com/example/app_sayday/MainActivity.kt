package com.example.app_sayday

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.ActivityCompat
import java.io.File
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var dateButton: TextView
    private lateinit var logoImageView: ImageView
    private lateinit var recordingsList: LinearLayout
    private lateinit var recordButton: ImageButton
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var mediaPlayer: MediaPlayer? = null
    private val recordings = mutableListOf<String>()
    private var currentFilePath: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize views first
        logoImageView = findViewById(R.id.tapeLogo)
        dateButton = findViewById(R.id.dateButton)
        recordButton = findViewById(R.id.recordButton)
        recordingsList = findViewById(R.id.recordingsList)

        // Disable record button until date is picked
        recordButton.isEnabled = false

        // Date button click listener
        dateButton.setOnClickListener {
            showDatePicker()
        }

        // Record button click listener
        recordButton.setOnClickListener {
            if (isRecording) {
                stopRecording()
            } else {
                startRecording()
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDay ->
            dateButton.text = "$selectedDay/${selectedMonth + 1}/$selectedYear"
            // Enable record button after date is selected
            recordButton.isEnabled = true
        }, year, month, day)

        datePickerDialog.show()
    }

    private fun generateFilePath(): String {
        val timestamp = System.currentTimeMillis()
        return "${externalCacheDir?.absolutePath}/recording_$timestamp.3gp"
    }

    private fun startRecording() {
        // 1. Permission check
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.RECORD_AUDIO),
                0
            )
            return
        }

        // 2. Create safe output file path
        val cacheDir = externalCacheDir ?: filesDir // fallback if externalCacheDir is null
        currentFilePath = "${cacheDir.absolutePath}/recording_${System.currentTimeMillis()}.3gp"

        // 3. Create recorder (no Builder pattern in MediaRecorder)
        @Suppress("DEPRECATION")
        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            setOutputFile(currentFilePath)
        }

        // 4. Start recording
        try {
            mediaRecorder?.prepare()
            mediaRecorder?.start()
            isRecording = true
            recordButton.isSelected = true
            // If you don't have R.drawable.ic_stoprecord, comment out the next line
            recordButton.setImageResource(R.drawable.ic_stoprecord)
        } catch (e: Exception) {
            e.printStackTrace()
            mediaRecorder?.release()
            mediaRecorder = null
            isRecording = false
            recordButton.isSelected = false
            // If you don't have R.drawable.ic_mic, comment out the next line
            recordButton.setImageResource(R.drawable.ic_mic)
        }
    }

    private fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }

            val filePath = currentFilePath
            val file = File(filePath)

            if (file.exists() && file.length() > 0) {
                recordings.add(filePath)
                updateRecordingsList()
            } else {
                println("Recording failed or empty file: $filePath")
            }

        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            mediaRecorder = null
            isRecording = false
            recordButton.isSelected = false
            // If you don't have R.drawable.ic_mic, comment out the next line
            recordButton.setImageResource(R.drawable.ic_mic)
        }
    }

    private fun updateRecordingsList() {
        recordingsList.removeAllViews()

        for (recording in recordings) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(8, 8, 8, 8)
            }

            val textView = TextView(this).apply {
                text = File(recording).name   // show just filename
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            // single play/pause button
            val playPauseButton = ImageView(this).apply {
                setImageResource(R.drawable.ic_play) // default
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
    }

    private fun togglePlayPause(filePath: String, button: ImageView) {
        if (mediaPlayer != null && mediaPlayer!!.isPlaying) {
            // pause/stop current playback
            stopPlaying()
            button.setImageResource(R.drawable.ic_play)
        } else {
            // play new recording
            playRecording(filePath, button)
        }
    }

    private fun playRecording(filePath: String, button: ImageView) {
        stopPlaying() // stop any previous playback

        val file = File(filePath)
        if (!file.exists() || file.length() == 0L) {
            println("Cannot play. File missing or empty: $filePath")
            return
        }

        mediaPlayer = MediaPlayer().apply {
            setDataSource(filePath)

            setOnPreparedListener {
                start()
                button.setImageResource(R.drawable.ic_pause) // change to pause icon
            }

            setOnCompletionListener {
                stopPlaying()
                button.setImageResource(R.drawable.ic_play) // reset to play
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
        if (file.exists()) {
            file.delete()
        }
        recordings.remove(filePath)
        updateRecordingsList()
    }
}