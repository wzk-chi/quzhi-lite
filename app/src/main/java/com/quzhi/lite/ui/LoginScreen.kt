package com.quzhi.lite.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.quzhi.lite.R
import com.quzhi.lite.data.QuzhiApi
import com.quzhi.lite.data.UserSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class LoginMode {
    Password,
    Sms,
}

@Composable
fun LoginScreen(
    api: QuzhiApi,
    onLoginSuccess: (UserSession) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var mode by rememberSaveable { mutableStateOf(LoginMode.Sms) }
    var phone by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var smsCode by rememberSaveable { mutableStateOf("") }
    var countdown by rememberSaveable { mutableStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val appName = stringResource(R.string.app_name)

    LaunchedEffect(countdown) {
        if (countdown > 0) {
            delay(1_000)
            countdown -= 1
        }
    }

    fun validPhone(): Boolean {
        return phone.length == 11 && phone.all(Char::isDigit)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 48.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Image(
                    painter = painterResource(id = R.mipmap.ic_launcher),
                    contentDescription = appName,
                    modifier = Modifier.size(40.dp),
                )
                Text(
                    text = appName,
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "登录后连接热水设备",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(28.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = mode == LoginMode.Sms,
                        onClick = { mode = LoginMode.Sms; message = null },
                        label = { Text("验证码登录") },
                    )
                    FilterChip(
                        selected = mode == LoginMode.Password,
                        onClick = { mode = LoginMode.Password; message = null },
                        label = { Text("密码登录") },
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it.filter(Char::isDigit).take(11) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("手机号") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Phone,
                            contentDescription = "手机号",
                        )
                    },
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (mode == LoginMode.Password) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("密码") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Lock,
                                contentDescription = "密码",
                            )
                        },
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = smsCode,
                            onValueChange = { smsCode = it.filter(Char::isDigit).take(6) },
                            modifier = Modifier.weight(1f),
                            label = { Text("验证码") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Sms,
                                    contentDescription = "验证码",
                                )
                            },
                        )
                        TextButton(
                            onClick = {
                                if (!validPhone()) {
                                    message = "请输入 11 位手机号"
                                    return@TextButton
                                }
                                loading = true
                                message = null
                                scope.launch {
                                    runCatching { api.sendVerificationCode(phone) }
                                        .onSuccess {
                                            countdown = 60
                                            message = "验证码已发送"
                                        }
                                        .onFailure { error ->
                                            message = error.message ?: "验证码发送失败"
                                        }
                                    loading = false
                                }
                            },
                            modifier = Modifier.align(Alignment.CenterVertically),
                            enabled = !loading && countdown == 0,
                        ) {
                            Text(if (countdown == 0) "获取验证码" else "${countdown}s")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = {
                        if (!validPhone()) {
                            message = "请输入 11 位手机号"
                            return@Button
                        }
                        if (mode == LoginMode.Password && password.isBlank()) {
                            message = "请输入密码"
                            return@Button
                        }
                        if (mode == LoginMode.Sms && smsCode.isBlank()) {
                            message = "请输入验证码"
                            return@Button
                        }
                        loading = true
                        message = null
                        scope.launch {
                            runCatching {
                                if (mode == LoginMode.Password) {
                                    api.loginByPassword(phone, password)
                                } else {
                                    api.loginBySms(phone, smsCode)
                                }
                            }
                                .onSuccess(onLoginSuccess)
                                .onFailure { error ->
                                    message = error.message ?: "登录失败"
                                }
                            loading = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !loading,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ArrowForward,
                        contentDescription = "登录",
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (loading) "处理中…" else "登录")
                }

                message?.let {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
}
