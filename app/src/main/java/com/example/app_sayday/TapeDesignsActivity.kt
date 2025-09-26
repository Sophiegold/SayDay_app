package com.sophiegold.app_sayday
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class TapeDesignsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tape_designs)

        // Add your drawable resource names here
        val tapeLogos = listOf(
            R.drawable.taia,
            R.drawable.des_chin,
            R.drawable.des_tuti,
            R.drawable.des_sea,
            R.drawable.des_ufo
        )

        val recyclerView = findViewById<RecyclerView>(R.id.logoRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = TapeLogoAdapter(tapeLogos) { logoResId ->
            val resultIntent = Intent()
            resultIntent.putExtra("selected_logo", logoResId)
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }
    }
}