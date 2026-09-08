package com.quzhi.lite

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.quzhi.lite.data.BluetoothDiscovery
import com.quzhi.lite.data.AnnouncementConsentStatus
import com.quzhi.lite.data.AnnouncementConsentStore
import com.quzhi.lite.data.BalanceStore
import com.quzhi.lite.data.DeviceInfoApi
import com.quzhi.lite.data.DeviceStore
import com.quzhi.lite.data.HotWaterApi
import com.quzhi.lite.data.QuzhiApi
import com.quzhi.lite.data.SessionStore
import com.quzhi.lite.ui.AddDeviceScreen
import com.quzhi.lite.ui.AnnouncementDialog
import com.quzhi.lite.ui.AboutScreen
import com.quzhi.lite.ui.GITHUB_REPOSITORY_URL
import com.quzhi.lite.ui.HomeScreen
import com.quzhi.lite.ui.HistoryOrderScreen
import com.quzhi.lite.ui.LoginScreen
import com.quzhi.lite.ui.QuzhiLiteTheme

class MainActivity : ComponentActivity() {
    private fun openRepository() {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_REPOSITORY_URL)))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val announcementConsentStore = AnnouncementConsentStore(applicationContext)
        val sessionStore = SessionStore(applicationContext)
        val api = QuzhiApi()
        val hotWaterApi = HotWaterApi()
        val deviceInfoApi = DeviceInfoApi()
        val bluetoothDiscovery = BluetoothDiscovery(applicationContext)
        val deviceStore = DeviceStore(applicationContext)
        val balanceStore = BalanceStore(applicationContext)
        val savedAnnouncementStatus = announcementConsentStore.load()
        val savedSession = sessionStore.load()

        setContent {
            QuzhiLiteTheme {
                var announcementStatus by remember { mutableStateOf(savedAnnouncementStatus) }

                when (announcementStatus) {
                    AnnouncementConsentStatus.Pending -> {
                        AnnouncementDialog(
                            onOpenRepository = ::openRepository,
                            onAccept = {
                                announcementConsentStore.accept()
                                announcementStatus = AnnouncementConsentStatus.Accepted
                            },
                            onDecline = {
                                finish()
                            },
                        )
                    }

                    AnnouncementConsentStatus.Accepted -> {
                        var session by remember { mutableStateOf(savedSession) }
                        var showAddDevice by rememberSaveable { mutableStateOf(false) }
                        var showHistoryOrders by rememberSaveable { mutableStateOf(false) }
                        var showAbout by rememberSaveable { mutableStateOf(false) }
                        var refreshBalanceOnHomeEntry by rememberSaveable { mutableStateOf(true) }

                        val activeSession = session
                        if (activeSession == null) {
                            LoginScreen(
                                api = api,
                                onLoginSuccess = { newSession ->
                                    sessionStore.save(newSession)
                                    session = newSession
                                    showAddDevice = false
                                    showHistoryOrders = false
                                    showAbout = false
                                    refreshBalanceOnHomeEntry = true
                                },
                            )
                        } else {
                            var savedDevices by remember(activeSession.accountId, activeSession.projectId) {
                                mutableStateOf(deviceStore.load(activeSession))
                            }

                            BackHandler(enabled = showAddDevice || showHistoryOrders || showAbout) {
                                if (showAddDevice) {
                                    showAddDevice = false
                                } else if (showHistoryOrders) {
                                    showHistoryOrders = false
                                } else {
                                    showAbout = false
                                }
                            }

                            if (showAddDevice) {
                                AddDeviceScreen(
                                    session = activeSession,
                                    deviceInfoApi = deviceInfoApi,
                                    bluetoothDiscovery = bluetoothDiscovery,
                                    onBack = { showAddDevice = false },
                                    onDeviceSaved = { device ->
                                        deviceStore.upsert(activeSession, device)
                                        savedDevices = deviceStore.load(activeSession)
                                    },
                                )
                            } else if (showHistoryOrders) {
                                HistoryOrderScreen(
                                    session = activeSession,
                                    api = api,
                                    onBack = { showHistoryOrders = false },
                                )
                            } else if (showAbout) {
                                AboutScreen(
                                    onBack = { showAbout = false },
                                    onOpenRepository = ::openRepository,
                                )
                            } else {
                                HomeScreen(
                                    session = activeSession,
                                    quzhiApi = api,
                                    balanceStore = balanceStore,
                                    api = hotWaterApi,
                                    devices = savedDevices,
                                    refreshBalanceOnEnter = refreshBalanceOnHomeEntry,
                                    onOpenAddDevice = {
                                        refreshBalanceOnHomeEntry = false
                                        showHistoryOrders = false
                                        showAbout = false
                                        showAddDevice = true
                                    },
                                    onOpenOrderHistory = {
                                        refreshBalanceOnHomeEntry = false
                                        showAddDevice = false
                                        showAbout = false
                                        showHistoryOrders = true
                                    },
                                    onOpenAbout = {
                                        refreshBalanceOnHomeEntry = false
                                        showAddDevice = false
                                        showHistoryOrders = false
                                        showAbout = true
                                    },
                                    onDeleteDevice = { device ->
                                        deviceStore.delete(activeSession, device)
                                        savedDevices = deviceStore.load(activeSession)
                                    },
                                    onLogout = {
                                        sessionStore.clear()
                                        session = null
                                        showAddDevice = false
                                        showHistoryOrders = false
                                        showAbout = false
                                        refreshBalanceOnHomeEntry = true
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
