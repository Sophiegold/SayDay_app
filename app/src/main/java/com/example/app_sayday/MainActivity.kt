package com.example.app_sayday

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.ActivityCompat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var dateButton: Button
    private lateinit var recordButton: Button
    private lateinit var logoImageView: ImageView
    private lateinit var recordingsList: LinearLayout
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private val recordings = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        logoImageView = findViewById(R.id.logoImageView)
        dateButton = findViewById(R.id.dateButton)
        recordButton = findViewById(R.id.recordButton)
        recordingsList = findViewById(R.id.recordingsList)

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
        }, year, month, day)

        datePickerDialog.show()
    }

    private fun startRecording() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.RECORD_AUDIO), 0)
            return
        }
        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            setOutputFile("${externalCacheDir?.absolutePath}/recording.3gp")
            prepare()
            start()
        }
        isRecording = true
        recordButton.setBackgroundResource(R.drawable.ic_stop)
    }

    private fun stopRecording() {
        mediaRecorder?.apply {
            stop()
            release()
        }
        isRecording = false
        recordButton.setBackgroundResource(R.drawable.ic_mic)
        recordings.add("Recording saved at ${externalCacheDir?.absolutePath}/recording.3gp")
        updateRecordingsList()
    }

    private fun updateRecordingsList() {
        recordingsList.removeAllViews()
        for (recording in recordings) {
            val textView = TextView(this)
            textView.text = recording
            recordingsList.addView(textView)
        }
    }
}
