package com.example.ppgekg

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

import com.jjoe64.graphview.GraphView
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import androidx.core.content.ContextCompat
import java.io.File
import java.io.IOException
import android.os.Environment
import android.os.Build
import android.provider.MediaStore
import android.content.ContentValues
import android.widget.EditText
import java.io.OutputStream
import androidx.appcompat.app.AlertDialog







class RekamanppgActivity : AppCompatActivity() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var userId: String
    private lateinit var sessionId: String
    private lateinit var textViewStatus: TextView
    private lateinit var textViewIR: TextView
    private lateinit var textViewRED: TextView
    private lateinit var textViewSpO2: TextView
    private lateinit var chartPPG: GraphView
    private lateinit var btnPlay: Button
    private lateinit var btnPause: Button
    private lateinit var btnReset: Button
    private lateinit var btnExportCsv: Button

    private val handler = Handler(Looper.getMainLooper())
    private val dataList = mutableListOf<Map<String, Any>>() // Simpan data rekaman
    private var currentIndex = 0
    private var isPlaying = false
    private lateinit var textViewWave: TextView // Tambahkan variabel untuk Wave
    private lateinit var stopwatchTextView: TextView
    private var stopwatchHandler = Handler(Looper.getMainLooper())
    private var isStopwatchRunning = false
    private var stopwatchElapsedTime = 0L
    private var stopwatchStartTime: Long = 0L
    private lateinit var playbackRunnable: Runnable

    // Tambahkan ini:
    private var viewportMaxX = 100.0
    private var viewportMinX = 0.0


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rekamanppg)
        firestore = FirebaseFirestore.getInstance()
        textViewStatus = findViewById(R.id.tv_status_rekaman)
        textViewIR = findViewById(R.id.tv_ir_value)
        textViewRED = findViewById(R.id.tv_red_value)
        textViewSpO2 = findViewById(R.id.tv_spo2_value)
        chartPPG = findViewById(R.id.chart_ppg_replay)
        btnPlay = findViewById(R.id.btn_play_replay)
        btnPause = findViewById(R.id.btn_pause_replay)
        btnReset = findViewById(R.id.btn_reset_replay)
        textViewWave = findViewById(R.id.tv_wave_value)
        stopwatchTextView = findViewById(R.id.tv_stopwatch)
        stopwatchHandler = Handler(Looper.getMainLooper())

        // ===== Setting GraphView untuk zoom & scroll =====
        val viewport = chartPPG.viewport
        viewport.isScrollable = true           // Bisa scroll horizontal
        viewport.isScalable = true             // Bisa zoom in/out
        viewport.setScalableY(false)           // Nonaktif zoom vertikal (optional)
        viewport.setScrollableY(false)         // Nonaktif scroll vertikal (optional)

        viewport.setXAxisBoundsManual(true)   // Aktifkan batas manual sumbu X
        viewport.setMinX(0.0)
        viewport.setMaxX(100.0)                // Contoh maksimal X, nanti bisa disesuaikan dengan data

        // Ambil username & session dari intent
        userId = intent.getStringExtra("USERNAME") ?: ""
        sessionId = intent.getStringExtra("sessionId") ?: ""

        if (userId.isNotEmpty() && sessionId.isNotEmpty()) {
            loadRecordingData()
        } else {
            showToast("Data rekaman tidak valid.")
        }

        btnPlay.setOnClickListener {
            if (!isPlaying) {
                if (currentIndex >= dataList.size) {
                    currentIndex = 0
                    series?.resetData(arrayOf())
                    stopStopwatch()  // Reset stopwatch ke 00:00 saat mulai ulang playback
                }
                isPlaying = true
                btnPlay.visibility = Button.GONE
                btnPause.visibility = Button.VISIBLE
                btnReset.visibility = Button.VISIBLE
                startPlayback()
                startStopwatch()  // lanjut stopwatch dari elapsedTime atau 0 jika reset di atas
            }
        }

        btnPause.setOnClickListener {
            if (isPlaying) {  // Cek kalau lagi play, baru pause
                isPlaying = false
                handler.removeCallbacks(playbackRunnable)
                pauseStopwatch()  // Pause stopwatch
                btnPlay.visibility = Button.VISIBLE
                btnPause.visibility = Button.GONE
            }
        }


        btnReset.setOnClickListener {
            isPlaying = false
            currentIndex = 0
            handler.removeCallbacksAndMessages(null)

            btnPlay.visibility = Button.VISIBLE
            btnPause.visibility = Button.GONE
            btnReset.visibility = Button.GONE

            textViewStatus.text = "Status: Menunggu..."
            textViewIR.text = "IR: -"
            textViewRED.text = "RED: -"
            textViewSpO2.text = "SpO2: -"

            chartPPG.removeAllSeries()    // Clear semua data grafik
            dataList.clear() // Pastikan data sebelumnya dihapus
            series = null    // Hapus grafik lama
            loadRecordingData() // 🔥 INI WAJIB: Biar data dimuat ulang dari Firestore
            stopStopwatch() // ⏹ Stop stopwatch di sini
        }

        btnExportCsv = findViewById(R.id.btn_export_csv)
        btnExportCsv.setOnClickListener {
            exportToCSV(dataList, "rekaman_$sessionId")
            promptAndExportCSV(dataList)
        }

    }

    private fun promptAndExportCSV(dataList: List<Map<String, Any>>) {
        val editText = EditText(this).apply {
            hint = "Masukkan judul file"
        }

        AlertDialog.Builder(this)
            .setTitle("Masukkan Judul File")
            .setView(editText)
            .setPositiveButton("Simpan") { _, _ ->
                val customTitle = editText.text.toString().trim()
                val fileName = if (customTitle.isNotEmpty()) {
                    "$customTitle rekaman_${getCurrentDateTime()}"
                } else {
                    "rekaman_${getCurrentDateTime()}"
                }
                exportToCSV(dataList, fileName)
            }
            .setNegativeButton("Batal", null)
            .show()
    }
    private fun getCurrentDateTime(): String {
        val sdf = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
        return sdf.format(java.util.Date())
    }



    private fun exportToCSV(dataList: List<Map<String, Any>>, fileName: String) {
        val csvHeader = "index;timestamp;ir;red;wave;spo2"
        val csvBody = StringBuilder()
        csvBody.appendLine(csvHeader)

        for ((index, data) in dataList.withIndex()) {
            val timestamp = data["timestamp"]?.toString() ?: ""
            val ir = data["ir"]?.toString() ?: ""
            val red = data["red"]?.toString() ?: ""
            val wave = data["ppg_wave"]?.toString() ?: ""
            val spo2 = data["spo2"]?.toString() ?: ""

            csvBody.append("$index;$timestamp;$ir;$red;$wave;$spo2\n")
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ → MediaStore (tanpa permission)
                val resolver = contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, "$fileName.csv")
                    put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }

                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

                uri?.let {
                    val outputStream: OutputStream? = resolver.openOutputStream(it)
                    outputStream?.write(csvBody.toString().toByteArray())
                    outputStream?.close()
                    Toast.makeText(this, "CSV berhasil disimpan di folder Download", Toast.LENGTH_LONG).show()
                }
            } else {
                // Android 9 ke bawah → gunakan public Downloads directory
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(downloadsDir, "$fileName.csv")
                file.writeText(csvBody.toString())
                Toast.makeText(this, "CSV berhasil disimpan di folder Download", Toast.LENGTH_LONG).show()
            }
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, "Gagal menyimpan file CSV", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadRecordingData() {
        val recordRef = firestore.collection("PPG_Monitoring")
            .document(userId)
            .collection("records")
            .document(sessionId)
            .collection("data")
            .orderBy("timestamp") // 🔹 Urutkan berdasarkan waktu

        recordRef.get().addOnSuccessListener { documents ->
            if (!documents.isEmpty) {
                dataList.clear()
                val spo2List = mutableListOf<Int>() // 🔹 List untuk menyimpan nilai SpO2

                for (document in documents) {
                    val data = document.data
                    dataList.add(data)

                    // 🔹 Tambahkan kode debug di sini
                    val ppgWaveValue = data["ppg_wave"]
                    println("DEBUG: ppg_wave = $ppgWaveValue") // Cek apakah data ada di Logcat

                    // 🔹 Ambil nilai SpO2, jika ada
                    val spo2Value = (data["spo2"] as? Number)?.toInt()
                    if (spo2Value != null) {
                        spo2List.add(spo2Value)
                    }
                }

                showToast("Data rekaman berhasil dimuat.") // ✅ Pindahkan keluar loop

                // 🔹 Aktifkan tombol Play hanya jika ada data
                btnPlay.isEnabled = dataList.isNotEmpty()

                // 🔹 Hitung rata-rata SpO2 setelah semua data dimuat
                hitungRataRataSpO2(spo2List)
            } else {
                showToast("Data rekaman tidak ditemukan.")
                btnPlay.isEnabled = false // 🔹 Matikan tombol Play jika tidak ada data
            }
        }.addOnFailureListener {
            showToast("Gagal memuat data rekaman.")
            btnPlay.isEnabled = false // 🔹 Matikan tombol Play jika error
        }
    }

    private fun hitungRataRataSpO2(spo2List: List<Int>) {
        val statusTextView = findViewById<TextView>(R.id.tvRataRataSpO2) // Ganti ID sesuai XML

        // 🔹 Hitung rata-rata dari semua nilai SpO2 tanpa filter
        val rataRataSpO2 = if (spo2List.isNotEmpty()) {
            spo2List.average()
        } else {
            0.0 // Jika tidak ada nilai, set ke 0
        }

        val avgSpO2 = rataRataSpO2.toInt()

        // 🔹 Tampilkan hasil di UI
        statusTextView?.text = "Rata-rata SpO2: ${"%.2f".format(rataRataSpO2)}%"

        // 🔹 Update status berdasarkan nilai rata-rata
        updateSpO2StatusRekaman(avgSpO2)
    }

    private fun updateSpO2StatusRekaman(spo2: Int) {
        val statusTextView = findViewById<TextView>(R.id.tvSpO2Status)

        // 🔹 Pastikan statusTextView tidak null sebelum digunakan
        statusTextView?.let {
            when {
                spo2 >= 95 -> {
                    it.text = "Status : Normal 🍃"
                    it.setTextColor(ContextCompat.getColor(this, R.color.green))
                }
                spo2 in 90..94 -> {
                    it.text = "Status : Waspada: Risiko Hipoksia ⚠️"
                    it.setTextColor(ContextCompat.getColor(this, R.color.orange))
                }
                spo2 in 70..89 -> {
                    it.text = "Status : PERINGATAN: Hipoksia Akut ❗"
                    it.setTextColor(ContextCompat.getColor(this, R.color.red))
                }
                else -> { // Kalau SpO2 di bawah 70
                    it.text = "Status : Proses ⏳"
                    it.setTextColor(ContextCompat.getColor(this, R.color.gray))
                }
            }
        }
    }

    private val stopwatchRunnable = object : Runnable {
        override fun run() {
            if (isStopwatchRunning) {
                val currentTime = System.currentTimeMillis()
                val elapsed = stopwatchElapsedTime + (currentTime - stopwatchStartTime)
                val seconds = (elapsed / 1000).toInt()
                val minutes = seconds / 60
                val remainingSeconds = seconds % 60

                stopwatchTextView.text = String.format("%02d:%02d", minutes, remainingSeconds)
                stopwatchHandler.postDelayed(this, 500)
            }
        }
    }

    private fun startStopwatch() {
        stopwatchStartTime = System.currentTimeMillis()
        isStopwatchRunning = true
        stopwatchHandler.post(stopwatchRunnable)
    }

    private fun pauseStopwatch() {
        if (isStopwatchRunning) {
            stopwatchElapsedTime += System.currentTimeMillis() - stopwatchStartTime
            isStopwatchRunning = false
            stopwatchHandler.removeCallbacks(stopwatchRunnable)
        }
    }

    private fun resumeStopwatch() {
        if (!isStopwatchRunning) {
            stopwatchStartTime = System.currentTimeMillis()
            isStopwatchRunning = true
            stopwatchHandler.post(stopwatchRunnable)
        }
    }

    private fun stopStopwatch() {
        isStopwatchRunning = false
        stopwatchHandler.removeCallbacks(stopwatchRunnable)
        stopwatchElapsedTime = 0L
        stopwatchTextView.text = "00:00"
    }

    private var series: LineGraphSeries<DataPoint>? = null

    private fun startPlayback() {
        if (dataList.isEmpty()) {
            showToast("Tidak ada data untuk diputar.")
            return
        }

        textViewStatus.text = "Status: Memutar Rekaman..."
        val delayMillis = 17L // Interval antar data

        if (series == null) {
            series = LineGraphSeries()
            series?.color = Color.BLUE
            series?.thickness = 6
            chartPPG.removeAllSeries()
            chartPPG.addSeries(series)
        }

        isPlaying = true

        playbackRunnable = object : Runnable {
            override fun run() {
                if (!isPlaying || currentIndex >= dataList.size) {
                    textViewStatus.text = "Status: Selesai Memutar Rekaman."
                    isPlaying = false
                    btnPlay.visibility = Button.VISIBLE
                    btnPause.visibility = Button.GONE
                    pauseStopwatch()  // Pause stopwatch
                    return
                }



                val data = dataList[currentIndex]
                val ir = (data["ir"] as? Number)?.toFloat() ?: 0f
                val red = (data["red"] as? Number)?.toFloat() ?: 0f
                val spo2 = (data["spo2"] as? Number)?.toFloat() ?: 0f
                val ppgWave = (data["ppg_wave"] as? Number)?.toFloat() ?: 0f
                val timestamp = currentIndex.toDouble()

                val windowSize = 7
                val avgWave = if (currentIndex >= windowSize) {
                    var sum = 0f
                    for (i in 0 until windowSize) {
                        val wave = (dataList[currentIndex - i]["ppg_wave"] as? Number)?.toFloat() ?: 0f
                        sum += wave
                    }
                    sum / windowSize
                } else {
                    ppgWave
                }

                textViewIR.text = "IR: $ir"
                textViewRED.text = "RED: $red"
                textViewSpO2.text = "SpO2: $spo2%"
                textViewWave.text = "Wave: $avgWave"

                series?.appendData(DataPoint(timestamp, avgWave.toDouble()), true, 1000)

                val viewport = chartPPG.viewport
                if (timestamp > viewport.getMaxX(false)) {
                    viewport.setMaxX(timestamp + 5.0)
                    viewport.setMinX(timestamp - 95.0)
                }

                currentIndex++
                handler.postDelayed(this, delayMillis)
            }
        }
        handler.post(playbackRunnable)
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}