package com.quzhi.lite.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quzhi.lite.data.ConsumptionOrder
import com.quzhi.lite.data.QuzhiApi
import com.quzhi.lite.data.UserSession
import kotlinx.coroutines.CancellationException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private sealed interface HistoryOrderUiState {
    data object Loading : HistoryOrderUiState
    data class Content(val orders: List<ConsumptionOrder>) : HistoryOrderUiState
    data class Error(val message: String) : HistoryOrderUiState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryOrderScreen(
    session: UserSession,
    api: QuzhiApi,
    onBack: () -> Unit,
) {
    var selectedMonth by rememberSaveable { mutableStateOf(currentMonth()) }
    var reloadKey by rememberSaveable { mutableStateOf(0) }
    var state by remember { mutableStateOf<HistoryOrderUiState>(HistoryOrderUiState.Loading) }
    val latestMonth = remember { currentMonth() }

    LaunchedEffect(session, selectedMonth, reloadKey) {
        state = HistoryOrderUiState.Loading
        try {
            state = HistoryOrderUiState.Content(
                api.fetchConsumptionOrders(session, selectedMonth),
            )
        } catch (cancellationException: CancellationException) {
            throw cancellationException
        } catch (error: Exception) {
            state = HistoryOrderUiState.Error(
                error.message ?: "历史订单暂时无法获取",
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("历史订单") },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            MonthSelector(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 4.dp),
                month = selectedMonth,
                latestMonth = latestMonth,
                canGoForward = selectedMonth < latestMonth,
                onPrevious = { selectedMonth = shiftMonth(selectedMonth, -1) },
                onNext = { selectedMonth = shiftMonth(selectedMonth, 1) },
                onMonthSelected = { selectedMonth = it },
            )

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clipToBounds(),
                contentPadding = PaddingValues(start = 20.dp, top = 6.dp, end = 20.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when (val currentState = state) {
                    HistoryOrderUiState.Loading -> {
                        item { LoadingOrders() }
                    }

                    is HistoryOrderUiState.Error -> {
                        item {
                            ErrorOrders(
                                message = currentState.message,
                                onRetry = { reloadKey += 1 },
                            )
                        }
                    }

                    is HistoryOrderUiState.Content -> {
                        if (currentState.orders.isEmpty()) {
                            item { EmptyOrders() }
                        } else {
                            items(currentState.orders) { order ->
                                ConsumptionOrderCard(order)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthSelector(
    modifier: Modifier = Modifier,
    month: String,
    latestMonth: String,
    canGoForward: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onMonthSelected: (String) -> Unit,
) {
    var showMonthPicker by rememberSaveable { mutableStateOf(false) }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(
                imageVector = Icons.Outlined.ChevronLeft,
                contentDescription = "上个月",
            )
        }

        OutlinedButton(
            onClick = { showMonthPicker = true },
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 12.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.CalendarMonth,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = monthLabel(month))
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Outlined.ExpandMore,
                contentDescription = "选择月份",
                modifier = Modifier.size(20.dp),
            )
        }

        IconButton(onClick = onNext, enabled = canGoForward) {
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = "下个月",
            )
        }
    }

    if (showMonthPicker) {
        MonthPickerDialog(
            selectedMonth = month,
            latestMonth = latestMonth,
            onDismiss = { showMonthPicker = false },
            onMonthSelected = { selectedMonth ->
                onMonthSelected(selectedMonth)
                showMonthPicker = false
            },
        )
    }
}

@Composable
private fun MonthPickerDialog(
    selectedMonth: String,
    latestMonth: String,
    onDismiss: () -> Unit,
    onMonthSelected: (String) -> Unit,
) {
    var pickerYear by rememberSaveable { mutableStateOf(monthYear(selectedMonth)) }
    val latestYear = monthYear(latestMonth)

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择月份") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { pickerYear -= 1 }) {
                        Icon(
                            imageVector = Icons.Outlined.ChevronLeft,
                            contentDescription = "上一年",
                        )
                    }
                    Text(
                        text = "${pickerYear}年",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    IconButton(
                        onClick = { pickerYear += 1 },
                        enabled = pickerYear < latestYear,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ChevronRight,
                            contentDescription = "下一年",
                        )
                    }
                }
                (1..12).chunked(3).forEach { months ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        months.forEach { monthNumber ->
                            val monthKey = monthValue(pickerYear, monthNumber)
                            FilterChip(
                                selected = monthKey == selectedMonth,
                                onClick = { onMonthSelected(monthKey) },
                                enabled = monthKey <= latestMonth,
                                label = { Text("${monthNumber}月") },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun LoadingOrders() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 56.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(32.dp))
            Text(
                text = "正在加载历史订单…",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ErrorOrders(
    message: String,
    onRetry: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
            )
            Button(onClick = onRetry) {
                Text("重新加载")
            }
        }
    }
}

@Composable
private fun EmptyOrders() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.AccountBalanceWallet,
                contentDescription = "历史订单",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "本月暂无热水消费记录",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ConsumptionOrderCard(order: ConsumptionOrder) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "热水消费",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "-${order.formattedAmount()} 元",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = "订单号：${order.orderNo}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (order.location.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.LocationOn,
                        contentDescription = "设备位置",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(order.location)
                }
            }
            Text(
                text = "使用时间：${order.consumeDate}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${order.settlementText()} · ${order.runStatusText()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun currentMonth(): String {
    return SimpleDateFormat("yyyy-MM", Locale.ROOT).format(Calendar.getInstance().time)
}

private fun monthLabel(month: String): String {
    val parts = month.split("-")
    return "${parts[0].toInt()}年${parts[1].toInt()}月"
}

private fun monthYear(month: String): Int {
    return month.substringBefore("-").toInt()
}

private fun monthValue(year: Int, month: Int): String {
    return String.format(Locale.ROOT, "%04d-%02d", year, month)
}

private fun shiftMonth(month: String, offset: Int): String {
    val parts = month.split("-")
    val calendar = Calendar.getInstance().apply {
        set(Calendar.YEAR, parts[0].toInt())
        set(Calendar.MONTH, parts[1].toInt() - 1)
        set(Calendar.DAY_OF_MONTH, 1)
    }
    calendar.add(Calendar.MONTH, offset)
    return String.format(
        Locale.ROOT,
        "%04d-%02d",
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH) + 1,
    )
}
