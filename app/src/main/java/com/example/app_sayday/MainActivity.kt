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

    private lateinit var dateButton: Button
    private lateinit var logoImageView: ImageView
    private lateinit var recordingsList: LinearLayout
    private lateinit var recordButton: ImageButton
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var mediaPlayer: MediaPlayer? = null
    private val recordings = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)



        logoImageView = findViewById(R.id.logoImageView)
        dateButton = findViewById(R.id.dateButton)
        recordButton = findViewById(R.id.recordButton)
        recordingsList = findViewById(R.id.recordingsList)

// Now it's safe to use dateButton
        dateButton.setOnClickListener {
            showDatePicker()
        }

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
            recordButton.isEnabled = true
        }, year, month, day)

        datePickerDialog.show()
    }



    private fun generateFilePath(): String {
        val timestamp = System.currentTimeMillis()
        return "${externalCacheDir?.absolutePath}/recording_$timestamp.3gp"
    }

    private var currentFilePath: String = ""

    private fun startRecording() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.RECORD_AUDIO), 0)
            return
        }

        currentFilePath = "${externalCacheDir?.absolutePath}/recording_${System.currentTimeMillis()}.3gp"

        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            setOutputFile(currentFilePath)
            prepare()
            start()
        }

        isRecording = true
        recordButton.isSelected = true                  // change to recording background
        recordButton.setImageResource(R.drawable.ic_stoprecord)
    }

    private fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            mediaRecorder = null
            isRecording = false
            recordButton.isSelected = false                 // back to default background
            recordButton.setImageResource(R.drawable.ic_mic)

            val filePath = currentFilePath
            recordings.add(filePath)
            updateRecordingsList()
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
                text = recording
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val playButton = ImageView(this).apply {
                setImageResource(R.drawable.ic_play) // your play icon
                setPadding(16, 0, 16, 0)
                setOnClickListener { playRecording(recording) }
            }

            val stopButton = ImageView(this).apply {
                setImageResource(R.drawable.ic_stop) // your stop icon
                setPadding(16, 0, 16, 0)
                setOnClickListener { stopPlaying() }
            }

            val deleteButton = ImageView(this).apply {
                setImageResource(R.drawable.ic_delete) // your bin icon
                setPadding(16, 0, 16, 0)
                setOnClickListener { deleteRecording(recording) }
            }

            row.addView(textView)
            row.addView(playButton)
            row.addView(stopButton)
            row.addView(deleteButton)

            recordingsList.addView(row)
        }
    }



    private fun playRecording(filePath: String) {
        stopPlaying()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(filePath)
            prepare()
            start()
        }
    }

    private fun stopPlaying() {
        mediaPlayer?.release()
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
