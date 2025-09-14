package com.example.app_sayday

import android.content.Intent
import android.os.Bundle
import android.view.animation.AnimationSet
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
    private lateinit var logoView: ImageView

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
                // Intentionally do nothing
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
        // Logo: fade + scale together
        val logoFadeIn = AnimationUtils.loadAnimation(this, android.R.anim.fade_in).apply {
            duration = 1000
        }
        val logoScale = AnimationUtils.loadAnimation(this, R.anim.scale_in).apply {
            duration = 800
            startOffset = 200
        }
        val logoAnimSet = AnimationSet(true).apply {
            addAnimation(logoFadeIn)
            addAnimation(logoScale)
        }

        // App name fade-in
        val appNameFadeIn = AnimationUtils.loadAnimation(this, android.R.anim.fade_in).apply {
            duration = 800
            startOffset = 600
        }

        // Tagline slide up (better to use your own anim resource, here using built-in fade+translate)
        val slideUp = AnimationUtils.loadAnimation(this, R.anim.slide_up).apply {
            duration = 600
            startOffset = 800
        }

        // Loading text fade-in (separate instance so it’s not reused)
        val loadingFadeIn = AnimationUtils.loadAnimation(this, android.R.anim.fade_in).apply {
            duration = 800
            startOffset = 1000
        }

        // Apply animations
        splashLogo.startAnimation(logoAnimSet)
        logoView.startAnimation(logoAnimSet)
        appNameText.startAnimation(appNameFadeIn)
        taglineText.startAnimation(slideUp)

        lifecycleScope.launch {
            delay(1000)
            loadingText.startAnimation(loadingFadeIn)
        }
    }

    private fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}
