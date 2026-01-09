package com.sophiegold.app_sayday

import androidx.appcompat.app.AlertDialog as AppAlertDialog
import android.view.View as AndroidView
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.text.InputType
import android.widget.Toast
import android.widget.EditText
import android.widget.TextView
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.media.MediaPlayer
import android.net.Uri
import android.os.*
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.AnimationUtils
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.exifinterface.media.ExifInterface
import com.bumptech.glide.Glide
import com.prolificinteractive.materialcalendarview.CalendarDay
import com.prolificinteractive.materialcalendarview.DayViewDecorator
import com.prolificinteractive.materialcalendarview.DayViewFacade
import com.prolificinteractive.materialcalendarview.MaterialCalendarView
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MainActivity : AppCompatActivity() {

    // UI Components
    private lateinit var tapeLogoImageView: ImageView
    private lateinit var dateButton: TextView
    private lateinit var recordButton: ImageButton
    private lateinit var recordButton_lbl: TextView
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

    private var isUnlocked = false
    private var isUnlockDialogShowing = false

    private val PREFS_HOWTO_KEY = "prefs_howto"
    private val PREFS_KEY_FIRST_LAUNCH_HOWTO = "first_launch_howto"

    // overlay views (nullable for safety)
    private lateinit var howtoOverlay: FrameLayout
    private lateinit var howtoPanel: View
    private var btnCloseOverlay: View? = null

    private var howtoContentContainer: ViewGroup? = null

    // Recording Management
    private lateinit var audioManager: AudioRecordingManager
    private var selectedDate: String = ""
    private val recordingsByDate = mutableMapOf<String, MutableList<RecordingInfo>>()
    private val dayTitlesByDate = mutableMapOf<String, String>()
    private val bdayDates = mutableSetOf<String>()
    private lateinit var prefs: SharedPreferences
    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    // Per-date image URIs and matrices

    private var pendingImageDate: String? = null
    private val imageUrisByDate = mutableMapOf<String, String>()
    private val IMAGE_URIS_KEY = "image_uris_by_date"
    private val imageMatrixByDate = mutableMapOf<String, FloatArray>()
    private val IMAGE_MATRIX_KEY = "image_matrix_by_date"
    private val matrixSaveRunnableMap = mutableMapOf<String, Runnable>()

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

        // Option B: keep per-date content URI mapping, but cache today's image locally at this path (overwritten)
        private const val TODAY_LOCAL_PATH_KEY = "today_local_path"
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

    // ----------------------------
    // Helper functions for Option B
    // ----------------------------
    private fun getTodayLocalPath(): String? {
        return try {
            prefs.getString(TODAY_LOCAL_PATH_KEY, null)
        } catch (e: Exception) {
            null
        }
    }

    private fun setTodayLocalPath(path: String?) {
        try {
            prefs.edit().putString(TODAY_LOCAL_PATH_KEY, path).apply()
        } catch (_: Exception) { }
    }

    private fun saveImageToInternalStorage(pickedUri: Uri, targetDate: String): String? {
        return try {
            val imagesDir = File(filesDir, "images")
            if (!imagesDir.exists()) imagesDir.mkdirs()

            // Use the date in the filename so each day is unique
            val filename = "image_$targetDate.jpg"
            val outFile = File(imagesDir, filename)

            contentResolver.openInputStream(pickedUri)?.use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return null

            return outFile.absolutePath
        } catch (e: Exception) {
            Log.w("MainActivity", "saveImageToInternalStorage failed: ${e.message}")
            null
        }
    }


    private fun deleteTodayLocalImage(): Boolean {
        val path = getTodayLocalPath() ?: return false
        return try {
            val f = File(path)
            val deleted = if (f.exists()) f.delete() else true
            setTodayLocalPath(null)
            deleted
        } catch (e: Exception) {
            Log.w("MainActivity", "deleteTodayLocalImage failed: ${e.message}")
            false
        }
    }

    private fun decodeBitmapFromFileWithExif(path: String): Bitmap? {
        return try {
            val file = File(path)
            if (!file.exists()) return null
            val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null

            return try {
                val exif = ExifInterface(file.absolutePath)
                val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                val matrix = Matrix()
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                    else -> return bitmap
                }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } catch (e: Exception) {
                bitmap
            }
        } catch (e: Exception) {
            Log.w("MainActivity", "decodeBitmapFromFileWithExif failed: ${e.message}")
            null
        }
    }

    // ----------------------------
    // Image picker -- replaced per Option B
    // ----------------------------
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        val targetDate = pendingImageDate ?: selectedDate
        pendingImageDate = null

        if (uri == null) {
            Toast.makeText(this, "No image selected", Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }

        try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val todayStr = dateFormat.format(Date())

            // Always try to persist the URI permission (harmless if it fails)
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) { }

            if (targetDate == todayStr) {
                executor.execute {
                    val savedPath = saveImageToInternalStorage(uri, targetDate)
                    handler.post {
                        if (savedPath != null) {
                            imageUrisByDate[targetDate] = uri.toString()
                            setTodayLocalPath(savedPath) // still keep a quick pointer for today
                            saveImageUris()
                            updateMyImage()
                        } else {
                            Toast.makeText(this, getString(R.string.failed_to_save_image), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } else {
                imageUrisByDate[targetDate] = uri.toString()
                saveImageUris()
                updateMyImage()
            }
        } catch (e: Exception) {
            Log.w("MainActivity", "pickImageLauncher: unexpected error ${e.message}")
            Toast.makeText(this, getString(R.string.failed_to_save_image), Toast.LENGTH_SHORT).show()
        }
    }


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
                dayTitle.setHintTextColor(color)
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putInt(SELECTED_DAYTITLE_COLOR_KEY, colorResId)
                    .apply()
            }
        }
    }

    // Safe bitmap load with auto-close streams and EXIF handling
    fun getCorrectlyOrientedBitmap(context: Context, imageUri: Uri): Bitmap? {
        return try {
            val bitmap = context.contentResolver.openInputStream(imageUri)?.use { input ->
                BitmapFactory.decodeStream(input)
            } ?: return null

            val exif = context.contentResolver.openInputStream(imageUri)?.use { exifStream ->
                ExifInterface(exifStream)
            } ?: return bitmap

            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                else -> return bitmap
            }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: Exception) {
            Log.w("MainActivity", "getCorrectlyOrientedBitmap failed: ${e.message}")
            null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Warm up encrypted prefs
        LockManager.initialize(this)
        LockManager.migratePlainIfNeeded(this)

        // Initialize SharedPreferences
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        // --- Initialize all UI components (including tapeLogo) ---
        initializeComponents()

        // FIRST-LAUNCH HOWTO: show only after views are laid out so positions are correct
        val firstLaunch = prefs.getBoolean(PREFS_KEY_FIRST_LAUNCH_HOWTO, true)
        if (firstLaunch) {
            // Defer until the view is laid out
            window.decorView.post {
                showHowtoOverlay()
                prefs.edit().putBoolean(PREFS_KEY_FIRST_LAUNCH_HOWTO, false).apply()
            }
        }


        // Toolbar
        val topAppBar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.topAppBar)
        setSupportActionBar(topAppBar)

        topAppBar.setNavigationOnClickListener { view ->
            // Wrap context with your dark popup theme overlay
            val popup = PopupMenu(this, view)
            popup.menuInflater.inflate(R.menu.top_appbar_popup, popup.menu)



            // Handle menu item clicks
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.menu_howto -> {
                        window.decorView.post { showHowtoOverlay() }
                        true
                    }
                    R.id.menu_archive -> { archiveRecordings(this); true }
                    R.id.menu_privacy -> {
                        val privacyUrl = "https://github.com/Sophiegold/SayDay_app/blob/master/Audio_Privacy_Policy"
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(privacyUrl))
                        startActivity(intent)
                        true
                    }
                    R.id.menu_close_app -> {
                        // Confirm then exit gracefully
                        AlertDialog.Builder(this)
                            .setTitle(getString(R.string.exit))
                            .setMessage(getString(R.string.exit_confirm_message).ifEmpty { "Are you sure you want to close the app?" })
                            .setPositiveButton(getString(R.string.yes)) { _, _ ->
                                // Save UI/data state synchronously before exit
                                try {
                                    saveDayTitle()
                                    saveData()
                                    saveImageUris()
                                } catch (e: Exception) {
                                    Log.w("MainActivity", "Error saving state before exit: ${e.message}")
                                }

                                // Optionally stop any ongoing recording or timers
                                try { stopRecordingTimer() } catch (_: Exception) {}
                                try { audioManager.cleanup() } catch (_: Exception) {}

                                // Close the app
                                finishAffinity()
                            }
                            .setNegativeButton(getString(R.string.no), null)
                            .show()
                        true
                    }


                    else -> false
                }
            }

            // Show only once, after everything is set up
            popup.show()
        }

        topAppBar.title = "SayDay"

        setupUI()

        // Load recordings/titles/birthdays but do NOT set an active selectedDate here.
        // We'll prompt the user on startup (or auto-select today if no last saved date).
        loadData()

        // Always auto-select today's date on startup (do not show the startup choice dialog)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val todayStr = dateFormat.format(Date())
        selectDate(todayStr)

        // Continue other initialization
        checkPermissions()
        startButtonAnimations()
        loadImageUris()

        // ensure image is updated for any current selection
        myImage.post { updateMyImage() }

        // Brush / design selection
        findViewById<ImageView>(R.id.brushIcon)?.setOnClickListener {
            val intent = Intent(this, TapeDesignsActivity::class.java)
            selectLogoLauncher.launch(intent)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.top_appbar_menu, menu)
        val lockItem = menu.findItem(R.id.menu_lock_toggle)
        val lockEnabled = LockManager.isLockEnabled(this)
        lockItem.icon = ContextCompat.getDrawable(
            this,
            if (lockEnabled) R.drawable.ic_lock_enabled else R.drawable.ic_lock_disabled
        )
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_lock_toggle -> {
                if (LockManager.isLockEnabled(this)) {
                    // 🔓 Lock is currently enabled → verify before disabling
                    showUnlockDialogForDisable(item)
                } else {
                    // 🔒 Lock is currently disabled → set password to enable
                    showSetPasswordDialog { passwordCharArray ->
                        LockManager.savePassword(this, passwordCharArray)
                        Toast.makeText(this, getString(R.string.lock_enabled_success), Toast.LENGTH_SHORT).show()
                        passwordCharArray.fill('\u0000')
                        item.icon = ContextCompat.getDrawable(this, R.drawable.ic_lock_enabled)
                    }
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }



    override fun onResume() {
        super.onResume()
        LockManager.migratePlainIfNeeded(this)

        // Only enforce on true app start; do not force lock on every resume
        if (LockManager.isLockEnabled(this) && !isUnlocked && !isUnlockDialogShowing) {
            showUnlockDialog()
        }
    }

    private fun showUnlockDialogForDisable(lockItem: MenuItem) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_unlock_password, null)
        val etPassword = dialogView.findViewById<EditText>(R.id.etPassword)
        val tvError = dialogView.findViewById<TextView>(R.id.tvPasswordError)

        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.unlock))
            .setMessage(getString(R.string.enter_password_to_lockoff))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.disable_lock), null)
            .setNegativeButton(getString(R.string.cancel), null)
            .create()

        dialog.setOnShowListener {
            val positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positive.setOnClickListener {
                val inputChars = etPassword.text?.toString()?.toCharArray() ?: charArrayOf()
                executor.execute {
                    val ok = LockManager.verifyPassword(this, inputChars)
                    inputChars.fill('\u0000')
                    handler.post {
                        if (ok) {
                            LockManager.clearLock(this)
                            Toast.makeText(this, getString(R.string.lock_disabled_success), Toast.LENGTH_SHORT).show()
                            lockItem.icon = ContextCompat.getDrawable(this, R.drawable.ic_lock_disabled)
                            dialog.dismiss()
                        } else {
                            tvError.text = getString(R.string.incorrect_password)
                            tvError.visibility = View.VISIBLE
                        }
                    }
                }
            }
        }
        dialog.show()
        etPassword.requestFocus()
    }


    private fun showUnlockDialog() {
        if (isUnlocked || isUnlockDialogShowing) return
        isUnlockDialogShowing = true

        val dialogView = layoutInflater.inflate(R.layout.dialog_unlock_password, null)
        val etPassword = dialogView.findViewById<EditText>(R.id.etPassword)
        val tvError = dialogView.findViewById<TextView>(R.id.tvPasswordError)

        val dialog = AppAlertDialog.Builder(this)
            .setTitle(getString(R.string.unlock))
            .setMessage(getString(R.string.enter_password_to_unlock))
            .setCancelable(false)
            .setView(dialogView)
            .setPositiveButton(getString(R.string.unlock), null)
            .setNegativeButton(getString(R.string.exit)) { _, _ ->
                isUnlockDialogShowing = false
                finishAffinity()
            }
            .create()

        dialog.setOnShowListener {
            val positive = dialog.getButton(AppAlertDialog.BUTTON_POSITIVE)
            positive.setOnClickListener {
                val inputChars = etPassword.text?.toString()?.toCharArray() ?: charArrayOf()
                executor.execute {
                    var ok = LockManager.verifyPassword(this@MainActivity, inputChars)
                    if (!ok) {
                        LockManager.initialize(this@MainActivity)
                        ok = LockManager.verifyPassword(this@MainActivity, inputChars)
                    }
                    inputChars.fill('\u0000')
                    handler.post {
                        if (ok) {
                            isUnlocked = true
                            isUnlockDialogShowing = false
                            dialog.dismiss()
                            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                            selectDate(todayStr)
                            loadImageUris()
                            myImage.post { updateMyImage() }
                        } else {
                            tvError.text = getString(R.string.incorrect_password)
                            tvError.visibility = View.VISIBLE
                        }
                    }
                }
            }
        }
        dialog.show()
        etPassword.requestFocus()
    }


    private fun initializeComponents() {
        dateButton = findViewById(R.id.dateButton)
        recordButton = findViewById(R.id.recordButton)
        recordButton_lbl = findViewById(R.id.recordButton_lbl)
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
        tapeLogoImageView = findViewById(R.id.tapeLogo)

        audioManager = AudioRecordingManager(this)
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        val savedLogoResId = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getInt(TAPE_LOGO_KEY, -1)

        if (savedLogoResId != -1) {
            tapeLogoImageView.setImageResource(savedLogoResId)

            // Also restore the text color for dayTitle
            val savedColorResId = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getInt(SELECTED_DAYTITLE_COLOR_KEY, R.color.defaultDayTitleColor)
            val color = ContextCompat.getColor(this, savedColorResId)
            dayTitle.setTextColor(color)
            dayTitle.setHintTextColor(color)
        }

        bdayImage.setOnClickListener {
            if (selectedDate.isNotEmpty()) {
                if (!bdayDates.contains(selectedDate)) {
                    bdayDates.add(selectedDate)
                    Toast.makeText(
                        this,
                        getString(R.string.this_day_is_marked_as_a_birthday),
                        Toast.LENGTH_SHORT
                    ).show()
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
            pendingImageDate = selectedDate
            pickImageLauncher.launch("image/*")
        }

        // Image view click for deletion
        myImage.setOnClickListener {
            if (imageUrisByDate.containsKey(selectedDate) || getTodayLocalPath() != null) {
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.delete_image_msg))
                    .setMessage(getString(R.string.are_you_sure_you_want_to_delete_this_image))
                    .setPositiveButton(getString(R.string.yes)) { _, _ ->
                        executor.execute {
                            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            val todayStr = dateFormat.format(Date())
                            // If this is today's image, delete the internal cached copy too
                            if (selectedDate == todayStr) {
                                deleteTodayLocalImage()
                            }
                            // If there is a content URI stored for this date, try to release persisted permission
                            val stored = imageUrisByDate[selectedDate]
                            if (!stored.isNullOrEmpty() && stored.startsWith("content://")) {
                                try {
                                    val oldUri = Uri.parse(stored)
                                    contentResolver.releasePersistableUriPermission(oldUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                } catch (_: Exception) { }
                            }
                            // Remove mapping & persist
                            imageUrisByDate.remove(selectedDate)
                            saveImageUris()
                            handler.post { updateMyImage() }
                        }
                    }
                    .setNegativeButton(getString(R.string.no), null)
                    .show()
            }
        }
        val minusIcon = findViewById<ImageButton>(R.id.minusIcon)
        minusIcon.setOnClickListener { myImage.performClick() }
    }

    private fun setupUI() {
        dateButton.setOnClickListener { toggleCalendarVisibility() }

        calendarView.setOnDateChangedListener { _, date, _ ->
            handleDateSelection(date)
        }

        recordButton.setOnClickListener {
            if (audioManager.isRecording()) {
                stopRecording()
                recordButton_lbl.text = getString(R.string.RECORD)
            } else {
                startRecording()
                recordButton_lbl.text = getString(R.string.stop_rec)
            }
        }

        dayTitle.setOnFocusChangeListener { _, hasFocus ->
            dayTitle.isCursorVisible = hasFocus
            if (!hasFocus) saveDayTitle()
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
                    if (filePath != null && error == null) addNewRecording(filePath)
                    else showError(error ?: "Recording failed")
                }
            }

            override fun onPlaybackStarted() {}
            override fun onPlaybackStopped() {
                runOnUiThread { updatePlayButtonsState() }
            }
        })
    }

    private fun updateBdayIconState() {
        bdayImage.alpha = if (bdayDates.contains(selectedDate)) 1f else 0.6f
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
            if (currentTitle.isNotEmpty()) dayTitlesByDate[selectedDate] = currentTitle
            else dayTitlesByDate.remove(selectedDate)
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
        dayTitle.hint = getString(R.string.add_title_hint)
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showSuccess(getString(R.string.permission_granted_you_can_now_record_audio_and_pick_images))
            } else {
                showError(getString(R.string.audio_and_storage_permissions_are_required_for_this_app_to_work))
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

        // Do NOT set selectedDate here. We'll decide in onCreate whether to prompt the user
        // or auto-select today. Leave selectedDate empty and recordButton disabled until selection.
        selectedDate = ""
        recordButton.isEnabled = false

        val bdayJson = prefs.getString(BDAY_DATES_KEY, "[]")
        try {
            val arr = JSONArray(bdayJson!!)
            bdayDates.clear()
            for (i in 0 until arr.length()) bdayDates.add(arr.getString(i))
        } catch (_: Exception) {
            bdayDates.clear()
        }

        // Ensure calendar shows today's month but do NOT select a date yet.
        val todayDay = CalendarDay.today()
        calendarView.currentDate = todayDay
        calendarView.selectedDate = null

        updateCalendarIndicators()
        updateBdayIconState()

        // Only update the image at startup if the app is unlocked or locking is disabled.
        if (!LockManager.isLockEnabled(this) || isUnlocked) {
            updateMyImage()
        }
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
                dayTitlesByDate.forEach { (date, title) -> titlesObject.put(date, title) }

                val bdayArr = JSONArray()
                bdayDates.forEach { date -> bdayArr.put(date) }

                prefs.edit()
                    .putString(RECORDINGS_KEY, recordingsObject.toString())
                    .putString(DAY_TITLES_KEY, titlesObject.toString())
                    .putString(SELECTED_DATE_KEY, selectedDate)
                    .putString(BDAY_DATES_KEY, bdayArr.toString())
                    .apply()
            } catch (_: Exception) {
                // ignore
            }
        }
    }

    private fun cleanupInvalidRecordings() {
        executor.execute {
            val keysToRemove = mutableListOf<String>()
            recordingsByDate.forEach { (date, recordings) ->
                val validRecordings = recordings.filter { File(it.filePath).exists() }.toMutableList()
                if (validRecordings.size != recordings.size) {
                    if (validRecordings.isEmpty()) keysToRemove.add(date)
                    else recordingsByDate[date] = validRecordings
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

    // Helper that centralizes selecting a date and updating UI/state.
    private fun selectDate(dateString: String) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        selectedDate = dateString

        try {
            val parsed = dateFormat.parse(dateString)
            if (parsed != null) {
                val cal = Calendar.getInstance().apply { time = parsed }
                val day = CalendarDay.from(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
                calendarView.selectedDate = day
                calendarView.currentDate = day
            } else {
                val today = CalendarDay.today()
                calendarView.selectedDate = today
                calendarView.currentDate = today
            }
        } catch (e: Exception) {
            val today = CalendarDay.today()
            calendarView.selectedDate = today
            calendarView.currentDate = today
        }

        recordButton.isEnabled = selectedDate.isNotEmpty()
        updateDateButton()
        updateDayTitle()
        updateRecordingsList()
        updateBdayIconState()
        updateMyImage()

        // Persist the user's chosen date
        saveData()
    }

    // Startup dialog asking user to choose Today or Last saved date.
    private fun showStartupChoiceDialog() {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val todayStr = dateFormat.format(Date())
        val lastSaved = prefs.getString(SELECTED_DATE_KEY, "") ?: ""

        // If there's no lastSaved we should not show the dialog (caller handles that case),
        // but guard here as well.
        if (lastSaved.isEmpty()) {
            selectDate(todayStr)
            return
        }

        val builder = AlertDialog.Builder(this)
            .setTitle(getString(R.string.open_date_choice_title).ifEmpty { "Open date" })
            .setMessage(getString(R.string.open_date_choice_msg)
                .ifEmpty { "Do you want to add a record for today or review the last recording you made?" })
            .setCancelable(false)
            .setPositiveButton(getString(R.string.add_for_today).ifEmpty { "Add for Today" }) { _, _ ->
                selectDate(todayStr)
            }
            .setNegativeButton(getString(R.string.review_last).ifEmpty { "Review Last" }) { _, _ ->
                // If lastSaved isn't parseable, fallback to today
                selectDate(lastSaved)
            }

        val dialog = builder.create()
        dialog.show()

        // Disable "Review Last" if there is no last saved date (defensive)
        if (lastSaved.isEmpty()) {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = false
        }
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
                recording.fileName.replace("${getString(R.string.recording)}_${selectedDate}_", "")
                    .replace(".3gp", "")
            })
            hint = (getString(R.string.edit_recording_title))
            setSingleLine(true)
            selectAll()
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.edit_recording_title))
            .setView(editText)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val newTitle = editText.text.toString().trim()
                updateRecordingTitle(recording, newTitle)
            }
            .setNegativeButton(getString(R.string.cancel), null)
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
        showSuccess(getString(R.string.title_updated_successfully))
    }

    private fun startRecording() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            showError(getString(R.string.please_grant_audio_recording_permission))
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
        val recordingWord = getString(R.string.recording)
        return "${recordingWord}_${selectedDate}_${timeFormat.format(Date())}.3gp"
    }

    private fun addNewRecording(filePath: String) {
        val file = File(filePath)
        if (!file.exists() || file.length() == 0L) {
            showError(getString(R.string.recording_file_is_invalid))
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
        showSuccess(getString(R.string.recording_saved_successfully))
    }

    private fun updateRecordingsList() {
        recordingsList.removeAllViews()
        val todaysRecordings = recordingsByDate[selectedDate] ?: emptyList()

        if (todaysRecordings.isEmpty()) {
            val noText = TextView(this).apply {
                text = context.getString(R.string.no_recordings_yet_for_this_date)
                textSize = 16f
                setPadding(16, 24, 16, 24)
                gravity = Gravity.CENTER
                setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.darker_gray))
                typeface = ResourcesCompat.getFont(this@MainActivity, R.font.playpen_medium)
            }
            recordingsList.addView(noText)
            return
        }

        // Add rows for recordings
        todaysRecordings.sortedByDescending { it.timestamp }.forEach { recording ->
            createRecordingRow(recording)
        }

        // Add "Delete All" button at the bottom
        val deleteAllBtn = Button(this).apply {
            text = getString(R.string.delete_all)
            setOnClickListener {
                confirmAndDeleteAllRecordings()
            }
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.orange))
            setTextColor(Color.WHITE) // Optional: Make text white for contrast
            setPadding(16, 16, 16, 16)
        }
        recordingsList.addView(deleteAllBtn)

        // Scroll to the bottom of the recordings list
        recordingsScroll.post { recordingsScroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun createRecordingRow(recording: RecordingInfo) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 12, 16, 12)
            background = ContextCompat.getDrawable(this@MainActivity, android.R.drawable.list_selector_background)
        }
        val infoLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val localTimeZone = TimeZone.getDefault()
        val displayTitle = recording.customTitle.ifEmpty {
            val dateFormat = SimpleDateFormat("MMM d, yyyy, HH:mm", Locale.getDefault())
            dateFormat.timeZone = localTimeZone
            "${getString(R.string.recording)} " + dateFormat.format(Date(recording.timestamp))
        }
        val nameText = TextView(this).apply {
            text = displayTitle
            typeface = ResourcesCompat.getFont(this@MainActivity, R.font.playpen_medium)
            textSize = 14f
            setTextColor(android.graphics.Color.BLACK)
            setOnClickListener { showEditTitleDialog(recording) }
            background = ContextCompat.getDrawable(this@MainActivity, android.R.drawable.list_selector_background)
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
            setOnClickListener { togglePlayPause(recording.filePath, this) }
        }
        val deleteButton = ImageButton(this).apply {
            setImageResource(R.drawable.ic_delete)
            background = null
            setPadding(12, 12, 12, 12)
            setOnClickListener { showDeleteConfirmation(recording) }
        }
        row.addView(infoLayout)
        row.addView(editButton)
        row.addView(playButton)
        row.addView(deleteButton)
        recordingsList.addView(row)
    }

    private fun confirmAndDeleteAllRecordings() {
        if (recordingsByDate[selectedDate].isNullOrEmpty()) {
            showError(getString(R.string.no_recordings_to_delete))
            return
        }

        // Show confirmation dialog
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_all_recordings))
            .setMessage(getString(R.string.delete_all_confirmation_message))
            .setPositiveButton(getString(R.string.yes)) { _, _ -> deleteAllRecordings() }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun deleteAllRecordings() {
        executor.execute {
            val filesToDelete = recordingsByDate[selectedDate]?.map { File(it.filePath) } ?: emptyList()

            filesToDelete.forEach { file ->
                if (file.exists()) file.delete()
            }

            recordingsByDate.remove(selectedDate)  // Clear all recordings for the selected date

            handler.post {
                updateRecordingsList()
                updateCalendarIndicators()
                saveData()  // Persist the changes
                showSuccess(getString(R.string.all_recordings_deleted))
            }
        }
    }

    private fun togglePlayPause(filePath: String, button: ImageButton) {
        if (audioManager.isPlaying()) {
            audioManager.stopPlayback()
            button.setImageResource(R.drawable.ic_play)
        } else {
            audioManager.playRecording(filePath) { isPlaying ->
                runOnUiThread { button.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play) }
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
            .setTitle(getString(R.string.delete_recording))
            .setMessage(getString(R.string.are_you_sure_you_want_to_delete_this_recording_this_action_cannot_be_undone))
            .setPositiveButton(getString(R.string.delete)) { _, _ -> deleteRecording(recording) }
            .setNegativeButton(getString(R.string.cancel_btn), null)
            .show()
    }

    private fun deleteRecording(recording: RecordingInfo) {
        executor.execute {
            val file = File(recording.filePath)
            val deleted = if (file.exists()) file.delete() else true
            handler.post {
                if (deleted) {
                    recordingsByDate[selectedDate]?.remove(recording)
                    if (recordingsByDate[selectedDate]?.isEmpty() == true) recordingsByDate.remove(selectedDate)
                    updateRecordingsList()
                    updateCalendarIndicators()
                    saveData()
                    showSuccess(getString(R.string.recording_deleted))
                } else showError(getString(R.string.failed_to_delete_recording))
            }
        }
    }

    private fun updateRecordingUI(isRecording: Boolean) {
        if (isRecording) {
            Glide.with(this).asGif().load(R.drawable.red_blink).into(recordButton)
            recordingStatusText.text = getString(R.string.recording_to_progress)
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
                            "${getString(R.string.recording_progress)}_${minutes}:${String.format("%02d", secs)}"
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

        // Persist UI/data state
        saveDayTitle()
        saveData()
        saveImageUris()
    }

    private fun showError(message: String) { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
    private fun showSuccess(message: String) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }

    fun archiveRecordings(context: Context) {
        // Run zip creation on background thread
        executor.execute {
            try {
                val allRecordingFiles = recordingsByDate.values.flatten().map { File(it.filePath) }.filter { it.exists() }
                if (allRecordingFiles.isEmpty()) {
                    handler.post { Toast.makeText(context, "No recordings found!", Toast.LENGTH_SHORT).show() }
                    return@execute
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

                val zipUri = FileProvider.getUriForFile(context, context.packageName + ".provider", zipFile)

                handler.post {
                    AlertDialog.Builder(context)
                        .setTitle(getString(R.string.archive_created))
                        .setMessage(getString(R.string.your_recordings_have_been_archived_what_would_you_like_to_do))
                        .setPositiveButton(getString(R.string.open_location)) { _, _ ->
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(zipUri, "application/zip")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            try {
                                context.startActivity(Intent.createChooser(intent, getString(R.string.open_with)))
                            } catch (e: Exception) {
                                Toast.makeText(context, getString(R.string.no_app_found_to_open_zip_file), Toast.LENGTH_SHORT).show()
                            }
                        }
                        .setNegativeButton(getString(R.string.share_zip)) { _, _ ->
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
                        .setNeutralButton(getString(R.string.close), null)
                        .show()
                }
            } catch (e: Exception) {
                handler.post { Toast.makeText(context, "Failed to create ZIP: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }
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
                        CalendarDay.from(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
                    }
                } catch (_: Exception) {
                    null
                }
            }
        if (datesWithRecordings.isNotEmpty()) calendarView.addDecorator(RecordingDotDecorator(this, datesWithRecordings))

        val birthdayDays = bdayDates.mapNotNull { dateString ->
            try {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val date = dateFormat.parse(dateString)
                date?.let {
                    val cal = Calendar.getInstance()
                    cal.time = it
                    CalendarDay.from(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
                }
            } catch (_: Exception) {
                null
            }
        }
        if (birthdayDays.isNotEmpty()) calendarView.addDecorator(BirthdayDecorator(this, birthdayDays))
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
        val myImage = findViewById<FrameLayout>(R.id.myImage)
        val myImageView = myImage.findViewById<ImageView>(R.id.myImageView)

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val todayStr = dateFormat.format(Date())

        // Prefer local cached file when viewing today
        if (selectedDate == todayStr) {
            val localPath = getTodayLocalPath()
            if (!localPath.isNullOrEmpty()) {
                val bitmap = decodeBitmapFromFileWithExif(localPath)
                if (bitmap != null) {
                    myImageView.setImageBitmap(bitmap)
                    myImage.visibility = View.VISIBLE

                    myImage.post {
                        val parentWidth = myImage.width
                        val parentHeight = myImage.height
                        val params = myImageView.layoutParams as FrameLayout.LayoutParams
                        params.width = (parentWidth * 0.83).toInt()
                        params.height = (parentHeight * 0.83).toInt()
                        params.gravity = Gravity.CENTER
                        myImageView.layoutParams = params
                    }
                    return
                } else {
                    // local file missing or decode failed -> fall through to URI path if present
                    setTodayLocalPath(null)
                }
            }
        }

        // Fallback to stored mapping (URI string)
        val uriStr = imageUrisByDate[selectedDate]
        if (uriStr != null) {
            if (uriStr.startsWith("/")) {
                // absolute file path
                val bitmap = decodeBitmapFromFileWithExif(uriStr)
                if (bitmap != null) {
                    myImageView.setImageBitmap(bitmap)
                    myImage.visibility = View.VISIBLE
                    myImage.post {
                        val parentWidth = myImage.width
                        val parentHeight = myImage.height
                        val params = myImageView.layoutParams as FrameLayout.LayoutParams
                        params.width = (parentWidth * 0.83).toInt()
                        params.height = (parentHeight * 0.83).toInt()
                        params.gravity = Gravity.CENTER
                        myImageView.layoutParams = params
                    }
                    return
                } else {
                    // stored internal file missing -> remove mapping
                    imageUrisByDate.remove(selectedDate)
                    saveImageUris()
                    myImage.visibility = View.GONE
                    val params = myImage.layoutParams
                    params.height = 0
                    myImage.layoutParams = params
                    myImageView.setImageDrawable(null)
                    return
                }
            } else {
                // content:// style URI
                val uri = try { Uri.parse(uriStr) } catch (e: Exception) { null }
                if (uri != null) {
                    try {
                        val bitmap = getCorrectlyOrientedBitmap(this, uri)
                        if (bitmap != null) {
                            myImageView.setImageBitmap(bitmap)
                            myImage.visibility = View.VISIBLE
                            myImage.post {
                                val parentWidth = myImage.width
                                val parentHeight = myImage.height
                                val params = myImageView.layoutParams as FrameLayout.LayoutParams
                                params.width = (parentWidth * 0.83).toInt()
                                params.height = (parentHeight * 0.83).toInt()
                                params.gravity = Gravity.CENTER
                                myImageView.layoutParams = params
                            }
                            return
                        } else {
                            // decode failed
                            if (myImageView.drawable == null) {
                                myImage.visibility = View.GONE
                                val params = myImage.layoutParams
                                params.height = 0
                                myImage.layoutParams = params
                                myImageView.setImageDrawable(null)
                            } else {
                                myImage.visibility = View.VISIBLE
                            }
                            return
                        }
                    } catch (se: SecurityException) {
                        Log.w("MainActivity", "updateMyImage: SecurityException opening uri: ${se.message}")
                        if (myImageView.drawable == null) {
                            myImage.visibility = View.GONE
                            val params = myImage.layoutParams
                            params.height = 0
                            myImage.layoutParams = params
                            myImageView.setImageDrawable(null)
                        }
                        return
                    } catch (e: Exception) {
                        Log.w("MainActivity", "updateMyImage: exception decoding uri: ${e.message}")
                        if (myImageView.drawable == null) {
                            myImage.visibility = View.GONE
                            val params = myImage.layoutParams
                            params.height = 0
                            myImage.layoutParams = params
                            myImageView.setImageDrawable(null)
                        }
                        return
                    }
                }
            }
        }

        // No image mapping found
        myImage.visibility = View.GONE
        val params = myImage.layoutParams
        params.height = 0
        myImage.layoutParams = params
        myImageView.setImageDrawable(null)
    }



    private fun dp(v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun showHowtoOverlay() {

        // Initialize overlay components if needed
        if (!this::howtoOverlay.isInitialized) {
            howtoOverlay = findViewById(R.id.howtoOverlay)
            if (howtoOverlay == null) return

            howtoPanel = findViewById(R.id.howtoPanel)
            btnCloseOverlay = findViewById(R.id.btnCloseOverlay)


            // Set up interactions
            howtoOverlay.setOnClickListener { hideHowtoOverlay() }
            howtoPanel.setOnClickListener { /* consume click */ }
            btnCloseOverlay?.setOnClickListener { hideHowtoOverlay() }

        }

        // --- Detect current orientation ---
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        // --- Compute horizontal shift for landscape (30dp to px) ---
        val landShiftPx = if (isLandscape) dp(50) else 0

        // --- Show overlay with fade-in animation ---
        try {
            howtoOverlay.bringToFront()
            howtoOverlay.elevation = 2000f
            (howtoOverlay.parent as? View)?.let {
                it.invalidate()
                it.requestLayout()
            }
        } catch (_: Exception) { }

        howtoOverlay.visibility = View.VISIBLE
        howtoOverlay.alpha = 0f
        howtoOverlay.animate().alpha(1f).setDuration(180).start()
        btnCloseOverlay?.requestFocus()

        // --- Clear any previous dynamic tips ---
        // (we keep your behavior but apply horizontal shift for landscape)
        howtoOverlay.removeAllViews()

        // Helper to add a tip and arrow (each with independent offsets)
        fun addTip(
            targetId: Int,
            message: String,
            arrowRes: Int,
            arrowOffsetX: Int = 0,
            arrowOffsetY: Int = -120,
            textOffsetX: Int = 0,
            textOffsetY: Int = -200
        ) {
            val targetView = findViewById<View>(targetId) ?: return
            val coords = IntArray(2)
            targetView.getLocationOnScreen(coords)
            val x = coords[0]
            val y = coords[1]

            // Adjust offsets for landscape if needed
            val yAdj = if (isLandscape) (arrowOffsetY / 2) else arrowOffsetY
            val textYAdj = if (isLandscape) (textOffsetY / 2) else textOffsetY

            // Arrow
            val arrow = ImageView(this)
            arrow.setImageResource(arrowRes)
            val arrowParams = FrameLayout.LayoutParams(100, 100)
            // subtract landShiftPx from left margins to move tips left in landscape
            arrowParams.leftMargin = x + targetView.width / 2 - 50 + arrowOffsetX - landShiftPx
            arrowParams.topMargin = y + yAdj
            howtoOverlay.addView(arrow, arrowParams)

            // Tip Text
            val tip = TextView(this)
            tip.text = message
            tip.setTextColor(Color.WHITE)
            tip.textSize = 16f
            tip.setBackgroundResource(R.drawable.hint_background)
            tip.setPadding(12, 6, 12, 6)
            val tipParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            // subtract landShiftPx from left margin for text as well
            tipParams.leftMargin = x + textOffsetX - landShiftPx
            tipParams.topMargin = y + textYAdj
            howtoOverlay.addView(tip, tipParams)
        }

        // --- Add tips with custom manual positioning ---
        addTip(
            R.id.recordButton,
            getString(R.string.tap_here_to_start_recording_your_daily_message),
            R.drawable.ic_arrow_down,
            arrowOffsetY = -120,
            textOffsetY = -230
        )

        addTip(
            R.id.dateButton,
            getString(R.string.select_the_date_you_want_to_edit),
            R.drawable.ic_arrow_down,
            arrowOffsetY = -80,
            textOffsetY = -180
        )

        addTip(
            R.id.brushIcon,
            getString(R.string.tap_to_choose_a_design_for_your_tape),
            R.drawable.ic_arrow_up,
            arrowOffsetY = 40,
            textOffsetY = 140
        )

        addTip(
            R.id.recordingsScroll,
            getString(R.string.all_your_recordings_for_this_day_are_listed_here),
            R.drawable.ic_arrow_down,
            arrowOffsetY = 180,
            textOffsetY = 80
        )

        addTip(
            R.id.addImageButton,
            getString(R.string.add_an_image_for_this_date),
            R.drawable.ic_arrow_down,
            arrowOffsetY = -160,
            textOffsetY = -290
        )



    }
    private fun hideHowtoOverlay() {
        if (!this::howtoOverlay.isInitialized) return
        howtoOverlay.animate()
            .alpha(0f)
            .setDuration(150)
            .withEndAction {
                howtoOverlay.visibility = View.GONE

            }
            .start()
    }

    private fun showSetPasswordDialog(onSave: (CharArray) -> Unit) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_set_password, null)
        val etPassword = dialogView.findViewById<EditText>(R.id.etPassword)
        val etConfirmPassword = dialogView.findViewById<EditText>(R.id.etConfirmPassword)
        val tvError = dialogView.findViewById<TextView>(R.id.tvPasswordError)

        val dialog = AppAlertDialog.Builder(this)
            .setTitle(getString(R.string.set_password))
            .setMessage(getString(R.string.lock_strong_warning))
            .setCancelable(false)
            .setView(dialogView)
            .setPositiveButton(getString(R.string.save), null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            val positive = dialog.getButton(AppAlertDialog.BUTTON_POSITIVE)
            positive.setOnClickListener {
                val pwd = etPassword.text?.toString()?.toCharArray() ?: charArrayOf()
                val confirm = etConfirmPassword.text?.toString()?.toCharArray() ?: charArrayOf()

                if (pwd.isEmpty() || !pwd.contentEquals(confirm)) {
                    tvError.text = getString(R.string.passwords_do_not_match)
                    tvError.visibility = View.VISIBLE
                    pwd.fill('\u0000')
                    confirm.fill('\u0000')
                    return@setOnClickListener
                }

                onSave(pwd)
                dialog.dismiss()
            }
        }
        dialog.show()
        etPassword.requestFocus()
    }


    // Decorators for calendar dots and birthdays
    class BirthdayDecorator(private val context: Context, private val dates: Collection<CalendarDay>) :
        DayViewDecorator {
        override fun shouldDecorate(day: CalendarDay): Boolean = dates.contains(day)
        override fun decorate(view: DayViewFacade) {
            view.setSelectionDrawable(ContextCompat.getDrawable(context, R.drawable.ic_cake_l)!!)
        }
    }


}