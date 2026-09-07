package com.quzhi.lite.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class SavedDevice(
    val snCode: String,
    val name: String,
    val address: String = "",
    val normalizedAddress: String = "",
    val buildingName: String = "",
    val floorName: String = "",
    val roomName: String = "",
    val projectName: String = "",
    val isBle: Boolean = true,
    val rssi: Int = 0,
) {
    val identity: String
        get() = normalizedAddress.ifBlank { snCode }.uppercase()
}

class DeviceStore(
    context: Context,
    private val gson: Gson = Gson(),
) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val listType = object : TypeToken<List<SavedDevice>>() {}.type

    fun load(session: UserSession): List<SavedDevice> {
        val json = preferences.getString(key(session), null) ?: return emptyList()
        return gson.fromJson<List<SavedDevice>>(json, listType)
    }

    fun upsert(session: UserSession, device: SavedDevice) {
        val devices = load(session).toMutableList()
        val existingIndex = devices.indexOfFirst { it.matches(device) }
        if (existingIndex >= 0) {
            devices[existingIndex] = devices[existingIndex].merge(device)
        } else {
            devices.add(0, device)
        }
        save(session, devices)
    }

    fun delete(session: UserSession, device: SavedDevice) {
        val devices = load(session).filterNot { it.matches(device) }
        save(session, devices)
    }

    private fun save(session: UserSession, devices: List<SavedDevice>) {
        preferences.edit()
            .putString(key(session), gson.toJson(devices, listType))
            .apply()
    }

    private fun key(session: UserSession): String {
        return "devices_${session.accountId}_${session.projectId}_${session.telephone}"
    }

    private fun SavedDevice.matches(other: SavedDevice): Boolean {
        if (snCode.isNotBlank() && other.snCode.isNotBlank() &&
            snCode.equals(other.snCode, ignoreCase = true)
        ) {
            return true
        }
        return normalizedAddress.isNotBlank() && other.normalizedAddress.isNotBlank() &&
            normalizedAddress.equals(other.normalizedAddress, ignoreCase = true)
    }

    private fun SavedDevice.merge(newer: SavedDevice): SavedDevice {
        return copy(
            snCode = newer.snCode.ifBlank { snCode },
            name = newer.name.ifBlank { name },
            address = newer.address.ifBlank { address },
            normalizedAddress = newer.normalizedAddress.ifBlank { normalizedAddress },
            buildingName = newer.buildingName.ifBlank { buildingName },
            floorName = newer.floorName.ifBlank { floorName },
            roomName = newer.roomName.ifBlank { roomName },
            projectName = newer.projectName.ifBlank { projectName },
            isBle = newer.isBle,
            rssi = if (newer.rssi != 0) newer.rssi else rssi,
        )
    }

    private companion object {
        const val PREFERENCES = "quzhi_lite_devices"
    }
}
