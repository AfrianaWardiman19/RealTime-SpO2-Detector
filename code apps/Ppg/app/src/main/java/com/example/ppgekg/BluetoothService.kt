package com.example.ppgekg

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import org.json.JSONException
import org.json.JSONObject
import java.io.*
import java.util.UUID

class BluetoothService(private val bluetoothAdapter: BluetoothAdapter?) {

    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var isConnected = false
    private var isListening = false
    private var readThread: Thread? = null
    var onDataReceivedListener: OnDataReceivedListener? = null  // Listener untuk mengirim data ke UI

    companion object {
        private const val TAG = "BluetoothService"
        private val DEVICE_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // UUID standar SPP
    }

    /** 🔍 Mencari perangkat berdasarkan nama Bluetooth */
    @SuppressLint("MissingPermission")
    fun findDevice(deviceName: String): BluetoothDevice? {
        bluetoothAdapter?.bondedDevices?.forEach { device ->
            if (device.name == deviceName) {
                Log.d(TAG, "Perangkat ditemukan: ${device.name} - ${device.address}")
                return device
            }
        }
        return null
    }

    /** 🔗 Menghubungkan ke perangkat Bluetooth */
    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice): Boolean {
        if (isConnected) {
            Log.d(TAG, "🔵 Sudah terhubung ke perangkat")
            return true
        }

        return try {
            bluetoothSocket = device.createRfcommSocketToServiceRecord(DEVICE_UUID)
            bluetoothSocket?.connect()
            outputStream = bluetoothSocket?.outputStream
            isConnected = true
            Log.d(TAG, "✅ Bluetooth Terhubung ke ${device.name}")
            true
        } catch (e: IOException) {
            Log.e(TAG, "❌ Gagal terhubung: ${e.message}")
            closeConnection()
            false
        }
    }

    /** 🎧 Memulai thread untuk membaca data terus-menerus */
    fun startListening() {
        if (!isConnected || bluetoothSocket?.inputStream == null) return

        isListening = true
        readThread = Thread {
            try {
                val reader = BufferedReader(InputStreamReader(bluetoothSocket!!.inputStream))
                while (isListening) {
                    val receivedData = reader.readLine() ?: break // Baca per baris
                    Log.d(TAG, "📥 Data diterima: $receivedData")
                    processReceivedData(receivedData)
                }
            } catch (e: IOException) {
                Log.e(TAG, "❌ Gagal membaca data: ${e.message}")
            }
        }
        readThread?.start()
    }

    /** 🛑 Menghentikan thread pembaca data */
    fun stopListening() {
        isListening = false
        readThread?.interrupt()
        readThread = null
    }

    /** 📤 Mengirim data ke perangkat Bluetooth */
    fun sendData(data: String): Boolean {
        if (!isConnected) return false

        return try {
            outputStream?.write("$data\n".toByteArray()) // Kirim data dengan newline
            outputStream?.flush()
            Log.d(TAG, "📤 Data terkirim: $data")
            true
        } catch (e: IOException) {
            Log.e(TAG, "❌ Gagal mengirim data: ${e.message}")
            false
        }
    }

    /** 🔌 Menutup koneksi Bluetooth */
    fun closeConnection() {
        try {
            stopListening()
            outputStream?.close()
            bluetoothSocket?.close()
            isConnected = false
            Log.d(TAG, "🔴 Bluetooth Terputus")
        } catch (e: IOException) {
            Log.e(TAG, "❌ Gagal menutup koneksi: ${e.message}")
        } finally {
            outputStream = null
            bluetoothSocket = null
        }
    }

    /** 🟢 Mengecek apakah Bluetooth masih terhubung */
    fun isConnected(): Boolean {
        return isConnected
    }

    /** 📌 Parsing dan memproses data yang diterima */
    private fun processReceivedData(data: String) {
        try {
            val jsonObject = JSONObject(data)

            if (jsonObject.has("IR") && jsonObject.has("RED") && jsonObject.has("PPGWave")) {
                val irValue = jsonObject.getInt("IR")
                val redValue = jsonObject.getInt("RED")
                val ppgWaveArray = jsonObject.getJSONArray("PPGWave")

                // Ubah JSONArray jadi List<Int> untuk grafik
                val ppgWaveList = mutableListOf<Int>()
                for (i in 0 until ppgWaveArray.length()) {
                    ppgWaveList.add(ppgWaveArray.getInt(i))
                }

                // Kirim data ke UI untuk ditampilkan
                onDataReceivedListener?.onPPGDataReceived(irValue, redValue, ppgWaveList)
            }
            // Parsing data SpO2 (HR bisa opsional/dummy)
            else if (jsonObject.has("spO2")) {
                val spO2 = jsonObject.getInt("spO2")
                val heartRate = if (jsonObject.has("heartRate")) jsonObject.getInt("heartRate") else -1

                // Kirim ke UI
                onDataReceivedListener?.onHRSpO2Received(heartRate, spO2)
            }
        } catch (e: JSONException) {
            Log.e(TAG, "❌ Error parsing JSON: ${e.message}")
        }
    }
}

/** 🔹 Interface untuk mengirim data ke UI */
interface OnDataReceivedListener {
    fun onPPGDataReceived(ir: Int, red: Int, ppgWave: List<Int>)
    fun onHRSpO2Received(heartRate: Int, spO2: Int)
}
