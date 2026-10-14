package com.qiyu.safe.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.qiyu.safe.data.OpResult
import com.qiyu.safe.data.VaultRepository

/** 登录 / 注册页。用户必须先登录或注册才能使用其他功能。 */
@Composable
fun AuthScreen() {
    var register by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("存钱保险柜", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Text("登录或注册后即可使用全部功能", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                modifier = Modifier
                    .width(240.dp)
                    .padding(top = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TabToggle("登录", !register) { register = false; error = null }
                TabToggle("注册", register) { register = true; error = null }
            }

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("用户名") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth480()
            )
            if (register) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("邮箱（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth480()
                )
            }
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("支付密码") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth480()
            )
            if (register) {
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = { Text("确认支付密码") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth480()
                )
            }

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }

            Button(
                onClick = {
                    busy = true
                    val result: OpResult
                    if (register) {
                        if (password != confirm) {
                            error = "两次输入的支付密码不一致"
                            busy = false
                            return@Button
                        }
                        result = VaultRepository.register(username, email, password)
                    } else {
                        result = VaultRepository.login(username, password)
                    }
                    busy = false
                    when (result) {
                        is OpResult.Ok -> error = null
                        is OpResult.Err -> error = result.message
                    }
                },
                enabled = !busy,
                modifier = Modifier
                    .fillMaxWidth480()
                    .padding(top = 12.dp)
            ) {
                Text(if (register) "注册并登录" else "登录")
            }
            Text(
                "每次存入 / 取出都需要「微软验证器动态码 + 支付密码」双重校验。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

@Composable
private fun TabToggle(label: String, active: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(label, color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
}

/** 让表单控件在手机上占满宽度，在平板上限制一个舒适的最大宽度。 */
private fun Modifier.fillMaxWidth480(): Modifier =
    this.fillMaxWidth()
