package com.quzhi.lite.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

private const val REQUIRED_READ_SECONDS = 5

@Composable
fun AnnouncementDialog(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onOpenRepository: () -> Unit,
) {
    var secondsRemaining by rememberSaveable { mutableStateOf(REQUIRED_READ_SECONDS) }

    LaunchedEffect(Unit) {
        repeat(REQUIRED_READ_SECONDS) { elapsedSecond ->
            delay(1_000)
            secondsRemaining = REQUIRED_READ_SECONDS - elapsedSecond - 1
        }
    }

    AlertDialog(
        onDismissRequest = {},
        title = { Text("项目公告与使用须知") },
        text = {
            AnnouncementContent(
                onOpenRepository = onOpenRepository,
                footer = {
                    Text(
                        text = if (secondsRemaining == 0) {
                            "已阅读 5 秒，可以点击同意。"
                        } else {
                            "请阅读 ${secondsRemaining} 秒后再点击同意。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
        },
        confirmButton = {
            TextButton(
                onClick = onAccept,
                enabled = secondsRemaining == 0,
            ) {
                Text("同意")
            }
        },
        dismissButton = {
            TextButton(onClick = onDecline) {
                Text("不同意")
            }
        },
    )
}

@Composable
fun ReadOnlyAnnouncementDialog(
    onClose: () -> Unit,
    onOpenRepository: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("项目公告") },
        text = {
            AnnouncementContent(onOpenRepository = onOpenRepository)
        },
        confirmButton = {
            TextButton(onClick = onClose) {
                Text("关闭")
            }
        },
    )
}
