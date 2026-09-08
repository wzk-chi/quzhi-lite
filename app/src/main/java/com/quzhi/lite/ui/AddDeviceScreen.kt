package com.quzhi.lite.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.quzhi.lite.data.ApiException
import com.quzhi.lite.data.BluetoothDeviceCandidate
import com.quzhi.lite.data.BluetoothDiscovery
import com.quzhi.lite.data.DeviceInfoApi
import com.quzhi.lite.data.DeviceLocation
import com.quzhi.lite.data.SavedDevice
import com.quzhi.lite.data.UserSession
import com.quzhi.lite.data.extractSnCode
import com.quzhi.lite.data.hasAddress
import com.quzhi.lite.data.normalizeBluetoothAddress
import com.quzhi.lite.data.upsertBluetoothDevice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

private const val MAX_DISCOVERED_DEVICES = 5
private const val MAX_LOOKUP_CANDIDATES = 20

private enum class AddMode {
    Choice,
    Manual,
    Scanner,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDeviceScreen(
    session: UserSession,
    deviceInfoApi: DeviceInfoApi,
    bluetoothDiscovery: BluetoothDiscovery,
    onBack: () -> Unit,
    onDeviceSaved: (SavedDevice) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mode by rememberSaveable { mutableStateOf(AddMode.Choice) }
    var snCode by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var bluetoothScanning by remember { mutableStateOf(false) }
    var resolvingLocations by remember { mutableStateOf(false) }
    val discoveredDevicesState = remember {
        mutableStateOf<List<BluetoothDeviceCandidate>>(emptyList())
    }
    val deviceLocationsState = remember {
        mutableStateOf<Map<String, DeviceLocation>>(emptyMap())
    }
    val scanGenerationState = remember { mutableStateOf(0) }
    val queuedAddressesState = remember { mutableStateOf<Set<String>>(emptySet()) }
    val locationQueueState = remember {
        mutableStateOf<Channel<BluetoothDeviceCandidate>?>(null)
    }
    val locationWorkerState = remember { mutableStateOf<Job?>(null) }
    val discoveredDevices = discoveredDevicesState.value
    val deviceLocations = deviceLocationsState.value
    val scanning = bluetoothScanning || resolvingLocations
    val refreshTransition = rememberInfiniteTransition(label = "scanRefresh")
    val refreshRotation by refreshTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "scanRefreshRotation",
    )

    fun stopBluetoothScan(messageText: String? = null) {
        scanGenerationState.value += 1
        locationQueueState.value?.close()
        locationWorkerState.value?.cancel()
        locationQueueState.value = null
        locationWorkerState.value = null
        queuedAddressesState.value = emptySet()
        bluetoothDiscovery.stop()
        bluetoothScanning = false
        resolvingLocations = false
        if (messageText != null) {
            message = messageText
        }
    }

    fun startBluetoothScan() {
        val scanGeneration = scanGenerationState.value + 1
        scanGenerationState.value = scanGeneration
        locationQueueState.value?.close()
        locationWorkerState.value?.cancel()
        discoveredDevicesState.value = emptyList()
        deviceLocationsState.value = emptyMap()
        queuedAddressesState.value = emptySet()

        val locationQueue = Channel<BluetoothDeviceCandidate>(Channel.UNLIMITED)
        locationQueueState.value = locationQueue
        locationWorkerState.value = scope.launch {
            for (device in locationQueue) {
                if (scanGeneration != scanGenerationState.value) {
                    break
                }
                if (discoveredDevicesState.value.size >= MAX_DISCOVERED_DEVICES) {
                    break
                }
                resolvingLocations = true
                val location = try {
                    val lookupValue = extractSnCode(device.name) ?: device.normalizedAddress
                    deviceInfoApi.queryByMac(session, lookupValue)
                } catch (cancellationException: CancellationException) {
                    throw cancellationException
                } catch (_: Exception) {
                    null
                }
                if (scanGeneration != scanGenerationState.value) {
                    break
                }
                if (location == null || !location.hasAddress()) {
                    continue
                }
                deviceLocationsState.value += device.normalizedAddress to location
                discoveredDevicesState.value = upsertBluetoothDevice(
                    devices = discoveredDevicesState.value,
                    device = device,
                )
            }
            if (scanGeneration == scanGenerationState.value) {
                resolvingLocations = false
            }
        }

        message = null
        bluetoothScanning = true
        val started = bluetoothDiscovery.start(
            onDevice = { device ->
                if (scanGeneration != scanGenerationState.value) {
                    return@start
                }
                val queued = queuedAddressesState.value
                if (queued.size >= MAX_LOOKUP_CANDIDATES ||
                    queued.contains(device.normalizedAddress) ||
                    discoveredDevicesState.value.any {
                        it.normalizedAddress == device.normalizedAddress
                    }
                ) {
                    return@start
                }
                queuedAddressesState.value = queued + device.normalizedAddress
                locationQueue.trySend(device)
            },
            onFinished = {
                bluetoothScanning = false
                message = null
            },
            onError = { errorMessage ->
                bluetoothScanning = false
                resolvingLocations = false
                message = errorMessage
            },
        )
        if (!started) {
            locationQueue.close()
            locationWorkerState.value?.cancel()
            locationQueueState.value = null
            locationWorkerState.value = null
            bluetoothScanning = false
            resolvingLocations = false
        }
    }

    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        if (bluetoothDiscovery.isBluetoothEnabled()) {
            startBluetoothScan()
        } else {
            message = "请先开启系统蓝牙"
        }
    }

    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (BluetoothDiscovery.hasRequiredPermissions(context) && grants.values.all { it }) {
            if (bluetoothDiscovery.isBluetoothEnabled()) {
                startBluetoothScan()
            } else {
                message = "请先开启系统蓝牙"
                enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            }
        } else {
            message = "需要允许附近设备权限才能搜索蓝牙设备"
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            mode = AddMode.Scanner
            message = null
        } else {
            message = "需要允许相机权限才能扫码添加"
        }
    }

    fun beginBluetoothScan() {
        if (BluetoothDiscovery.hasRequiredPermissions(context)) {
            if (bluetoothDiscovery.isBluetoothEnabled()) {
                startBluetoothScan()
            } else {
                message = "请先开启系统蓝牙"
                enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            }
        } else {
            bluetoothPermissionLauncher.launch(BluetoothDiscovery.requiredPermissions())
        }
    }

    fun saveAfterConnection(rawDeviceIdentifier: String) {
        val enteredIdentifier = rawDeviceIdentifier.trim().uppercase()
        if (!isValidDeviceIdentifier(enteredIdentifier)) {
            message = "请输入有效的 12 位 SN 或 MAC 地址"
            return
        }
        if (loading || bluetoothScanning || resolvingLocations) {
            return
        }
        loading = true
        message = "正在测试设备连接…"
        scope.launch {
            try {
                val location = deviceInfoApi.queryByMac(session, enteredIdentifier)
                    ?: throw ApiException("未查询到该 SN/MAC 对应的设备")
                if (!location.hasAddress()) {
                    throw ApiException("该设备暂未返回楼栋、楼层或房间信息")
                }
                val resolvedSn = location.snCode?.takeIf { it.isNotBlank() } ?: enteredIdentifier
                onDeviceSaved(
                    SavedDevice(
                        snCode = resolvedSn,
                        name = location.deviceName.orEmpty(),
                        buildingName = location.buildingName.orEmpty(),
                        floorName = location.floorName.orEmpty(),
                        roomName = location.roomName.orEmpty(),
                        projectName = location.projectName.orEmpty(),
                    ),
                )
                onBack()
            } catch (cancellationException: CancellationException) {
                throw cancellationException
            } catch (error: Exception) {
                message = error.message ?: "设备连接失败，未保存"
            } finally {
                loading = false
            }
        }
    }

    fun handleQrCode(content: String) {
        val candidate = content.trim().takeLast(12).uppercase()
        if (candidate.length != 12 || candidate.any { !it.isDigit() && it !in 'A'..'F' }) {
            message = "二维码中未找到有效的 12 位 SN"
            return
        }
        mode = AddMode.Choice
        saveAfterConnection(candidate)
    }

    fun openScanner() {
        stopBluetoothScan(messageText = null)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            mode = AddMode.Scanner
            message = null
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(Unit) {
        beginBluetoothScan()
    }

    DisposableEffect(bluetoothDiscovery) {
        onDispose {
            stopBluetoothScan(messageText = null)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("添加设备") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Devices,
                            contentDescription = "添加设备",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp),
                        )
                        Text("选择添加方式", style = MaterialTheme.typography.titleLarge)
                    }
                    Text(
                        "设备连接成功后才会保存到我的设备。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(
                            onClick = ::openScanner,
                            modifier = Modifier.weight(1f),
                            enabled = !loading,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.QrCodeScanner,
                                contentDescription = "扫码添加",
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("扫码添加")
                        }
                        OutlinedButton(
                            onClick = {
                                stopBluetoothScan(messageText = null)
                                mode = AddMode.Manual
                                message = null
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !loading,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = "手动添加",
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("手动添加")
                        }
                    }
                }
            }
            if (mode == AddMode.Manual) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            OutlinedTextField(
                                value = snCode,
                                onValueChange = { value ->
                                    snCode = value.filter {
                                        it.isDigit() || it in 'A'..'F' || it in 'a'..'f' || it == ':'
                                    }.take(17).uppercase()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("设备 SN/MAC") },
                                placeholder = { Text("请输入设备 SN 或 MAC") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                                enabled = !loading,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Edit,
                                        contentDescription = "设备 SN/MAC",
                                    )
                                },
                            )
                            Button(
                                onClick = { saveAfterConnection(snCode) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !loading,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = "保存设备",
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (loading) "测试连接中…" else "保存设备")
                            }
                            TextButton(
                                onClick = { mode = AddMode.Choice },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "取消",
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("取消")
                            }
                        }
                    }
                }
            }
            if (mode == AddMode.Scanner) {
                item {
                    QrScanner(
                        onCodeDetected = ::handleQrCode,
                        onError = { errorMessage -> message = errorMessage },
                    )
                }
                item {
                    OutlinedButton(
                        onClick = { mode = AddMode.Choice },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("关闭扫码")
                    }
                }
            }
            message?.let { status ->
                item {
                    Text(
                        text = status,
                        color = if (status.contains("失败") || status.contains("未查询") || status.contains("未找到")) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            if (discoveredDevices.isNotEmpty() || bluetoothScanning || resolvingLocations) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = "搜索结果",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp),
                            )
                            Text(
                                "搜索结果",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        if (scanning) {
                            Text(
                                text = "扫描中…",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        IconButton(
                            onClick = ::beginBluetoothScan,
                            enabled = !loading,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = "刷新搜索结果",
                                modifier = Modifier.graphicsLayer {
                                    rotationZ = if (scanning) refreshRotation else 0f
                                },
                            )
                        }
                        IconButton(
                            onClick = { stopBluetoothScan() },
                            enabled = bluetoothScanning || resolvingLocations,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.StopCircle,
                                contentDescription = "停止搜索",
                            )
                        }
                    }
                    if (discoveredDevices.isNotEmpty()) {
                        Text(
                            "点击设备卡片保存到我的设备",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (discoveredDevices.isNotEmpty()) {
                    items(
                        items = discoveredDevices,
                        key = { it.normalizedAddress },
                    ) { device ->
                        DiscoveredDeviceCard(
                            device = device,
                            location = deviceLocations[device.normalizedAddress],
                            onClick = {
                                val location = deviceLocations[device.normalizedAddress]
                                if (location != null) {
                                    onDeviceSaved(
                                        SavedDevice(
                                            snCode = location.snCode?.takeIf { it.isNotBlank() }
                                                ?: device.snCode.orEmpty(),
                                            name = location.deviceName.orEmpty().ifBlank { device.name },
                                            address = device.address,
                                            normalizedAddress = normalizeBluetoothAddress(device.address),
                                            buildingName = location.buildingName.orEmpty(),
                                            floorName = location.floorName.orEmpty(),
                                            roomName = location.roomName.orEmpty(),
                                            projectName = location.projectName.orEmpty(),
                                            isBle = device.isBle,
                                            rssi = device.rssi,
                                        ),
                                    )
                                    onBack()
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoveredDeviceCard(
    device: BluetoothDeviceCandidate,
    location: DeviceLocation?,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Devices,
                    contentDescription = "蓝牙设备",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = location?.deviceName?.takeIf { it.isNotBlank() } ?: device.name,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.LocationOn,
                    contentDescription = "设备位置",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text("位置：${locationText(location)}")
            }
            Text(
                text = "MAC：${device.address} · ${if (device.isBle) "BLE" else "经典蓝牙"} · RSSI ${device.rssi}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun locationText(location: DeviceLocation?): String {
    if (location == null) {
        return "正在获取位置…"
    }
    return buildList {
        location.buildingName?.takeIf { it.isNotBlank() }?.let(::add)
        location.floorName?.takeIf { it.isNotBlank() }?.let(::add)
        location.roomName?.takeIf { it.isNotBlank() }?.let(::add)
    }.joinToString(separator = "").ifBlank { "位置暂未获取" }
}

private fun isValidDeviceIdentifier(value: String): Boolean {
    val parts = value.split(':')
    val compactLength = parts.sumOf(String::length)
    val isHex = parts.all { part ->
        part.all { character ->
            character.isDigit() || character in 'A'..'F'
        }
    }
    return compactLength == 12 && isHex &&
        (parts.size == 1 || (parts.size == 6 && parts.all { it.length == 2 }))
}
