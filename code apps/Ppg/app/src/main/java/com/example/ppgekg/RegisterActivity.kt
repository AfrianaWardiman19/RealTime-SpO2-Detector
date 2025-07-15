package com.example.ppgekg

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp

class RegisterActivity : AppCompatActivity() {
    private lateinit var etUsername: EditText
    private lateinit var btnDaftar: Button
    private lateinit var btnBack: ImageButton
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        etUsername = findViewById(R.id.main_et_daftar)
        btnDaftar = findViewById(R.id.main_btn_daftar)
        btnBack = findViewById(R.id.btn_back)

        // Tombol kembali
        btnBack.setOnClickListener { onBackPressed() }

        // Tombol daftar
        btnDaftar.setOnClickListener {
            val username = etUsername.text.toString().trim()

            if (username.isEmpty()) {
                Toast.makeText(this, "Masukkan username!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val userRef = db.collection("users").document(username)

            // Cek apakah username sudah ada
            userRef.get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        Toast.makeText(this, "Username sudah terdaftar, coba yang lain!", Toast.LENGTH_SHORT).show()
                    } else {
                        // Simpan username & timestamp ke Firestore
                        val user = hashMapOf(
                            "username" to username,
                            "created_at" to Timestamp.now()
                        )

                        userRef.set(user)
                            .addOnSuccessListener {
                                Toast.makeText(this, "Pendaftaran berhasil!", Toast.LENGTH_SHORT).show()
                                startActivity(Intent(this, MainActivity::class.java))
                                finish()
                            }
                            .addOnFailureListener {
                                Toast.makeText(this, "Pendaftaran gagal, coba lagi!", Toast.LENGTH_SHORT).show()
                            }
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Terjadi kesalahan, periksa koneksi!", Toast.LENGTH_SHORT).show()
                }
        }
    }
}
