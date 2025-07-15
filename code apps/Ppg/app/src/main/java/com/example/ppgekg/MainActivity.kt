package com.example.ppgekg

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {
    private lateinit var etUsername: EditText
    private lateinit var btnLogin: Button
    private lateinit var btnRegister: Button
    private lateinit var btnNoAccount: Button
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etUsername = findViewById(R.id.main_et_username)
        btnLogin = findViewById(R.id.main_btn_login)
        btnRegister = findViewById(R.id.main_btn_register)
        btnNoAccount = findViewById(R.id.main_btn_noaccount)

        btnRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        btnLogin.setOnClickListener {
            val username = etUsername.text.toString().trim()

            if (username.isEmpty()) {
                showToast("Masukkan username!")
                return@setOnClickListener
            }

            val userRef = db.collection("users").document(username)

            // Cek apakah username ada di Firestore
            userRef.get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        // Username ditemukan, lanjut ke MenuActivity
                        startActivity(Intent(this, MenuActivity::class.java).apply {
                            putExtra("USERNAME", username)
                        })
                        finish()
                    } else {
                        showToast("Username tidak terdaftar! Silakan daftar terlebih dahulu.")
                    }
                }
                .addOnFailureListener {
                    showToast("Terjadi kesalahan, periksa koneksi internet!")
                }
        }

        btnNoAccount.setOnClickListener {
            // Langsung masuk ke MenuActivity dengan username default
            startActivity(Intent(this, MenuActivity::class.java).apply {
                putExtra("USERNAME", "No Name")
            })
            finish()
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
