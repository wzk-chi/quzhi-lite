package com.quzhi.lite.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private const val REQUIRED_READ_SECONDS = 5

@Composable
fun AnnouncementDialog(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
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
        title = { Text("使用须知") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = buildAnnotatedString {
                        append("此项目（智趣轻享）是个人为了兴趣而开发，")
                        withStyle(
                            SpanStyle(
                                fontWeight = FontWeight.Bold,
                                textDecoration = TextDecoration.Underline,
                            ),
                        ) {
                            append("不提供任何破解内容")
                        }
                        append("，仅供技术研究、学习、测试及个人非商业用途使用。请于下载后24小时内删除。")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "请先前往原平台注册后再使用本项目。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "不提供任何充值入口，需要充值请移步原平台入口。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "使用者在使用本项目时，应自行确认其行为符合相关法律法规、原平台的服务条款、开发者协议、接口使用规则以及其他适用的政策要求。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "使用本项目即表示使用者已阅读、理解并同意自行承担因使用本项目产生的相关风险与责任。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (secondsRemaining == 0) {
                        "已阅读 5 秒，可以点击同意。"
                    } else {
                        "请阅读 ${secondsRemaining} 秒后再点击同意。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
