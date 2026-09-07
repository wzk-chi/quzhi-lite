package com.quzhi.lite.data

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.Locale

data class DeviceLocation(
    val buildingName: String?,
    val floorName: String?,
    val roomName: String?,
    val projectName: String?,
    val deviceName: String?,
    val snCode: String?,
)

fun DeviceLocation.hasAddress(): Boolean {
    return buildingName?.isNotBlank() == true ||
        floorName?.isNotBlank() == true ||
        roomName?.isNotBlank() == true
}

class DeviceInfoApi(
    private val client: OkHttpClient = OkHttpClient(),
    private val gson: Gson = Gson(),
) {
    suspend fun queryByMac(session: UserSession, macAddress: String): DeviceLocation? =
        withContext(Dispatchers.IO) {
            val loginCode = session.v3LoginCode.ifBlank { session.loginCode }
            val lookupValue = normalizeLookupValue(macAddress)
            val url = (BASE_URL + "device/info/mac").toHttpUrl().newBuilder().apply {
                addQueryParameter("projectId", session.projectId.toString())
                addQueryParameter("isNew", "1")
                addQueryParameter("phoneSystem", "android")
                addQueryParameter("version", APP_VERSION)
                addQueryParameter("loginCode", loginCode)
                addQueryParameter("macAddress", lookupValue)
                if (session.accountId != 0L) {
                    addQueryParameter("accountId", session.accountId.toString())
                }
                addQueryParameter("telephone", session.telephone)
                addQueryParameter("telPhone", session.telephone)
                addQueryParameter("userId", session.userId.toString())
            }.build()
            val requestBuilder = Request.Builder()
                .url(url)
                .get()
            if (session.projectId != 0) {
                requestBuilder.header("Config-Project", session.projectId.toString())
                requestBuilder.header("Config-Keys", CONFIG_KEYS)
            }
            val request = requestBuilder.build()

            val response = execute(request)
            if (response.errorCode != 0) {
                throw ApiException(response.message ?: "设备信息查询失败（${response.errorCode}）")
            }
            response.data?.takeUnless { it.isJsonNull }?.let { data ->
                gson.fromJson(data, DeviceInfoDto::class.java).toLocation()
            }
        }

    private fun execute(request: Request): ApiResponse {
        client.newCall(request).execute().use { response: Response ->
            val body = response.body?.string().orEmpty()
            if (response.code != 200) {
                throw ApiException("HTTP ${response.code}: ${response.message}")
            }
            if (body.isBlank()) {
                throw ApiException("服务器返回为空")
            }
            return gson.fromJson(body, ApiResponse::class.java)
        }
    }

    private fun normalizeLookupValue(value: String): String {
        val trimmed = value.trim()
        return if (trimmed.contains(':')) {
            normalizeBluetoothAddress(trimmed)
        } else {
            trimmed.uppercase(Locale.US)
        }
    }

    private data class ApiResponse(
        val errorCode: Int = -1,
        @SerializedName(value = "message", alternate = ["errorMessage"])
        val message: String? = null,
        val data: JsonElement? = null,
    )

    private data class DeviceInfoDto(
        @SerializedName(value = "LDName", alternate = ["buildingName", "building"])
        val buildingName: String? = null,
        @SerializedName(value = "LCName", alternate = ["floorName", "floor"])
        val floorName: String? = null,
        @SerializedName(value = "FJName", alternate = ["roomName", "room"])
        val roomName: String? = null,
        @SerializedName(value = "PrjName", alternate = ["projectName"])
        val projectName: String? = null,
        @SerializedName(value = "DevName", alternate = ["deviceName"])
        val deviceName: String? = null,
        val snCode: String? = null,
    )

    private fun DeviceInfoDto.toLocation(): DeviceLocation {
        return DeviceLocation(
            buildingName = buildingName,
            floorName = floorName,
            roomName = roomName,
            projectName = projectName,
            deviceName = deviceName,
            snCode = snCode,
        )
    }

    private companion object {
        const val BASE_URL = "https://v3-api.china-qzxy.cn/"
        const val APP_VERSION = "6.5.28"
        const val CONFIG_KEYS =
            "module_list,advertise_type,question_list,service_phone_list,banner_list_app,activity_list_app,aliCard_popup_config"
    }
}
