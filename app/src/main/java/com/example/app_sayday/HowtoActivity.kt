package com.sophiegold.app_sayday

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class HowtoActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_howto)

        val btnClose = findViewById<Button>(R.id.btnCloseHowto)
        btnClose.setOnClickListener {
            finish() // goes back to main screen
        }
    }
}