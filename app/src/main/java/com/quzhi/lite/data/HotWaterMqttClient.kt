package com.quzhi.lite.data

import android.os.SystemClock
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.channels.BufferOverflow
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.nio.charset.StandardCharsets

internal sealed interface HotWaterMqttEvent {
    val createTime: Long
    val receivedAtMillis: Long

    data class Start(
        val deviceSnCode: String?,
        val result: Int,
        val accountId: Long,
        val accountType: Int,
        val orderNo: String?,
        val preDeductMilliUnits: Long?,
        override val createTime: Long,
        override val receivedAtMillis: Long,
    ) : HotWaterMqttEvent

    data class Close(
        val deviceSnCode: String?,
        val result: Int,
        override val createTime: Long,
        override val receivedAtMillis: Long,
    ) : HotWaterMqttEvent

    data class Consume(
        val preDeductMilliUnits: Long?,
        val preDeductAfterMilliUnits: Long?,
        val consumedMilliUnits: Long?,
        override val createTime: Long,
        override val receivedAtMillis: Long,
    ) : HotWaterMqttEvent
}

class HotWaterMqttClient(
    private val gson: Gson = Gson(),
) {
    private val events = MutableSharedFlow<HotWaterMqttEvent>(
        replay = EVENT_REPLAY_COUNT,
        extraBufferCapacity = EVENT_BUFFER_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val connectionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connectionMutex = Mutex()

    @Volatile
    private var mqttClient: MqttAsyncClient? = null

    @Volatile
    private var connectedSessionKey: String? = null

    fun connectInBackground(session: UserSession) {
        Log.d(TAG, "connect scheduled")
        connectionScope.launch {
            val startedAtMillis = SystemClock.elapsedRealtime()
            try {
                Log.d(TAG, "connect begin")
                ensureConnected(session)
                Log.d(
                    TAG,
                    "connect ready elapsed=${SystemClock.elapsedRealtime() - startedAtMillis}ms",
                )
            } catch (error: Exception) {
                // HTTP fallback remains available when MQTT is unavailable.
                Log.w(
                    TAG,
                    "connect failed elapsed=${SystemClock.elapsedRealtime() - startedAtMillis}ms",
                    error,
                )
            }
        }
    }

    fun disconnectInBackground() {
        Log.d(TAG, "disconnect scheduled")
        connectionScope.launch {
            connectionMutex.withLock {
                closeClientLocked()
            }
        }
    }

    internal suspend fun awaitStart(
        snCode: String,
        operationStartedAtMillis: Long,
        timeoutMillis: Long,
    ): HotWaterMqttEvent.Start? {
        Log.d(TAG, "await start MQTT result timeout=${timeoutMillis}ms")
        val startedAtMillis = SystemClock.elapsedRealtime()
        val result = awaitEvent(timeoutMillis, operationStartedAtMillis) { event ->
            event is HotWaterMqttEvent.Start && event.deviceSnCode == snCode
        } as? HotWaterMqttEvent.Start
        Log.d(
            TAG,
            "await start MQTT result completed matched=${result != null} " +
                "elapsed=${SystemClock.elapsedRealtime() - startedAtMillis}ms",
        )
        return result
    }

    internal suspend fun awaitClose(
        snCode: String,
        operationStartedAtMillis: Long,
        timeoutMillis: Long,
    ): HotWaterMqttEvent.Close? {
        Log.d(TAG, "await close MQTT result timeout=${timeoutMillis}ms")
        val result = awaitEvent(timeoutMillis, operationStartedAtMillis) { event ->
            event is HotWaterMqttEvent.Close && event.deviceSnCode == snCode
        } as? HotWaterMqttEvent.Close
        Log.d(TAG, "await close MQTT result completed matched=${result != null}")
        return result
    }

    internal suspend fun awaitConsume(
        operationStartedAtMillis: Long,
        timeoutMillis: Long,
    ): HotWaterMqttEvent.Consume? {
        Log.d(TAG, "await consume MQTT result timeout=${timeoutMillis}ms")
        val result = awaitEvent(timeoutMillis, operationStartedAtMillis) { event ->
            event is HotWaterMqttEvent.Consume
        } as? HotWaterMqttEvent.Consume
        Log.d(TAG, "await consume MQTT result completed matched=${result != null}")
        return result
    }

    private suspend fun awaitEvent(
        timeoutMillis: Long,
        operationStartedAtMillis: Long,
        predicate: (HotWaterMqttEvent) -> Boolean,
    ): HotWaterMqttEvent? {
        return withTimeoutOrNull(timeoutMillis) {
            events
                .filter { event ->
                    event.receivedAtMillis >= operationStartedAtMillis &&
                        isRecent(event) &&
                        predicate(event)
                }
                .first()
        }
    }

    private suspend fun ensureConnected(session: UserSession) {
        val sessionKey = sessionKey(session)
        val topics = topics(session)
        val topicFilters = arrayOf(topics.start, topics.close, topics.consume)
        connectionMutex.withLock {
            if (connectedSessionKey == sessionKey && mqttClient?.isConnected == true) {
                Log.d(TAG, "already connected and subscribed")
                return@withLock
            }

            closeClientLocked()

            val client = MqttAsyncClient(
                BROKER_URL,
                "android_${session.telephone}",
                MemoryPersistence(),
            )
            client.setCallback(object : MqttCallback {
                override fun connectionLost(cause: Throwable?) {
                    Log.w(TAG, "MQTT connection lost", cause)
                    if (mqttClient === client) {
                        mqttClient = null
                        connectedSessionKey = null
                    }
                }

                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    if (topic == null || message == null) {
                        return
                    }
                    Log.d(TAG, "MQTT message received topic=$topic bytes=${message.payload.size}")
                    val event = parseEvent(
                        topic = topic,
                        payload = String(message.payload, StandardCharsets.UTF_8),
                        topics = topics,
                    )
                    if (event == null) {
                        Log.w(TAG, "MQTT message could not be parsed")
                    } else {
                        Log.d(
                            TAG,
                            "MQTT event parsed type=${event::class.simpleName} " +
                                "createTime=${event.createTime}",
                        )
                        events.tryEmit(event)
                    }
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) = Unit
            })

            try {
                Log.d(TAG, "connecting to MQTT broker")
                val options = MqttConnectOptions().apply {
                    isCleanSession = true
                    connectionTimeout = 10
                    keepAliveInterval = 90
                    userName = MQTT_USERNAME
                    password = MQTT_PASSWORD.toCharArray()
                    isAutomaticReconnect = false
                }
                client.connect(options).waitForCompletion()
                Log.d(TAG, "MQTT broker connected; subscribing to ${topicFilters.size} topics")
                client.subscribe(topicFilters, IntArray(topicFilters.size) { MQTT_QOS }).waitForCompletion()
                mqttClient = client
                connectedSessionKey = sessionKey
                Log.d(TAG, "MQTT topics subscribed")
            } catch (error: Exception) {
                Log.w(TAG, "MQTT connect or subscribe failed", error)
                runCatching { client.close() }
                throw error
            }
        }
    }

    private fun parseEvent(
        topic: String,
        payload: String,
        topics: MqttTopics,
    ): HotWaterMqttEvent? {
        val data = runCatching {
            gson.fromJson(payload, JsonObject::class.java)
        }.getOrNull() ?: return null
        val receivedAtMillis = System.currentTimeMillis()
        val createTime = data.longValue("createTime") ?: return null

        return when (topic) {
            topics.start -> HotWaterMqttEvent.Start(
                deviceSnCode = data.stringValue("deviceSncode", "deviceSnCode"),
                result = data.intValue("result", "state") ?: -1,
                accountId = data.longValue("accountId") ?: 0L,
                accountType = data.intValue("accountType") ?: 0,
                orderNo = data.stringValue("timeIds", "orderNo"),
                preDeductMilliUnits = data.longValue("perMoney", "preDeductMoney"),
                createTime = createTime,
                receivedAtMillis = receivedAtMillis,
            )

            topics.close -> HotWaterMqttEvent.Close(
                deviceSnCode = data.stringValue("deviceSncode", "deviceSnCode"),
                result = data.intValue("result", "state") ?: -1,
                createTime = createTime,
                receivedAtMillis = receivedAtMillis,
            )

            topics.consume -> HotWaterMqttEvent.Consume(
                preDeductMilliUnits = data.longValue("perMoney", "preDeductMoney"),
                preDeductAfterMilliUnits = data.longValue("upLeadMoney", "preDeductMoneyAfter"),
                consumedMilliUnits = data.longValue("upMoney", "consumeMoney"),
                createTime = createTime,
                receivedAtMillis = receivedAtMillis,
            )

            else -> null
        }
    }

    private fun isRecent(event: HotWaterMqttEvent): Boolean {
        return event.createTime > 0L &&
            System.currentTimeMillis() - event.createTime * 1000L < RESULT_WINDOW_MILLIS
    }

    private fun closeClientLocked() {
        val client = mqttClient ?: return
        mqttClient = null
        connectedSessionKey = null
        runCatching {
            if (client.isConnected) {
                client.disconnectForcibly()
            }
        }
        runCatching { client.close() }
    }

    private fun sessionKey(session: UserSession): String {
        return listOf(session.telephone, session.accountId, session.projectId).joinToString("/")
    }

    private fun topics(session: UserSession): MqttTopics {
        return MqttTopics(
            start = "app_downRate_${session.telephone}",
            close = "app_shutdownOrder_${session.telephone}",
            consume = "app_uploadData_${session.telephone}",
        )
    }

    private data class MqttTopics(
        val start: String,
        val close: String,
        val consume: String,
    )

    private companion object {
        const val TAG = "HotWaterMqtt"
        const val BROKER_URL = "tcp://mq.china-qzxy.cn:1883"
        const val MQTT_USERNAME = "android-client"
        const val MQTT_PASSWORD = "android-client"
        const val MQTT_QOS = 2
        const val RESULT_WINDOW_MILLIS = 5_000L
        const val EVENT_REPLAY_COUNT = 32
        const val EVENT_BUFFER_CAPACITY = 32
    }
}

private fun JsonObject.stringValue(vararg names: String): String? {
    return names.asSequence()
        .mapNotNull { name -> get(name)?.takeUnless { it.isJsonNull }?.asString }
        .firstOrNull { it.isNotBlank() }
}

private fun JsonObject.longValue(vararg names: String): Long? {
    return names.asSequence().mapNotNull { name ->
        runCatching {
            get(name)?.takeUnless { it.isJsonNull }?.asString?.toLongOrNull()
        }.getOrNull()
    }.firstOrNull()
}

private fun JsonObject.intValue(vararg names: String): Int? {
    return names.asSequence().mapNotNull { name ->
        runCatching {
            get(name)?.takeUnless { it.isJsonNull }?.asString?.toIntOrNull()
        }.getOrNull()
    }.firstOrNull()
}
