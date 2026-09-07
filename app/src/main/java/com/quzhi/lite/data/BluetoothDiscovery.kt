package com.quzhi.lite.data

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat

class BluetoothDiscovery(context: Context) {
    private val appContext = context.applicationContext
    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val handler = Handler(Looper.getMainLooper())

    private var receiverRegistered = false
    private var bleScanning = false
    private var classicScanning = false
    private var deviceListener: ((BluetoothDeviceCandidate) -> Unit)? = null
    private var finishedListener: (() -> Unit)? = null
    private var errorListener: ((String) -> Unit)? = null
    private val discoveredAddresses = mutableSetOf<String>()

    private val stopRunnable = Runnable {
        val listener = finishedListener
        stop()
        listener?.invoke()
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = preferredBluetoothName(
                scanRecordName = result.scanRecord?.deviceName,
                deviceName = result.device.safeName(),
            )
            publish(result.device, name, result.rssi, isBle = true)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { result ->
                val name = preferredBluetoothName(
                    scanRecordName = result.scanRecord?.deviceName,
                    deviceName = result.device.safeName(),
                )
                publish(result.device, name, result.rssi, isBle = true)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            errorListener?.invoke("BLE 扫描失败（$errorCode）")
        }
    }

    private val classicReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != BluetoothDevice.ACTION_FOUND) {
                return
            }
            val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(
                    BluetoothDevice.EXTRA_DEVICE,
                    BluetoothDevice::class.java,
                )
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            } ?: return
            val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, 0).toInt()
            publish(device, device.safeName(), rssi, isBle = false)
        }
    }

    @SuppressLint("MissingPermission")
    fun start(
        onDevice: (BluetoothDeviceCandidate) -> Unit,
        onFinished: () -> Unit,
        onError: (String) -> Unit,
    ): Boolean {
        stop()
        deviceListener = onDevice
        finishedListener = onFinished
        errorListener = onError

        val bluetoothAdapter = adapter
        if (bluetoothAdapter == null) {
            onError("当前设备不支持蓝牙")
            clearListeners()
            return false
        }
        if (!bluetoothAdapter.isEnabled) {
            onError("请先开启系统蓝牙")
            clearListeners()
            return false
        }

        try {
            registerClassicReceiver()
            bluetoothAdapter.cancelDiscovery()

            bluetoothAdapter.bluetoothLeScanner?.let { scanner ->
                scanner.startScan(
                    null,
                    ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .build(),
                    scanCallback,
                )
                bleScanning = true
            }

            classicScanning = bluetoothAdapter.startDiscovery()
            if (!bleScanning && !classicScanning) {
                stop()
                onError("蓝牙扫描未启动")
                return false
            }

            handler.postDelayed(stopRunnable, SCAN_DURATION_MILLIS)
            return true
        } catch (securityException: SecurityException) {
            stop()
            onError("蓝牙权限不足，请允许附近设备权限")
            return false
        }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        handler.removeCallbacks(stopRunnable)
        adapter?.let { bluetoothAdapter ->
            if (bleScanning) {
                bluetoothAdapter.bluetoothLeScanner?.stopScan(scanCallback)
            }
            if (classicScanning) {
                bluetoothAdapter.cancelDiscovery()
            }
        }
        bleScanning = false
        classicScanning = false
        discoveredAddresses.clear()
        unregisterClassicReceiver()
        clearListeners()
    }

    @SuppressLint("MissingPermission")
    fun isBluetoothEnabled(): Boolean {
        return adapter?.isEnabled == true
    }

    private fun publish(
        device: BluetoothDevice,
        name: String?,
        rssi: Int,
        isBle: Boolean,
    ) {
        if (!hasUsableDiscoverySignal(rssi) || !isSupportedBluetoothName(name)) {
            return
        }
        val deviceName = name ?: return
        val address = device.safeAddress() ?: return
        val normalizedAddress = normalizeBluetoothAddress(address)
        if (!discoveredAddresses.add(normalizedAddress)) {
            return
        }
        deviceListener?.invoke(
            BluetoothDeviceCandidate(
                name = deviceName,
                address = address,
                normalizedAddress = normalizedAddress,
                rssi = rssi,
                isBle = isBle,
                snCode = extractSnCode(deviceName)
                    ?: snCodeFromBluetoothAddress(normalizedAddress),
            ),
        )
    }

    private fun registerClassicReceiver() {
        if (receiverRegistered) {
            return
        }
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(
                classicReceiver,
                filter,
                Context.RECEIVER_EXPORTED,
            )
        } else {
            @Suppress("DEPRECATION")
            appContext.registerReceiver(classicReceiver, filter)
        }
        receiverRegistered = true
    }

    private fun unregisterClassicReceiver() {
        if (!receiverRegistered) {
            return
        }
        appContext.unregisterReceiver(classicReceiver)
        receiverRegistered = false
    }

    private fun clearListeners() {
        deviceListener = null
        finishedListener = null
        errorListener = null
    }

    @SuppressLint("MissingPermission")
    private fun BluetoothDevice.safeName(): String? {
        return try {
            name
        } catch (_: SecurityException) {
            null
        }
    }

    @SuppressLint("MissingPermission")
    private fun BluetoothDevice.safeAddress(): String? {
        return try {
            address
        } catch (_: SecurityException) {
            null
        }
    }

    companion object {
        const val SCAN_DURATION_MILLIS = 30_000L

        fun requiredPermissions(): Array<String> {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                )
            } else {
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        fun hasRequiredPermissions(context: Context): Boolean {
            return requiredPermissions().all { permission ->
                ContextCompat.checkSelfPermission(
                    context,
                    permission,
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
    }
}
