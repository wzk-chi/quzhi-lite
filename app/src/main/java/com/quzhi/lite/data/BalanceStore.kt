package com.quzhi.lite.data

import android.content.Context

class BalanceStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun load(session: UserSession): WalletBalance? {
        val key = key(session)
        if (!preferences.contains(accountKey(key))) {
            return null
        }
        return WalletBalance(
            accountMilliUnits = preferences.getLong(accountKey(key), 0L),
            givenMilliUnits = preferences.getLong(givenKey(key), 0L),
        )
    }

    fun save(session: UserSession, balance: WalletBalance) {
        val key = key(session)
        preferences.edit()
            .putLong(accountKey(key), balance.accountMilliUnits)
            .putLong(givenKey(key), balance.givenMilliUnits)
            .apply()
    }

    private fun key(session: UserSession): String {
        return "balance_${session.accountId}_${session.projectId}_${session.telephone}"
    }

    private fun accountKey(key: String): String = "${key}_account"

    private fun givenKey(key: String): String = "${key}_given"

    private companion object {
        const val PREFERENCES = "quzhi_lite_balance"
    }
}
