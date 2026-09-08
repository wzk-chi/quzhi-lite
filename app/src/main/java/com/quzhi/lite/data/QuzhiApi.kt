package com.quzhi.lite.data

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.math.BigDecimal
import java.math.RoundingMode

data class UserSession(
    val loginCode: String,
    val v3LoginCode: String,
    val telephone: String,
    val userId: Long,
    val accountId: Long,
    val projectId: Int,
    val name: String,
)

data class WalletBalance(
    val accountMilliUnits: Long,
    val givenMilliUnits: Long,
) {
    val totalMilliUnits: Long
        get() = accountMilliUnits + givenMilliUnits

    fun formattedAmount(): String {
        return formatMilliUnits(totalMilliUnits)
    }
}

data class ConsumptionOrder(
    val orderNo: String,
    val consumeDate: String,
    val location: String,
    val consumeMoney: Double,
    val tradeSettlement: Int,
    val runStatus: Int,
) {
    fun formattedAmount(): String {
        return BigDecimal.valueOf(consumeMoney)
            .setScale(2, RoundingMode.HALF_UP)
            .toPlainString()
    }

    fun settlementText(): String {
        return if (tradeSettlement == 1) "已支付" else "未支付"
    }

    fun runStatusText(): String {
        return if (runStatus == 1) "已完成" else "未完成"
    }
}

internal fun formatMilliUnits(milliUnits: Long): String {
    return BigDecimal.valueOf(milliUnits)
        .movePointLeft(3)
        .setScale(2, RoundingMode.HALF_UP)
        .toPlainString()
}

class ApiException(message: String) : IOException(message)

class QuzhiApi(
    private val client: OkHttpClient = OkHttpClient(),
    private val gson: Gson = Gson(),
) {
    suspend fun sendVerificationCode(phone: String) {
        val response = withContext(Dispatchers.IO) {
            get(
                path = "user/verification/code/get",
                parameters = mapOf(
                    "secret" to LoginCredentials.verificationSecret(phone),
                    "typeId" to "3",
                    "telephone" to phone,
                    "platform" to "1",
                    "phoneSystem" to "android",
                    "version" to APP_VERSION,
                ),
            )
        }
        if (response.errorCode != 0) {
            throw ApiException(response.message ?: "验证码发送失败")
        }
    }

    suspend fun loginByPassword(phone: String, password: String): UserSession {
        val response = withContext(Dispatchers.IO) {
            postForm(
                path = "user/login",
                parameters = mapOf(
                    "password" to LoginCredentials.passwordValue(password),
                    "telephone" to phone,
                    "type" to "0",
                    "identifier" to "",
                    "phoneSystem" to "android",
                    "version" to APP_VERSION,
                ),
            )
        }
        return parseLoginResponse(response)
    }

    suspend fun loginBySms(phone: String, smsCode: String): UserSession {
        val response = withContext(Dispatchers.IO) {
            postForm(
                path = "user/registerAndLogin",
                parameters = mapOf(
                    "type" to "5",
                    "telephone" to phone,
                    "smsCode" to smsCode,
                    "phoneSystem" to "android",
                    "version" to APP_VERSION,
                ),
            )
        }
        return parseLoginResponse(response)
    }

    suspend fun fetchBalance(session: UserSession): WalletBalance {
        val response = withContext(Dispatchers.IO) {
            get(
                path = "account/wallet",
                parameters = commonParameters(session),
                projectId = session.projectId,
            )
        }
        if (response.errorCode != 0) {
            throw ApiException(response.message ?: "余额获取失败（${response.errorCode}）")
        }
        val data = response.data
            ?.takeUnless { it.isJsonNull }
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject
            ?: throw ApiException("余额响应缺少账户信息")
        return WalletBalance(
            accountMilliUnits = data.longValue("accountMoney", "accountRealMoney"),
            givenMilliUnits = data.longValue("accountGivenMoney"),
        )
    }

    suspend fun fetchConsumptionOrders(
        session: UserSession,
        month: String,
    ): List<ConsumptionOrder> {
        val response = withContext(Dispatchers.IO) {
            get(
                path = "order/query/account/bill/list",
                parameters = commonParameters(session) + mapOf(
                    "month" to month,
                    "billRequestType" to "2",
                ),
                projectId = session.projectId,
            )
        }
        if (response.errorCode != 0) {
            throw ApiException(response.message ?: "历史订单获取失败（${response.errorCode}）")
        }

        val data = response.data
            ?.takeUnless { it.isJsonNull }
            ?: return emptyList()
        if (!data.isJsonArray) {
            throw ApiException("历史订单响应格式异常")
        }

        return gson.fromJson(data, Array<BillInfoDto>::class.java)
            .asSequence()
            .filter { it.billType == 2 }
            .mapNotNull { item ->
                item.consumeBillDTO?.let { consume ->
                    ConsumptionOrder(
                        orderNo = consume.orderNo.orEmpty(),
                        consumeDate = consume.consumeDate.orEmpty(),
                        location = consume.description
                            ?.takeIf { it.isNotBlank() }
                            ?: consume.deviceDescription.orEmpty(),
                        consumeMoney = consume.consumeMoney,
                        tradeSettlement = consume.tradeSettlement,
                        runStatus = consume.runStatus,
                    )
                }
            }
            .toList()
    }

    private fun postForm(path: String, parameters: Map<String, String>): ApiResponse {
        val form = FormBody.Builder().apply {
            parameters.forEach { (key, value) -> add(key, value) }
        }.build()
        val request = Request.Builder()
            .url(BASE_URL + path)
            .post(form)
            .build()

        return execute(request)
    }

    private fun get(
        path: String,
        parameters: Map<String, String>,
        projectId: Int = 0,
    ): ApiResponse {
        val url = (BASE_URL + path).toHttpUrl().newBuilder().apply {
            parameters.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build()
        val requestBuilder = Request.Builder()
            .url(url)
            .get()
        if (projectId != 0) {
            requestBuilder.header("Config-Project", projectId.toString())
            requestBuilder.header("Config-Keys", CONFIG_KEYS)
        }

        return execute(requestBuilder.build())
    }

    private fun commonParameters(session: UserSession): Map<String, String> {
        val loginCode = session.v3LoginCode.ifBlank { session.loginCode }
        return buildMap {
            put("projectId", session.projectId.toString())
            if (session.accountId != 0L) {
                put("accountId", session.accountId.toString())
            }
            if (session.userId != 0L) {
                put("userId", session.userId.toString())
            }
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

    private fun parseLoginResponse(response: ApiResponse): UserSession {
        if (response.errorCode != 0 && response.errorCode != 2) {
            throw ApiException(response.message ?: "登录失败")
        }
        val loginUser = response.data?.let { gson.fromJson(it, UserInfoDto::class.java) }
            ?: throw ApiException("登录响应缺少用户信息")
        val account = loginUser.userAccount ?: loginUser
        val loginCode = loginUser.loginCode.orEmpty()
        return UserSession(
            loginCode = loginCode,
            v3LoginCode = if (loginUser.userAccount != null) {
                loginCode
            } else {
                loginUser.v3LoginCode?.takeIf { it.isNotBlank() } ?: loginCode
            },
            telephone = (loginUser.telephone ?: account.telephone).orEmpty(),
            userId = loginUser.userId ?: account.userId ?: 0L,
            accountId = account.accountId ?: 0L,
            projectId = account.projectId ?: 0,
            name = account.name.orEmpty(),
        )
    }

    private data class ApiResponse(
        val errorCode: Int = -1,
        @SerializedName(value = "message", alternate = ["errorMessage"])
        val message: String? = null,
        val data: JsonElement? = null,
    )

    private data class UserInfoDto(
        val accountId: Long? = null,
        val projectId: Int? = null,
        val loginCode: String? = null,
        val v3LoginCode: String? = null,
        @SerializedName(value = "telephone", alternate = ["telPhone"])
        val telephone: String? = null,
        @SerializedName(value = "userId", alternate = ["v3UserId"])
        val userId: Long? = null,
        val name: String? = null,
        val userAccount: UserInfoDto? = null,
    )

    private data class BillInfoDto(
        val billType: Int = 0,
        val consumeBillDTO: ConsumeBillDto? = null,
    )

    private data class ConsumeBillDto(
        val consumeDate: String? = null,
        val consumeMoney: Double = 0.0,
        val description: String? = null,
        val deviceDescription: String? = null,
        val orderNo: String? = null,
        val runStatus: Int = 0,
        val tradeSettlement: Int = 0,
    )

    private companion object {
        const val BASE_URL = "https://v3-api.china-qzxy.cn/"
        const val APP_VERSION = "6.5.28"
        const val CONFIG_KEYS =
            "module_list,advertise_type,question_list,service_phone_list,banner_list_app,activity_list_app,aliCard_popup_config"
    }
}

private fun JsonElement.longValue(vararg names: String): Long {
    if (!isJsonObject) {
        return 0L
    }
    return names.asSequence()
        .mapNotNull { name ->
            asJsonObject.get(name)
                ?.takeUnless { it.isJsonNull }
                ?.asString
                ?.toLongOrNull()
        }
        .firstOrNull()
        ?: 0L
}
