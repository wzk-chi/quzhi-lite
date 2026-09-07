package com.quzhi.lite.data

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

object LoginCredentials {
    fun passwordValue(password: String): String {
        return md5(password).uppercase(Locale.ROOT).takeLast(10)
    }

    fun verificationSecret(phone: String): String {
        require(phone.length == 11) { "手机号必须为 11 位" }
        return md5(phone.substring(0, 3) + phone.substring(7, 11) + "klcx")
    }

    private fun md5(value: String): String {
        val digest = MessageDigest.getInstance("MD5")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString(separator = "") { byte ->
            "%02x".format(Locale.ROOT, byte.toInt() and 0xff)
        }
    }
}
