package com.example.app_sayday


import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.util.Log
import java.io.File

class AudioRecordingManager(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var currentRecordingPath: String? = null
    private var callback: RecordingCallback? = null
    private var playbackCallback: ((Boolean) -> Unit)? = null

    interface RecordingCallback {
        fun onRecordingStarted()
        fun onRecordingStopped(filePath: String?, error: String?)
        fun onPlaybackStarted()
        fun onPlaybackStopped()
    }

    fun setRecordingCallback(callback: RecordingCallback) {
        this.callback = callback
    }

    fun isRecording(): Boolean = mediaRecorder != null
    fun isPlaying(): Boolean = mediaPlayer?.isPlaying == true

    fun startRecording(fileName: String) {
        if (isRecording()) {
            Log.w(TAG, "Already recording")
            return
        }

        try {
            val recordingsDir = getRecordingsDirectory()
            val filePath = File(recordingsDir, fileName).absolutePath
            currentRecordingPath = filePath

            mediaRecorder = createMediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(filePath)
                setMaxDuration(300000) // 5 minutes max
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaRecorder error: what=$what, extra=$extra")
                    stopRecording("Recording failed due to MediaRecorder error")
                }
                setOnInfoListener { _, what, extra ->
                    if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                        stopRecording()
                    }
                }
            }

            mediaRecorder?.prepare()
            mediaRecorder?.start()

            callback?.onRecordingStarted()
            Log.d(TAG, "Recording started: $filePath")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            cleanup()
            callback?.onRecordingStopped(null, "Failed to start recording: ${e.message}")
        }
    }

    fun stopRecording(error: String? = null) {
        if (!isRecording()) return

        var filePath: String? = null

        try {
            mediaRecorder?.apply {
                stop()
                release()
            }

            // Validate the recording file
            currentRecordingPath?.let { path ->
                val file = File(path)
                if (file.exists() && file.length() > 0 && error == null) {
                    filePath = path
                    Log.d(TAG, "Recording saved: $path (${file.length()} bytes)")
                } else {
                    file.delete() // Clean up invalid file
                    Log.w(TAG, "Recording file is invalid or error occurred")
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
            // Try to clean up the file if there was an error
            currentRecordingPath?.let { File(it).delete() }
        } finally {
            mediaRecorder = null
            currentRecordingPath = null
            callback?.onRecordingStopped(filePath, error)
        }
    }

    fun playRecording(filePath: String, onStateChange: (Boolean) -> Unit) {
        this.playbackCallback = onStateChange

        if (isPlaying()) {
            stopPlayback()
            return
        }

        val file = File(filePath)
        if (!file.exists() || file.length() == 0L) {
            Log.w(TAG, "Recording file doesn't exist or is empty: $filePath")
            return
        }

        try {
            stopPlayback() // Ensure we're clean

            mediaPlayer = MediaPlayer().apply {
                setDataSource(filePath)
                setOnPreparedListener {
                    start()
                    callback?.onPlaybackStarted()
                    playbackCallback?.invoke(true)
                    Log.d(TAG, "Playback started: $filePath")
                }
                setOnCompletionListener {
                    stopPlayback()
                    Log.d(TAG, "Playback completed: $filePath")
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                    stopPlayback()
                    true // Error handled
                }
                prepareAsync()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to play recording", e)
            stopPlayback()
        }
    }

    fun stopPlayback() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping playback", e)
        } finally {
            mediaPlayer = null
            callback?.onPlaybackStopped()
            playbackCallback?.invoke(false)
        }
    }

    fun cleanup() {
        try {
            if (isRecording()) {
                stopRecording("App is closing")
            }
            stopPlayback()
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }

    private fun createMediaRecorder(): MediaRecorder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
    }

    private fun getRecordingsDirectory(): File {
        // Use app-specific external storage for recordings
        val recordingsDir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_MUSIC),
            "recordings"
        )

        // Fallback to internal storage if external is not available
        val finalDir = if (recordingsDir.exists() || recordingsDir.mkdirs()) {
            recordingsDir
        } else {
            File(context.filesDir, "recordings").apply { mkdirs() }
        }

        Log.d(TAG, "Recordings directory: ${finalDir.absolutePath}")
        return finalDir
    }

    companion object {
        private const val TAG = "AudioRecordingManager"
    }
}