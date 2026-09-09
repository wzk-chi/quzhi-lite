package com.quzhi.lite.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Announcement
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quzhi.lite.data.AppUpdate
import com.quzhi.lite.data.UpdateApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onOpenRepository: () -> Unit,
    onOpenUrl: (String) -> Unit,
    updateApi: UpdateApi,
    currentVersion: String,
) {
    var showAnnouncement by rememberSaveable { mutableStateOf(false) }
    var isCheckingForUpdates by remember { mutableStateOf(false) }
    var availableUpdate by remember { mutableStateOf<AppUpdate?>(null) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    fun checkForUpdates() {
        if (isCheckingForUpdates) {
            return
        }
        isCheckingForUpdates = true
        coroutineScope.launch {
            try {
                val update = updateApi.checkForUpdate(currentVersion)
                if (update == null) {
                    Toast.makeText(context, "当前是最新版本了", Toast.LENGTH_SHORT).show()
                } else {
                    availableUpdate = update
                }
            } catch (cancellationException: CancellationException) {
                throw cancellationException
            } catch (exception: Exception) {
                Toast.makeText(
                    context,
                    "检查更新失败：${exception.message ?: "请稍后重试"}",
                    Toast.LENGTH_LONG,
                ).show()
            } finally {
                isCheckingForUpdates = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("关于") },
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
            contentPadding = PaddingValues(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "趣智轻享",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "轻量、无广告的原生 Android 客户端",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "本项目完全免费，不收取软件费用，不设置会员、充值或其他付费入口。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "GitHub 仓库",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = GITHUB_REPOSITORY_URL,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = onOpenRepository,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                            contentDescription = "打开 GitHub 仓库",
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("打开 GitHub")
                    }
                }
            }
            item {
                Button(
                    onClick = { showAnnouncement = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Announcement,
                        contentDescription = "查看公告",
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("查看公告")
                }
            }
            item {
                OutlinedButton(
                    onClick = ::checkForUpdates,
                    enabled = !isCheckingForUpdates,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (isCheckingForUpdates) "正在检查更新…" else "检查更新")
                }
            }
        }
    }

    if (showAnnouncement) {
        ReadOnlyAnnouncementDialog(
            onClose = { showAnnouncement = false },
            onOpenRepository = onOpenRepository,
        )
    }

    availableUpdate?.let { update ->
        AlertDialog(
            onDismissRequest = { availableUpdate = null },
            title = { Text("发现新版本") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("最新版本：${update.versionName}")
                    Text(
                        text = "GitHub 地址：${update.githubUrl}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        availableUpdate = null
                        onOpenUrl(update.githubUrl)
                    },
                ) {
                    Text("更新")
                }
            },
            dismissButton = {
                TextButton(onClick = { availableUpdate = null }) {
                    Text("取消")
                }
            },
        )
    }
}
