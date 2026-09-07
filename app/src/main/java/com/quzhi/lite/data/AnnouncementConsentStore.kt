package com.quzhi.lite.data

import android.content.Context

enum class AnnouncementConsentStatus {
    Pending,
    Accepted,
}

class AnnouncementConsentStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun load(): AnnouncementConsentStatus {
        return when (preferences.getString(STATUS_KEY, null)) {
            ACCEPTED_VALUE -> AnnouncementConsentStatus.Accepted
            else -> AnnouncementConsentStatus.Pending
        }
    }

    fun accept() {
        save(ACCEPTED_VALUE)
    }

    private fun save(value: String) {
        preferences.edit().putString(STATUS_KEY, value).apply()
    }

    private companion object {
        const val PREFERENCES = "quzhi_lite_announcement"
        const val STATUS_KEY = "consent_status"
        const val ACCEPTED_VALUE = "accepted"
    }
}
