package com.example.app_sayday

import android.content.Intent
import android.os.Bundle
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {

    private lateinit var splashLogo: ImageView
    private lateinit var appNameText: TextView
    private lateinit var taglineText: TextView
    private lateinit var loadingText: TextView
    private lateinit var logoView: TextView // ✅ Make sure in XML it's really a TextView

    companion object {
        private const val SPLASH_DURATION = 4000L // 4 seconds
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install splash screen (Android 12+ native splash screen)
        installSplashScreen()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Hide action bar (optional if using NoActionBar theme)
        supportActionBar?.hide()

        initializeViews()
        startAnimations()

        // Launch MainActivity after SPLASH_DURATION
        lifecycleScope.launch {
            delay(SPLASH_DURATION)
            navigateToMainActivity()
        }

        // Disable back press during splash
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Do nothing
            }
        })
    }

    private fun initializeViews() {
        splashLogo = findViewById(R.id.splashLogo)
        appNameText = findViewById(R.id.appNameText)
        taglineText = findViewById(R.id.taglineText)
        loadingText = findViewById(R.id.loadingText)
        logoView = findViewById(R.id.logoView)
    }

    private fun startAnimations() {
        val logoFadeIn = AnimationUtils.loadAnimation(this, android.R.anim.fade_in).apply {
            duration = 1000
        }

        val logoScale = AnimationUtils.loadAnimation(this, R.anim.scale_in).apply {
            duration = 800
            startOffset = 200
        }

        val textFadeIn = AnimationUtils.loadAnimation(this, android.R.anim.fade_in).apply {
            duration = 800
            startOffset = 600
        }

        val slideUp = AnimationUtils.loadAnimation(this, android.R.anim.slide_in_left).apply {
            duration = 600
            startOffset = 800
        }

        splashLogo.startAnimation(logoFadeIn)
        appNameText.startAnimation(textFadeIn)
        taglineText.startAnimation(slideUp)
        logoView.startAnimation(logoFadeIn)

        // Start loading text fade-in after 1s
        lifecycleScope.launch {
            delay(1000)
            loadingText.startAnimation(textFadeIn)
        }
    }

    private fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}
