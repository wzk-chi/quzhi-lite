package com.quzhi.lite.data

import android.os.SystemClock
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

data class WaterOrder(
    val orderNo: String,
)

data class WaterStopResult(
    val consumedMilliUnits: Long?,
    val orderAlreadyClosed: Boolean = false,
)

class HotWaterApi(
    private val client: OkHttpClient = OkHttpClient(),
    private val gson: Gson = Gson(),
    private val mqttClient: HotWaterMqttClient = HotWaterMqttClient(gson),
    private val resultDelayMillis: Long = RESULT_DELAY_MILLIS,
) {
    suspend fun start(session: UserSession, snCode: String): WaterOrder = withContext(Dispatchers.IO) {
        val requestStartedAtMillis = SystemClock.elapsedRealtime()
        Log.d(TAG, "start begin snCode=$snCode")
        mqttClient.connectInBackground(session)
        val operationStartedAtMillis = System.currentTimeMillis()
        val response = postForm(
            session = session,
            path = "order/tcpDevice/downRate/rateOrder",
            values = mapOf(
                "snCode" to snCode,
                "xfModel" to "0",
            ),
        )
        Log.d(
            TAG,
            "start command response errorCode=${response.errorCode} " +
                "elapsed=${SystemClock.elapsedRealtime() - requestStartedAtMillis}ms",
        )

        when (response.errorCode) {
            0 -> {
                Log.d(TAG, "start waiting MQTT result timeout=${resultDelayMillis}ms")
                val mqttResult = mqttClient.awaitStart(
                    snCode = snCode,
                    operationStartedAtMillis = operationStartedAtMillis,
                    timeoutMillis = resultDelayMillis,
                )
                if (mqttResult != null) {
                    Log.d(
                        TAG,
                        "start MQTT result received result=${mqttResult.result} " +
                            "elapsed=${SystemClock.elapsedRealtime() - requestStartedAtMillis}ms",
                    )
                    parseMqttStartResult(session, mqttResult)
                } else {
                    Log.w(
                        TAG,
                        "start MQTT result timeout; fallback to HTTP query " +
                            "elapsed=${SystemClock.elapsedRealtime() - requestStartedAtMillis}ms",
                    )
                    queryStart(session, snCode)
                }
            }

            DEVICE_ALREADY_IN_USE_ERROR_CODE -> parseOwnedOrder(
                data = response.data,
                missingDataMessage = "设备正在使用中",
            )

            else -> throw ApiException(startErrorMessage(response))
        }
    }

    suspend fun stop(session: UserSession, snCode: String, orderNo: String): WaterStopResult =
        withContext(Dispatchers.IO) {
            mqttClient.connectInBackground(session)
            val operationStartedAtMillis = System.currentTimeMillis()
            val closeResponse = postForm(
                session = session,
                path = "order/tcpDevice/closeOrder",
                values = mapOf(
                    "snCode" to snCode,
                    "orderNo" to orderNo,
                ),
            )
            if (closeResponse.errorCode == ORDER_ALREADY_CLOSED_ERROR_CODE) {
                return@withContext WaterStopResult(
                    consumedMilliUnits = null,
                    orderAlreadyClosed = true,
                )
            }
            requireStopSuccess(closeResponse, "结束热水失败")

            val mqttCloseResult = mqttClient.awaitClose(
                snCode = snCode,
                operationStartedAtMillis = operationStartedAtMillis,
                timeoutMillis = resultDelayMillis,
            )

            if (mqttCloseResult != null) {
                when (mqttCloseResult.result) {
                    CLOSE_SUCCESS_RESULT -> {
                        val mqttConsumeResult = mqttClient.awaitConsume(
                            operationStartedAtMillis = mqttCloseResult.receivedAtMillis,
                            timeoutMillis = resultDelayMillis,
                        )
                        if (mqttConsumeResult != null) {
                            return@withContext WaterStopResult(
                                consumedMilliUnits = mqttConsumeResult.consumedMilliUnits(),
                            )
                        }
                    }

                    CLOSE_ALREADY_CLOSED_RESULT -> {
                        return@withContext WaterStopResult(
                            consumedMilliUnits = null,
                            orderAlreadyClosed = true,
                        )
                    }

                    CLOSE_ORDER_NUMBER_ERROR_RESULT -> {
                        throw ApiException("订单号错误，请重试")
                    }

                    else -> throw ApiException("结束失败，请重试")
                }
            } else {
                val closeResult = postForm(
                    session = session,
                    path = "order/tcpDevice/closeOrder/result/query",
                    values = mapOf(
                        "snCode" to snCode,
                        "orderNo" to orderNo,
                    ),
                )
                if (closeResult.errorCode == ORDER_ALREADY_CLOSED_ERROR_CODE) {
                    return@withContext WaterStopResult(
                        consumedMilliUnits = null,
                        orderAlreadyClosed = true,
                    )
                }
                requireStopSuccess(closeResult, "结束结果未确认")
                delay(resultDelayMillis)
            }

            val consumeResult = postForm(
                session = session,
                path = "order/consumeOrder/result/query",
                values = mapOf("orderNo" to orderNo),
            )
            requireStopSuccess(consumeResult, "消费结果未确认")

            WaterStopResult(
                consumedMilliUnits = consumeResult.data
                    ?.asJsonObjectOrNull()
                    ?.consumedMilliUnits(),
            )
    }

    private suspend fun queryStart(session: UserSession, snCode: String): WaterOrder {
        val queryStartedAtMillis = SystemClock.elapsedRealtime()
        Log.d(TAG, "start HTTP result query begin snCode=$snCode")
        val response = postForm(
            session = session,
            path = "order/tcpDevice/query/downRateResult",
            values = mapOf(
                "snCode" to snCode,
                "xfModel" to "0",
            ),
        )
        Log.d(
            TAG,
            "start HTTP result query response errorCode=${response.errorCode} " +
                "elapsed=${SystemClock.elapsedRealtime() - queryStartedAtMillis}ms",
        )
        requireStartSuccess(response)

        val data = response.data?.asJsonObjectOrNull()
        return when (data?.intValue("result")) {
            0, START_ALREADY_USING_RESULT -> parseOwnedOrder(response.data)
            START_INSUFFICIENT_BALANCE_RESULT -> throw ApiException("余额不足")
            else -> throw ApiException(response.message ?: "启动结果未确认")
        }
    }

    private fun parseMqttStartResult(
        session: UserSession,
        response: HotWaterMqttEvent.Start,
    ): WaterOrder {
        if (response.result == START_INSUFFICIENT_BALANCE_RESULT) {
            throw ApiException("余额不足")
        }
        if (response.result != 0 && response.result != START_ALREADY_USING_RESULT) {
            throw ApiException("启动失败，请重试")
        }
        if (session.accountId != response.accountId || response.accountType != ACCOUNT_TYPE_STUDENT) {
            throw ApiException("设备正在被其他账号使用")
        }
        return WaterOrder(
            orderNo = response.orderNo
                ?: throw ApiException("启动响应缺少订单号"),
        )
    }

    private fun parseOwnedOrder(
        data: JsonElement?,
        missingDataMessage: String = "启动响应缺少订单信息",
    ): WaterOrder {
        val objectData = data?.asJsonObjectOrNull()
            ?: throw ApiException(missingDataMessage)
        val isOwner = objectData.booleanValue("isOwner")
        if (isOwner == false) {
            throw ApiException("设备正在被其他账号使用")
        }

        val orderNo = objectData.stringValue("timeIds", "orderNo")
            ?: throw ApiException("启动响应缺少订单号")
        return WaterOrder(orderNo = orderNo)
    }

    private fun requireStartSuccess(response: ApiResponse) {
        if (response.errorCode != 0) {
            throw ApiException(startErrorMessage(response))
        }
    }

    private fun startErrorMessage(response: ApiResponse): String {
        return when (response.errorCode) {
            INSUFFICIENT_BALANCE_ERROR_CODE -> "余额不足"
            DEVICE_ALREADY_IN_USE_ERROR_CODE -> "设备正在使用中"
            CANNOT_INTERRUPT_ERROR_CODE -> "当前设备不能中断"
            else -> response.message ?: "启动热水失败（${response.errorCode}）"
        }
    }

    private fun requireStopSuccess(response: ApiResponse, message: String) {
        if (response.errorCode != 0) {
            throw ApiException(stopErrorMessage(response, message))
        }
    }

    private fun stopErrorMessage(response: ApiResponse, message: String): String {
        return when (response.errorCode) {
            ORDER_ALREADY_CLOSED_ERROR_CODE -> "设备已关闭，请前往账单查看"
            ORDER_NUMBER_ERROR_CODE -> "订单号错误，请重试"
            else -> response.message ?: "$message（${response.errorCode}）"
        }
    }

    private fun postForm(
        session: UserSession,
        path: String,
        values: Map<String, String>,
    ): ApiResponse {
        val parameters = commonParameters(session).toMutableMap().apply {
            putAll(values)
        }
        val form = FormBody.Builder().apply {
            parameters.forEach { (key, value) -> add(key, value) }
        }.build()
        val builder = Request.Builder()
            .url(BASE_URL + path)
            .post(form)
        if (session.projectId != 0) {
            builder.header("Config-Project", session.projectId.toString())
            builder.header("Config-Keys", CONFIG_KEYS)
        }

        return execute(builder.build())
    }

    private fun commonParameters(session: UserSession): Map<String, String> {
        val loginCode = session.v3LoginCode.ifBlank { session.loginCode }
        return buildMap {
            put("projectId", session.projectId.toString())
            if (session.accountId != 0L) put("accountId", session.accountId.toString())
            if (session.userId != 0L) put("userId", session.userId.toString())
            if (session.telephone.isNotBlank()) {
                put("telephone", session.telephone)
                put("telPhone", session.telephone)
            }
            put("loginCode", loginCode)
            put("phoneSystem", "android")
            put("version", APP_VERSION)
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

    private data class ApiResponse(
        val errorCode: Int = -1,
        @SerializedName(value = "message", alternate = ["errorMessage"])
        val message: String? = null,
        val data: JsonElement? = null,
    )

    private companion object {
        const val TAG = "HotWaterApi"
        const val BASE_URL = "https://v3-api.china-qzxy.cn/"
        const val APP_VERSION = "6.5.28"
        const val CONFIG_KEYS =
            "module_list,advertise_type,question_list,service_phone_list,banner_list_app,activity_list_app,aliCard_popup_config"
        const val INSUFFICIENT_BALANCE_ERROR_CODE = 204
        const val DEVICE_ALREADY_IN_USE_ERROR_CODE = 307
        const val CANNOT_INTERRUPT_ERROR_CODE = 311
        const val ORDER_ALREADY_CLOSED_ERROR_CODE = 308
        const val ORDER_NUMBER_ERROR_CODE = 309
        const val START_INSUFFICIENT_BALANCE_RESULT = 2
        const val START_ALREADY_USING_RESULT = 36
        const val ACCOUNT_TYPE_STUDENT = 2
        const val CLOSE_SUCCESS_RESULT = 0
        const val CLOSE_ORDER_NUMBER_ERROR_RESULT = 38
        const val CLOSE_ALREADY_CLOSED_RESULT = 40
        const val RESULT_DELAY_MILLIS = 5_000L
    }
}

private fun JsonElement.asJsonObjectOrNull(): JsonObject? {
    return if (isJsonObject) asJsonObject else null
}

private fun JsonObject.stringValue(vararg names: String): String? {
    return names.asSequence()
        .mapNotNull { name -> get(name)?.takeUnless { it.isJsonNull }?.asString }
        .firstOrNull { it.isNotBlank() }
}

private fun JsonObject.intValue(vararg names: String): Int? {
    return names.asSequence()
        .mapNotNull { name -> get(name)?.takeUnless { it.isJsonNull }?.asInt }
        .firstOrNull()
}

private fun JsonObject.consumedMilliUnits(): Long? {
    val preDeductMilliUnits = longValue("preDeductMoney")
    val preDeductAfterMilliUnits = longValue("preDeductMoneyAfter")
    return if (preDeductMilliUnits != null && preDeductAfterMilliUnits != null) {
        preDeductMilliUnits - preDeductAfterMilliUnits
    } else {
        null
    }
}

private fun JsonObject.longValue(name: String): Long? {
    return get(name)
        ?.takeUnless { it.isJsonNull }
        ?.asString
        ?.toLongOrNull()
}

private fun HotWaterMqttEvent.Consume.consumedMilliUnits(): Long? {
    return consumedMilliUnits ?: if (
        preDeductMilliUnits != null && preDeductAfterMilliUnits != null
    ) {
        (preDeductMilliUnits - preDeductAfterMilliUnits).takeIf { it >= 0L }
    } else {
        null
    }
}

private fun JsonObject.booleanValue(name: String): Boolean? {
    return get(name)?.takeUnless { it.isJsonNull }?.asBoolean
}
