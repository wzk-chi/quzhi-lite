package com.quzhi.lite.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

internal const val GITHUB_REPOSITORY_URL = "https://github.com/wzk-chi/quzhi-lite"

@Composable
internal fun AnnouncementContent(
    onOpenRepository: () -> Unit,
    footer: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "趣智轻享是个人独立开发的轻量 Android 客户端，项目完全免费，不收取软件费用，不设置会员、充值或其他付费入口。",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "本项目仅供学习、技术研究、测试及个人非商业用途使用，不提供破解、绕过平台限制或未授权访问等功能。",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "首次使用前，请先在原平台完成账号注册和登录。本项目不提供原平台的账号注册、充值或其他账户服务；使用原平台服务产生的费用，以原平台规则和实际消费为准。",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "使用前请确认你的行为符合适用法律法规、原平台服务条款、开发者协议、接口使用规则及其他相关政策。",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "使用本项目产生的风险和责任由使用者自行承担。",
            style = MaterialTheme.typography.bodyMedium,
        )
        Column {
            Text(
                text = "项目地址",
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onOpenRepository) {
                Icon(
                    imageVector = Icons.Outlined.OpenInNew,
                    contentDescription = "打开 GitHub 仓库",
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(GITHUB_REPOSITORY_URL)
            }
        }
        footer?.invoke()
    }
}
