package com.sophiegold.app_sayday

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View

object AnimationHelper {

    private var pulseHandler: Handler? = null
    private var isRunning = false
    private var isPaused = false

    fun startPeriodicPulsing(context: Context, recordButton: View, dateButton: View) {
        if (isRunning) return

        isRunning = true
        isPaused = false
        pulseHandler = Handler(Looper.getMainLooper())

        schedulePulse(recordButton, dateButton)
    }

    private fun schedulePulse(recordButton: View, dateButton: View) {
        if (!isRunning || isPaused) return

        // Random delay between 3-6 seconds (much more frequent)
        val delay = (3000 + Math.random() * 3000).toLong()

        pulseHandler?.postDelayed({
            if (!isRunning || isPaused) return@postDelayed

            // Randomly choose which button to animate
            val choice = (Math.random() * 3).toInt()
            when (choice) {
                0 -> shakeButton(recordButton)
                1 -> shakeButton(dateButton)
                2 -> {
                    shakeButton(recordButton)
                    pulseHandler?.postDelayed({
                        if (!isPaused) shakeButton(dateButton)
                    }, 300)
                }
            }

            // Schedule next pulse
            schedulePulse(recordButton, dateButton)
        }, delay)
    }

    fun pauseAnimations() {
        isPaused = true
    }

    fun resumeAnimations(recordButton: View, dateButton: View) {
        if (isRunning && isPaused) {
            isPaused = false
            schedulePulse(recordButton, dateButton)
        }
    }

    fun stopPeriodicPulsing() {
        isRunning = false
        isPaused = false
        pulseHandler?.removeCallbacksAndMessages(null)
        pulseHandler = null
    }

    // Original pulse animation
    fun pulseButton(button: View) {
        if (button.visibility == View.VISIBLE) {
            button.animate()
                .scaleX(1.1f)
                .scaleY(1.1f)
                .setDuration(400)
                .withEndAction {
                    button.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(400)
                        .start()
                }
                .start()
        }
    }

    // New shake animation
    fun shakeButton(button: View) {
        if (button.visibility == View.VISIBLE) {
            val originalX = button.translationX
            val originalY = button.translationY

            button.animate()
                .translationX(originalX + 15f)
                .setDuration(50)
                .withEndAction {
                    button.animate()
                        .translationX(originalX - 15f)
                        .setDuration(50)
                        .withEndAction {
                            button.animate()
                                .translationX(originalX + 10f)
                                .setDuration(50)
                                .withEndAction {
                                    button.animate()
                                        .translationX(originalX - 10f)
                                        .setDuration(50)
                                        .withEndAction {
                                            button.animate()
                                                .translationX(originalX + 5f)
                                                .setDuration(50)
                                                .withEndAction {
                                                    button.animate()
                                                        .translationX(originalX)
                                                        .setDuration(50)
                                                        .start()
                                                }
                                                .start()
                                        }
                                        .start()
                                }
                                .start()
                        }
                        .start()
                }
                .start()
        }
    }

    // Shake with rotation for more dramatic effect
    fun shakeWithRotation(button: View) {
        if (button.visibility == View.VISIBLE) {
            val originalX = button.translationX
            val originalRotation = button.rotation

            button.animate()
                .translationX(originalX + 12f)
                .rotation(originalRotation + 3f)
                .setDuration(80)
                .withEndAction {
                    button.animate()
                        .translationX(originalX - 12f)
                        .rotation(originalRotation - 3f)
                        .setDuration(80)
                        .withEndAction {
                            button.animate()
                                .translationX(originalX + 8f)
                                .rotation(originalRotation + 2f)
                                .setDuration(80)
                                .withEndAction {
                                    button.animate()
                                        .translationX(originalX - 8f)
                                        .rotation(originalRotation - 2f)
                                        .setDuration(80)
                                        .withEndAction {
                                            button.animate()
                                                .translationX(originalX)
                                                .rotation(originalRotation)
                                                .setDuration(80)
                                                .start()
                                        }
                                        .start()
                                }
                                .start()
                        }
                        .start()
                }
                .start()
        }
    }

    fun guidePulse(button: View) {
        shakeButton(button) // Changed to use shake instead of pulse
    }

    fun cleanup() {
        stopPeriodicPulsing()
    }
}