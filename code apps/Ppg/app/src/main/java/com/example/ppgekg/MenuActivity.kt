package com.example.ppgekg

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity

class MenuActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_menu)

        val username = intent.getStringExtra("USERNAME")

        val btnPPG: Button = findViewById(R.id.btn_ppg)
        val layoutPpgInfo: LinearLayout = findViewById(R.id.layout_ppg_info)
        val btnPpgMasuk: Button = findViewById(R.id.btn_ppg_masuk)

        // Toggle PPG Info
        btnPPG.setOnClickListener {
            if (layoutPpgInfo.visibility == View.GONE) {
                layoutPpgInfo.visibility = View.VISIBLE
            } else {
                layoutPpgInfo.visibility = View.GONE
            }
        }

        // Masuk ke halaman PPG
        btnPpgMasuk.setOnClickListener {
            val intent = Intent(this, PpgActivity::class.java)
            intent.putExtra("USERNAME", username)
            startActivity(intent)
        }
    }
}
