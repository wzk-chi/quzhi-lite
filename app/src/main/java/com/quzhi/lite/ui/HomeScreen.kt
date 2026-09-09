package com.quzhi.lite.ui

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.quzhi.lite.data.BalanceStore
import com.quzhi.lite.data.formatMilliUnits
import com.quzhi.lite.data.HotWaterApi
import com.quzhi.lite.data.HotWaterMqttClient
import com.quzhi.lite.data.QuzhiApi
import com.quzhi.lite.data.SavedDevice
import com.quzhi.lite.data.UserSession
import com.quzhi.lite.data.WalletBalance
import com.quzhi.lite.data.WaterOrder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private sealed interface HotWaterUiState {
    data object Idle : HotWaterUiState
    data object Starting : HotWaterUiState
    data class Running(val order: WaterOrder) : HotWaterUiState
    data object Stopping : HotWaterUiState
    data class Error(val order: WaterOrder? = null) : HotWaterUiState
}

@Composable
fun HomeScreen(
    session: UserSession,
    quzhiApi: QuzhiApi,
    balanceStore: BalanceStore,
    api: HotWaterApi,
    mqttClient: HotWaterMqttClient,
    devices: List<SavedDevice>,
    refreshBalanceOnEnter: Boolean,
    onOpenAddDevice: () -> Unit,
    onOpenOrderHistory: () -> Unit,
    onOpenAbout: () -> Unit,
    onDeleteDevice: (SavedDevice) -> Unit,
    onLogout: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedIdentity by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteCandidate by remember { mutableStateOf<SavedDevice?>(null) }
    var operationState by remember { mutableStateOf<HotWaterUiState>(HotWaterUiState.Idle) }
    var stopSuccessAmountMilliUnits by remember { mutableStateOf<Long?>(null) }
    var stopOrderAlreadyClosed by remember { mutableStateOf(false) }
    var showStopSuccessDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var balance by remember(session) { mutableStateOf(balanceStore.load(session)) }
    var balanceLoading by remember(session, refreshBalanceOnEnter) {
        mutableStateOf(refreshBalanceOnEnter)
    }
    var balanceMessage by remember { mutableStateOf<String?>(null) }

    DisposableEffect(session) {
        mqttClient.connectInBackground(session)
        onDispose { mqttClient.disconnectInBackground() }
    }

    val runningOrder = (operationState as? HotWaterUiState.Running)?.order
        ?: (operationState as? HotWaterUiState.Error)?.order
    val loadingMessage = when (operationState) {
        HotWaterUiState.Starting -> "正在启动"
        HotWaterUiState.Stopping -> "正在关闭"
        else -> null
    }
    val busy = loadingMessage != null

    LaunchedEffect(devices) {
        if (devices.none { it.identity == selectedIdentity }) {
            selectedIdentity = devices.firstOrNull()?.identity
        }
    }

    suspend fun refreshBalance() {
        balanceLoading = true
        balanceMessage = null
        try {
            val latestBalance = quzhiApi.fetchBalance(session)
            balance = latestBalance
            balanceStore.save(session, latestBalance)
        } catch (cancellationException: CancellationException) {
            throw cancellationException
        } catch (error: Exception) {
            balanceMessage = error.message ?: "余额暂时无法获取"
        } finally {
            balanceLoading = false
        }
    }

    LaunchedEffect(session, refreshBalanceOnEnter) {
        if (refreshBalanceOnEnter) {
            refreshBalance()
        }
    }

    fun startWater(device: SavedDevice) {
        selectedIdentity = device.identity
        operationState = HotWaterUiState.Starting
        Toast.makeText(context, "正在启动热水…", Toast.LENGTH_SHORT).show()
        scope.launch {
            try {
                val order = api.start(session, device.snCode)
                operationState = HotWaterUiState.Running(order)
                refreshBalance()
            } catch (cancellationException: CancellationException) {
                throw cancellationException
            } catch (error: Exception) {
                val reason = error.message?.takeIf { it.isNotBlank() } ?: "未知原因"
                operationState = HotWaterUiState.Error()
                Toast.makeText(context, "启动失败：$reason", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun stopWater(device: SavedDevice, order: WaterOrder) {
        operationState = HotWaterUiState.Stopping
        scope.launch {
            try {
                val result = api.stop(session, device.snCode, order.orderNo)
                operationState = HotWaterUiState.Idle
                stopSuccessAmountMilliUnits = result.consumedMilliUnits
                stopOrderAlreadyClosed = result.orderAlreadyClosed
                showStopSuccessDialog = true
                refreshBalance()
            } catch (cancellationException: CancellationException) {
                throw cancellationException
            } catch (error: Exception) {
                operationState = HotWaterUiState.Error(order)
                val reason = error.message?.takeIf { it.isNotBlank() } ?: "未知原因"
                Toast.makeText(context, "结束失败：$reason", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun toggleWater(device: SavedDevice, enabled: Boolean) {
        if (enabled) {
            if (runningOrder == null) {
                startWater(device)
            }
        } else if (selectedIdentity == device.identity) {
            runningOrder?.let { stopWater(device, it) }
                ?: run { operationState = HotWaterUiState.Idle }
        }
    }

    Scaffold { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                AccountCard(
                    session = session,
                    balance = balance,
                    balanceLoading = balanceLoading,
                    balanceMessage = balanceMessage,
                    onOpenOrderHistory = onOpenOrderHistory,
                    actionsEnabled = !busy,
                    onLogout = { showLogoutDialog = true },
                )
            }
            item {
                SectionHeading(
                    title = "我的设备",
                    supporting = null,
                    onAddDevice = onOpenAddDevice,
                    actionsEnabled = !busy,
                    onOpenAbout = onOpenAbout,
                )
            }
            if (devices.isEmpty()) {
                item {
                    EmptyDeviceCard(
                        onAddDevice = onOpenAddDevice,
                        enabled = !busy,
                    )
                }
            } else {
                items(
                    items = devices,
                    key = { it.identity },
                ) { device ->
                    SavedDeviceCard(
                        device = device,
                        checked = device.identity == selectedIdentity && (
                            operationState is HotWaterUiState.Starting ||
                                operationState is HotWaterUiState.Running ||
                                operationState is HotWaterUiState.Stopping ||
                                (operationState as? HotWaterUiState.Error)?.order != null
                            ),
                        enabled = !busy && (runningOrder == null || device.identity == selectedIdentity),
                        onToggle = { enabled -> toggleWater(device, enabled) },
                        onLongClick = { deleteCandidate = device },
                    )
                }
            }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("退出登录") },
            text = { Text("确定要退出当前账号吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    },
                ) {
                    Text("退出")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("取消")
                }
            },
        )
    }

    if (showStopSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showStopSuccessDialog = false },
            title = { Text(if (stopOrderAlreadyClosed) "订单已结束" else "结束成功") },
            text = {
                if (stopOrderAlreadyClosed) {
                    Text("设备已关闭，请前往账单查看")
                } else {
                    Text(
                        stopSuccessAmountMilliUnits?.let {
                            "本次消费 ${formatMilliUnits(it)} 元"
                        } ?: "本次消费金额：未知",
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showStopSuccessDialog = false }) {
                    Text("确定")
                }
            },
        )
    }

    deleteCandidate?.let { device ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("删除设备") },
            text = { Text("确定要删除“${deviceLocationText(device)}”吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (selectedIdentity == device.identity) {
                            selectedIdentity = null
                            operationState = HotWaterUiState.Idle
                        }
                        onDeleteDevice(device)
                        deleteCandidate = null
                    },
                ) {
                    Text("是")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) { Text("否") }
            },
        )
    }

    loadingMessage?.let { message ->
        LoadingOverlay(
            message = message,
            scrimAlpha = if (operationState is HotWaterUiState.Stopping) 0.52f else 0.36f,
        )
    }
}

@Composable
private fun AccountCard(
    session: UserSession,
    balance: WalletBalance?,
    balanceLoading: Boolean,
    balanceMessage: String?,
    onOpenOrderHistory: () -> Unit,
    actionsEnabled: Boolean,
    onLogout: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Person,
                            contentDescription = "用户",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = session.telephone,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    if (session.name.isNotBlank()) {
                        Text(
                            text = session.name,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = onLogout, enabled = actionsEnabled) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Logout,
                        contentDescription = "退出登录",
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                when {
                    balance != null -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.AccountBalanceWallet,
                                    contentDescription = "账户余额",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "¥${balance.formattedAmount()}",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            IconButton(
                                onClick = onOpenOrderHistory,
                                enabled = actionsEnabled,
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.ReceiptLong,
                                    contentDescription = "历史订单",
                                )
                            }
                        }
                        if (balanceLoading) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                            )
                        }
                        balanceMessage?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    balanceLoading -> {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                        )
                    }
                    else -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "暂不可用",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            IconButton(
                                onClick = onOpenOrderHistory,
                                enabled = actionsEnabled,
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.ReceiptLong,
                                    contentDescription = "历史订单",
                                )
                            }
                        }
                        balanceMessage?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeading(
    title: String,
    supporting: String?,
    onAddDevice: () -> Unit,
    actionsEnabled: Boolean,
    onOpenAbout: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Devices,
            contentDescription = "我的设备",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(26.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            supporting?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        TextButton(
            onClick = onAddDevice,
            enabled = actionsEnabled,
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = "添加设备",
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("添加设备")
        }
        TextButton(onClick = onOpenAbout, enabled = actionsEnabled) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = "关于",
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("关于")
        }
    }
}

@Composable
private fun EmptyDeviceCard(
    onAddDevice: () -> Unit,
    enabled: Boolean,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.WaterDrop,
                    contentDescription = "热水设备",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Text("开始添加你的第一台设备", style = MaterialTheme.typography.titleMedium)
            }
            OutlinedButton(onClick = onAddDevice, enabled = enabled) { Text("去添加设备") }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SavedDeviceCard(
    device: SavedDevice,
    checked: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onLongClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                enabled = enabled,
                onClick = {},
                onLongClick = onLongClick,
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Devices,
                    contentDescription = "蓝牙设备",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.LocationOn,
                    contentDescription = "设备位置",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = deviceLocationText(device),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = { onToggle(!checked) },
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
            ) {
                Icon(
                    imageVector = Icons.Outlined.WaterDrop,
                    contentDescription = if (checked) "结束洗澡" else "开始洗澡",
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (checked) "结束洗澡" else "开始洗澡")
            }
        }
    }
}

private fun deviceLocationText(device: SavedDevice): String {
    return buildList {
        device.buildingName.takeIf { it.isNotBlank() }?.let(::add)
        device.floorName.takeIf { it.isNotBlank() }?.let(::add)
        device.roomName.takeIf { it.isNotBlank() }?.let(::add)
    }.joinToString(separator = "").ifBlank { "位置暂未获取" }
}

@Composable
private fun LoadingOverlay(
    message: String,
    scrimAlpha: Float,
) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha)),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.widthIn(min = 180.dp),
                shape = RoundedCornerShape(4.dp),
                color = Color.Black.copy(alpha = 0.82f),
                contentColor = Color.White,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(44.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f),
                        strokeWidth = 4.dp,
                        strokeCap = StrokeCap.Round,
                    )
                    Text(message, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
