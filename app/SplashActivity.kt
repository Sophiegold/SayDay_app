package com.example.app_sayday.com.example.app_sayday

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.app_sayday.MainActivity
import com.example.app_sayday.R

class SplashActivity : AppCompatActivity() {

    private lateinit var splashLogo: ImageView
    private lateinit var appNameText: TextView
    private lateinit var taglineText: TextView
    private lateinit var loadingText: TextView

    companion object {
        private const val SPLASH_DURATION = 3000L // 3 seconds
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install splash screen (Android 12+ native splash screen)
        installSplashScreen()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Hide action bar
        supportActionBar?.hide()

        initializeViews()
        startAnimations()
        navigateToMainActivity()
    }

    private fun initializeViews() {
        splashLogo = findViewById(R.id.splashLogo)
        appNameText = findViewById(R.id.appNameText)
        taglineText = findViewById(R.id.taglineText)
        loadingText = findViewById(R.id.loadingText)
    }

    private fun startAnimations() {
        // Logo fade in and scale animation
        val logoFadeIn = AnimationUtils.loadAnimation(this, android.R.anim.fade_in).apply {
            duration = 1000
        }

        val logoScale = AnimationUtils.loadAnimation(this, R.anim.scale_in).apply {
            duration = 800
            startOffset = 200
        }

        // Text animations
        val textFadeIn = AnimationUtils.loadAnimation(this, android.R.anim.fade_in).apply {
            duration = 800
            startOffset = 600
        }

        val slideUp = AnimationUtils.loadAnimation(this, android.R.anim.slide_in_left).apply {
            duration = 600
            startOffset = 800
        }

        // Apply animations
        splashLogo.startAnimation(logoFadeIn)
        splashLogo.startAnimation(logoScale)
        appNameText.startAnimation(textFadeIn)
        taglineText.startAnimation(slideUp)

        // Loading text with delay
        Handler(Looper.getMainLooper()).postDelayed({
            loadingText.startAnimation(textFadeIn)
        }, 1000)
    }

    private fun navigateToMainActivity() {
        Handler(Looper.getMainLooper()).postDelayed({
            // Start MainActivity
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)

            // Add transition animation
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)

            // Close splash activity
            finish()
        }, SPLASH_DURATION)
    }

    override fun onBackPressed() {
        // Disable back button on splash screen
        super.onBackPressed()
    }
}