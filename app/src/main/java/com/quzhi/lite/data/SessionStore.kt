package com.quzhi.lite.data

import android.content.Context
import android.util.Base64
import com.google.gson.Gson
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import javax.crypto.spec.GCMParameterSpec

class SessionStore(
    context: Context,
    private val gson: Gson = Gson(),
) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    fun load(): UserSession? {
        val encodedData = preferences.getString(DATA, null) ?: return null
        val encodedIv = preferences.getString(IV, null) ?: return null
        val cipher = Cipher.getInstance(CIPHER)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(128, Base64.decode(encodedIv, Base64.NO_WRAP)),
        )
        val json = cipher.doFinal(Base64.decode(encodedData, Base64.NO_WRAP))
            .toString(StandardCharsets.UTF_8)
        return gson.fromJson(json, UserSession::class.java)
    }

    fun save(session: UserSession) {
        val cipher = Cipher.getInstance(CIPHER)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(gson.toJson(session).toByteArray(StandardCharsets.UTF_8))
        preferences.edit()
            .putString(DATA, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    fun loadLastLoginCacheRenewalAt(): Long {
        return preferences.getLong(LAST_LOGIN_CACHE_RENEWAL_AT, 0L)
    }

    fun saveLastLoginCacheRenewalAt(timestamp: Long) {
        preferences.edit()
            .putLong(LAST_LOGIN_CACHE_RENEWAL_AT, timestamp)
            .apply()
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val existingKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existingKey != null) {
            return existingKey
        }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val CIPHER = "AES/GCM/NoPadding"
        const val KEY_ALIAS = "quzhi_lite_session"
        const val PREFERENCES = "quzhi_lite_session"
        const val DATA = "data"
        const val IV = "iv"
        const val LAST_LOGIN_CACHE_RENEWAL_AT = "last_login_cache_renewal_at"
    }
}
