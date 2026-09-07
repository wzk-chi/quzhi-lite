package com.quzhi.lite.data

import java.util.Locale

const val MIN_DISCOVERY_RSSI = -90

data class BluetoothDeviceCandidate(
    val name: String,
    val address: String,
    val normalizedAddress: String,
    val rssi: Int,
    val isBle: Boolean,
    val snCode: String?,
)

fun isSupportedBluetoothName(name: String?): Boolean {
    return name?.startsWith("KLCXKJ") == true
}

fun preferredBluetoothName(scanRecordName: String?, deviceName: String?): String? {
    return sequenceOf(deviceName, scanRecordName)
        .filterNotNull()
        .firstOrNull { extractSnCode(it) != null }
        ?: deviceName
        ?: scanRecordName
}

fun hasUsableDiscoverySignal(rssi: Int): Boolean {
    return rssi != 0 && rssi >= MIN_DISCOVERY_RSSI
}

fun upsertBluetoothDevice(
    devices: List<BluetoothDeviceCandidate>,
    device: BluetoothDeviceCandidate,
): List<BluetoothDeviceCandidate> {
    val index = devices.indexOfFirst {
        it.normalizedAddress == device.normalizedAddress
    }
    if (index < 0) {
        return devices + device
    }

    return devices.toMutableList().apply {
        set(index, device)
    }
}

fun normalizeBluetoothAddress(address: String): String {
    val normalized = address.trim().uppercase(Locale.US)
    return if (normalized.startsWith("C0")) {
        "00${normalized.drop(2)}"
    } else {
        normalized
    }
}

fun extractSnCode(deviceName: String): String? {
    val candidate = deviceName.substringAfterLast(',', missingDelimiterValue = "").trim()
    return candidate
        .takeIf { it.length == BLUETOOTH_ADDRESS_HEX_LENGTH && it.all(::isHexDigit) }
        ?.uppercase(Locale.US)
}

fun snCodeFromBluetoothAddress(address: String): String {
    return normalizeBluetoothAddress(address)
        .filterNot { it == ':' }
        .uppercase(Locale.US)
}

private fun isHexDigit(character: Char): Boolean {
    return character in '0'..'9' || character in 'A'..'F' || character in 'a'..'f'
}

private const val BLUETOOTH_ADDRESS_HEX_LENGTH = 12
