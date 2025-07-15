package com.example.ppgekg

import android.Manifest
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.jjoe64.graphview.GraphView
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import java.text.SimpleDateFormat
import java.util.*
import android.util.Log
import android.content.Intent
import android.graphics.Color
import android.os.Looper


class PpgActivity : AppCompatActivity(), OnDataReceivedListener {
    private lateinit var tvUsername: TextView
    private lateinit var tvSpO2: TextView
    private lateinit var tvIr: TextView
    private lateinit var tvRed: TextView
    private lateinit var tvBluetoothStatus: TextView
    private lateinit var tvFingerStatus: TextView
    private lateinit var tvDatetime: TextView
    private lateinit var btnConnectBluetooth: Button
    private lateinit var btnDisconnectBluetooth: Button
    private lateinit var btnStartRecord: Button
    private lateinit var btnStopRecord: Button
    private lateinit var stopwatchTextView: TextView
    private var stopwatchHandler = Handler(Looper.getMainLooper())
    private var stopwatchStartTime = 0L
    private var isStopwatchRunning = false
    private lateinit var graphPpg: GraphView
    private lateinit var listViewRecords: ListView
    private var series = LineGraphSeries<DataPoint>()
    private var lastXValue = 0.0
    private lateinit var btnReset: Button

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }

    private var bluetoothService: BluetoothService? = null
    private val firestore = FirebaseFirestore.getInstance()
    private var isRecording = false
    private val recordedData = mutableListOf<Map<String, Any>>()
    private var userId: String = ""
    private var recordSessionId: String = ""


    // Handler to update the time every second
    private val handler = Handler()
    private val updateTimeRunnable = object : Runnable {
        override fun run() {
            // Update time
            val currentTime = System.currentTimeMillis()
            val dateFormat = SimpleDateFormat("dd MMM yyyy - HH:mm:ss", Locale.getDefault())
            val formattedDate = dateFormat.format(Date(currentTime))
            tvDatetime.text = "Waktu: $formattedDate"

            // Repeat this runnable every second
            handler.postDelayed(this, 100)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ppg)

        setupUI()

        bluetoothService = BluetoothService(bluetoothAdapter)
        bluetoothService?.onDataReceivedListener = this

        stopwatchTextView = findViewById(R.id.tv_stopwatch)

        // Start the time update
        handler.post(updateTimeRunnable)

        // Load saved recordings
        loadSavedRecordings()
    }

    private fun setupUI() {
        tvUsername = findViewById(R.id.tv_username)
        tvSpO2 = findViewById(R.id.tv_spo2)
        tvIr = findViewById(R.id.tv_ir)
        tvRed = findViewById(R.id.tv_red)
        tvBluetoothStatus = findViewById(R.id.tv_bluetooth_status)
        tvFingerStatus = findViewById(R.id.tv_finger_status)
        tvDatetime = findViewById(R.id.tv_datetime)
        btnConnectBluetooth = findViewById(R.id.btn_connect_bluetooth)
        btnDisconnectBluetooth = findViewById(R.id.btn_disconnect_bluetooth)
        btnStartRecord = findViewById(R.id.btn_start_record)
        btnStopRecord = findViewById(R.id.btn_stop_record)
        graphPpg = findViewById(R.id.graph_ppg)
        listViewRecords = findViewById(R.id.listView_records)
        btnReset = findViewById(R.id.btn_reset)


        val username = intent?.extras?.getString("USERNAME") ?: "default_user"
        userId = username.replace(" ", "_").lowercase()
        tvUsername.text = "Nama Pengguna: $username"

        btnConnectBluetooth.setOnClickListener { showBluetoothDeviceList() }
        btnDisconnectBluetooth.setOnClickListener { disconnectBluetooth() }
        btnDisconnectBluetooth.isEnabled = false

        btnStartRecord.setOnClickListener { startRecording() }
        btnStopRecord.setOnClickListener { stopRecording() }
        btnReset.setOnClickListener { resetAll()}

        btnStopRecord.visibility = Button.GONE

        graphPpg.addSeries(series)
        graphPpg.viewport.isXAxisBoundsManual = true
        graphPpg.viewport.setMinX(0.0)
        graphPpg.viewport.setMaxX(100.0)
        graphPpg.viewport.isScalable = true
        graphPpg.viewport.isScrollable = true
    }

    override fun onResume() {
        super.onResume()
        loadSavedRecordings() // Refresh daftar rekaman saat kembali ke activity ini
    }

    private var lastPpgWave: Int = 0
    override fun onHRSpO2Received(heartRate: Int, spO2: Int) {
        runOnUiThread {
            // 🔹 Batasi SpO₂ maksimal 99%
            val limitedSpO2 = if (spO2 > 99) 99 else spO2
            tvSpO2.text = "SpO2: $limitedSpO2 %"
            if (isRecording) {
                val data = hashMapOf(
                    "spo2" to limitedSpO2, // Simpan nilai SpO₂ yang sudah dibatasi
                    "ir" to tvIr.text.toString().replace("IR: ", "").toInt(),
                    "red" to tvRed.text.toString().replace("RED: ", "").toInt(),
                    "ppg_wave" to lastPpgWave, // Tambahkan PPG Wave terakhir
                    "timestamp" to tvDatetime.text.toString().replace("Waktu: ", "")
                )
                recordedData.add(data)
            }
        }
    }

    override fun onPPGDataReceived(ir: Int, red: Int, ppgWave: List<Int>) {
        runOnUiThread {
            tvIr.text = "IR: $ir"
            tvRed.text = "RED: $red"
            if (ir < 3000) {
                tvFingerStatus.text = "Harap tempelkan jari"
                tvFingerStatus.setTextColor(ContextCompat.getColor(this, R.color.red))
            } else {
                tvFingerStatus.text = "Proses..."
                tvFingerStatus.setTextColor(ContextCompat.getColor(this, R.color.teal_700))
            }
            if (ppgWave.isNotEmpty()) {
                lastPpgWave = ppgWave.last() // Simpan nilai terakhir dari gelombang PPG
                updateGraph(lastPpgWave) // Update grafik dengan nilai terbaru
            }

            // **Ambil nilai SpO2 dengan aman**
            val spo2Text = tvSpO2.text.toString().replace("SpO2: ", "").replace("%", "").trim()
            val spo2Value = spo2Text.toIntOrNull() ?: 0  // Pastikan tidak error jika null
            updateSpO2Status(spo2Value)

            // **Tambahkan penyimpanan ke Firestore sesuai IR & RED**
            if (isRecording) {
                val currentTimestamp = System.currentTimeMillis()
                val formattedTime = SimpleDateFormat("dd MMM yyyy - HH:mm:ss", Locale.getDefault()).format(Date(currentTimestamp))

                val data = hashMapOf(
                    "ir" to ir,
                    "red" to red,
                    "spo2" to spo2Value,  // Pastikan ini integer
                    "ppg_wave" to lastPpgWave, // Tambahkan nilai terakhir PPG Wave
                    "timestamp" to formattedTime
                )
                recordedData.add(data) // Tambahkan ke list lokal

                // **Langsung simpan ke Firestore**
                saveDataToFirestore(ir, red, spo2Value, lastPpgWave)
            }

        }
    }



    private fun updateSpO2Status(spo2: Int) {
        val statusTextView = findViewById<TextView>(R.id.statusSpO2)
        when {
            spo2 >= 95 -> {
                statusTextView.text = "Status : Normal\uD83C\uDF3F"
                statusTextView.setTextColor(Color.GREEN)
            }
            spo2 in 90..94 -> {
                statusTextView.text = "Status : Waspada: Risiko Hipoksia ⚠\uFE0F"
                statusTextView.setTextColor(Color.parseColor("#FFA500")) // Oranye
            }
            spo2 in 70..89 -> {
                statusTextView.text = "Status : PERINGATAN: Hipoksia Akut ❗"
                statusTextView.setTextColor(Color.RED)
            }
            else -> { // Kalau SpO2 di bawah 70
                statusTextView.text = "Status: Proses⏳"
                statusTextView.setTextColor(Color.GRAY) // Bisa pakai warna abu-abu buat indikasi error
            }
        }
    }

    private var indexCounter = 0  // Inisialisasi index
    private fun saveDataToFirestore(ir: Int, red: Int, spo2Value: Int, lastPpgWave: Int) {
        val timestampNow = System.currentTimeMillis() // Gunakan timestamp aktual

        val data = hashMapOf(
            "index" to indexCounter,  // Set index berdasarkan urutan
            "ir" to ir,
            "ppg_wave" to lastPpgWave,
            "red" to red,
            "spo2" to spo2Value,
            "timestamp" to timestampNow
        )

        val db = FirebaseFirestore.getInstance()
        db.collection("rekaman_data")
            .document(indexCounter.toString())  // Gunakan index sebagai ID dokumen
            .set(data)
            .addOnSuccessListener {
                Log.d("Firestore", "Data berhasil disimpan dengan index: $indexCounter")
                indexCounter++  // Tambah index setiap kali data disimpan
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Gagal menyimpan data", e)
            }
    }

    private val stopwatchRunnable = object : Runnable {
        override fun run() {
            val elapsedMillis = System.currentTimeMillis() - stopwatchStartTime
            val seconds = (elapsedMillis / 1000) % 60
            val minutes = (elapsedMillis / 1000) / 60
            stopwatchTextView.text = String.format("%02d:%02d", minutes, seconds)

            if (isStopwatchRunning) {
                stopwatchHandler.postDelayed(this, 1000)
            }
        }
    }

    private fun startStopwatch() {
        stopwatchStartTime = System.currentTimeMillis()
        isStopwatchRunning = true
        stopwatchHandler.post(stopwatchRunnable)
    }

    private fun stopStopwatch() {
        isStopwatchRunning = false
        stopwatchHandler.removeCallbacks(stopwatchRunnable)
        stopwatchTextView.text = "00:00"
    }

    private fun resetAll() {
        tvSpO2.text = "SpO2: - %"
        tvIr.text = "IR: -"
        tvRed.text = "RED: -"
        tvFingerStatus.text = "Status: -"
        tvFingerStatus.setTextColor(Color.GRAY)
        findViewById<TextView>(R.id.statusSpO2).apply {
            text = "Status: -"
            setTextColor(Color.GRAY)
        }
        series.resetData(arrayOf()) // Reset grafik
    }

    private fun startRecording() {
        if (!isRecording) {
            isRecording = true
            recordedData.clear()
            indexCounter = 0

            val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
            recordSessionId = dateFormat.format(Date())

            btnStartRecord.visibility = Button.GONE
            btnStopRecord.visibility = Button.VISIBLE
            showToast("Rekaman dimulai")

            startStopwatch() // ▶️ Start stopwatch di sini
        }
    }

    private fun stopRecording() {
        if (isRecording) {
            isRecording = false
            if (recordedData.isNotEmpty()) {
                saveRecordingToFirestore()
            }
            btnStartRecord.visibility = Button.VISIBLE
            btnStopRecord.visibility = Button.GONE
            showToast("Rekaman berhenti & tersimpan")

            stopStopwatch() // ⏹ Stop stopwatch di sini
        }
    }

    private fun saveRecordingToFirestore() {
        if (recordedData.isNotEmpty() && recordSessionId.isNotEmpty() && userId.isNotEmpty()) {
            val recordsCollectionRef = firestore.collection("PPG_Monitoring")
                .document(userId)
                .collection("records")

            val sessionDocRef = recordsCollectionRef.document(recordSessionId)

            val sessionMetadata = hashMapOf(
                "timestamp" to System.currentTimeMillis()
            )

            val batch = firestore.batch()
            batch.set(sessionDocRef, sessionMetadata)

            var counter = 1  // Pakai index untuk urutan yang benar
            for (data in recordedData) {
                val docRef = sessionDocRef.collection("data").document()  // Pakai Firestore auto-ID

                val newData = data.toMutableMap()
                newData["index"] = counter  // Tambahkan field "index"
                newData["timestamp"] = System.currentTimeMillis()

                batch.set(docRef, newData)
                counter++
            }

            batch.commit().addOnSuccessListener {
                showToast("Rekaman tersimpan.")
                loadSavedRecordings() // ⬅ Tambahkan ini
            }.addOnFailureListener {
                showToast("Gagal menyimpan rekaman.")
                Log.e("Firestore", "Error saat menyimpan rekaman", it)
            }

        } else {
            Log.e("Firestore", "Gagal menyimpan rekaman: Data kosong atau ID tidak valid")
        }
    }

    private fun loadSavedRecordings() {
        val recordRef = firestore.collection("PPG_Monitoring")
            .document(userId)
            .collection("records")

        Log.d("Firestore", "Fetching records for user: $userId")


        recordRef.get().addOnSuccessListener { documents ->
            val sessionList = mutableListOf<Map<String, String>>() // List berisi map (id, timestamp)

            for (document in documents) {
                val sessionId = document.id
                val timestamp = document.getLong("timestamp") ?: 0
                val formattedTime = if (timestamp > 0) {
                    SimpleDateFormat("dd MMM yyyy - HH:mm", Locale.getDefault()).format(Date(timestamp))
                } else {
                    "Tanggal Tidak Diketahui"
                }
                sessionList.add(mapOf("sessionId" to sessionId, "timestamp" to formattedTime))
                Log.d("Firestore", "Found record: $sessionId at $formattedTime")
            }

            if (sessionList.isEmpty()) {
                showToast("Belum ada rekaman.")
                listViewRecords.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, listOf("Tidak ada rekaman"))
            } else {
                val adapter = SimpleAdapter(
                    this,
                    sessionList,
                    android.R.layout.simple_list_item_2,
                    arrayOf("sessionId", "timestamp"),
                    intArrayOf(android.R.id.text1, android.R.id.text2)
                )
                listViewRecords.adapter = adapter

                listViewRecords.setOnItemClickListener { _, _, position, _ ->
                    val selectedSessionId = sessionList[position]["sessionId"] ?: return@setOnItemClickListener
                    val intent = Intent(this, RekamanppgActivity::class.java)
                    intent.putExtra("USERNAME", userId)  // Pastikan username dikirim
                    intent.putExtra("sessionId", selectedSessionId)
                    Log.d("Firestore", "Opening session: $selectedSessionId for user: $userId")
                    startActivity(intent)
                }

                // **Fitur hapus rekaman dengan konfirmasi**
                listViewRecords.setOnItemLongClickListener { _, _, position, _ ->
                    val selectedSessionId = sessionList[position]["sessionId"] ?: return@setOnItemLongClickListener true
                    showDeleteConfirmationDialog(selectedSessionId)
                    true
                }
            }
        }.addOnFailureListener {
            showToast("Gagal mengambil daftar rekaman.")
            Log.e("Firestore", "Error fetching records", it)
        }

    }



    private fun showDeleteConfirmationDialog(sessionId: String) {
        AlertDialog.Builder(this)
            .setTitle("Hapus Rekaman")
            .setMessage("Apakah Anda yakin ingin menghapus rekaman ini?")
            .setPositiveButton("Hapus") { _, _ ->
                deleteRecording(sessionId) // Panggil fungsi hapus
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun deleteRecording(sessionId: String) {
        val recordRef = firestore.collection("PPG_Monitoring")
            .document(userId)
            .collection("records")
            .document(sessionId)

        // 🔍 Cek apakah data ada sebelum dihapus
        recordRef.get().addOnSuccessListener { document ->
            if (document.exists()) {
                Log.d("Firestore", "Data ditemukan sebelum dihapus: $sessionId")

                // 🗑 Hapus dokumen langsung tanpa transaction
                recordRef.delete()
                    .addOnSuccessListener {
                        Log.d("Firestore", "✅ Data berhasil dihapus dari Firestore: $sessionId")
                        showToast("Rekaman berhasil dihapus.")

                        // 🔄 Pastikan daftar rekaman diperbarui
                        loadSavedRecordings()
                    }
                    .addOnFailureListener { e ->
                        Log.e("Firestore", "❌ Gagal menghapus rekaman", e)
                        showToast("Gagal menghapus rekaman.")
                    }
            } else {
                Log.d("Firestore", "⚠️ Data tidak ditemukan sebelum dihapus.")
                showToast("Rekaman tidak ditemukan.")
            }
        }.addOnFailureListener {
            Log.e("Firestore", "❌ Gagal mendapatkan data rekaman", it)
        }
    }


    private val ppgDataList = mutableListOf<Float>()
    private val windowSize = 6
    private fun updateGraph(ppgValue: Int) {
        lastXValue += 1
        //series.appendData(DataPoint(lastXValue, ppgValue.toDouble()), true, 50)

        // Tambahkan nilai baru ke dalam list
        ppgDataList.add(ppgValue.toFloat())

        // Pastikan ukuran list tidak lebih dari windowSize
        if (ppgDataList.size > windowSize) {
            ppgDataList.removeAt(0) // Hapus elemen paling lama
        }

        // Hitung Moving Average
        val smoothedValue = ppgDataList.average().toFloat()

        // Tambahkan ke grafik dengan nilai yang sudah dihaluskan
        series.appendData(DataPoint(lastXValue, smoothedValue.toDouble()), true, 100)
    }

    private fun connectToDevice(device: BluetoothDevice) {
        val success = bluetoothService?.connectToDevice(device) ?: false
        if (success) {
            tvBluetoothStatus.text = "Bluetooth: Terhubung ke ${device.name}"
            showToast("Terhubung ke ${device.name}")
            btnDisconnectBluetooth.isEnabled = true
            bluetoothService?.startListening()
        } else {
            showToast("Gagal terhubung ke ${device.name}")
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun requestBluetoothPermission() {
        requestPermissions(
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
            REQUEST_BLUETOOTH_PERMISSION
        )
    }

    private fun showBluetoothDeviceList() {
        if (bluetoothAdapter == null) {
            showToast("Bluetooth tidak tersedia di perangkat ini")
            return
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestBluetoothPermission()
            return
        }

        val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter?.bondedDevices
        if (pairedDevices.isNullOrEmpty()) {
            showToast("Tidak ada perangkat Bluetooth yang terhubung")
            return
        }

        val deviceList = pairedDevices.map { it.name ?: "Unknown" }.toTypedArray()
        val deviceArray = pairedDevices.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Pilih Perangkat Bluetooth")
            .setItems(deviceList) { _, which ->
                connectToDevice(deviceArray[which])
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun disconnectBluetooth() {
        bluetoothService?.closeConnection()
        tvBluetoothStatus.text = "Bluetooth: Tidak terhubung"
        btnDisconnectBluetooth.isEnabled = false
        showToast("Terputus dari perangkat Bluetooth")
    }

    companion object {
        private const val REQUEST_BLUETOOTH_PERMISSION = 1
    }
}