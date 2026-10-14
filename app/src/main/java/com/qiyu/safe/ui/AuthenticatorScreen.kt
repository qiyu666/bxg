package com.qiyu.safe.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.qiyu.safe.data.VaultRepository
import kotlinx.coroutines.delay
import com.qiyu.safe.security.Security
import com.qiyu.safe.security.Totp

/** 微软验证器绑定页：扫码（或手动输入密钥）把保险柜添加进验证器，并做一次动态码校验。 */
@Composable
fun AuthenticatorScreen() {
    val state = VaultRepository.uiState.collectAsState()
    var gen by remember { mutableStateOf(0) }
    val uri = remember(gen) { VaultRepository.authenticatorUri() }
    val secret = remember(gen) { VaultRepository.totpSecret() }
    val qrBitmap = remember(gen) {
        try {
            val u = VaultRepository.authenticatorUri() ?: return@remember null
            Security.toQrBitmap(u, 440).asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }
    var code by remember { mutableStateOf("") }
    var verifyMsg by remember { mutableStateOf<String?>(null) }
    var copyMsg by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(copyMsg) {
        if (copyMsg != null) {
            delay(2000)
            copyMsg = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("绑定微软验证器", style = MaterialTheme.typography.titleLarge)
        Text(
            "用微软验证器（Microsoft Authenticator）扫码或手动输入下方密钥，" +
                "把它作为本保险柜的动态口令来源。每次存入 / 取出都要输入它生成的 6 位动态码。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                when {
                    uri == null -> Text("暂无验证器密钥，请在设置中重新绑定。", style = MaterialTheme.typography.bodyMedium)
                    qrBitmap == null -> Text("二维码生成失败，请手动输入下方密钥。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    else -> {
                        Image(
                            bitmap = qrBitmap,
                            contentDescription = "验证器二维码",
                            modifier = Modifier.size(220.dp)
                        )
                        Text("使用微软验证器：添加其他账号 → 其他 → 扫描条码，或直接输入下方密钥。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("手动输入密钥", style = MaterialTheme.typography.titleSmall)
                Text("发行方：存钱保险柜", style = MaterialTheme.typography.bodyMedium)
                val s = secret
                if (s.isNullOrBlank()) {
                    Text("密钥：（无）", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "密钥：$s",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable {
                                clipboard.setText(AnnotatedString(s))
                                copyMsg = "已复制到剪贴板 ✓"
                            }
                        )
                        Text("（点击复制）", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    copyMsg?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("快速校验", style = MaterialTheme.typography.titleSmall)
                Text("在验证器中查看当前 6 位动态码，填入下方测试是否正确。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("6 位动态码") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                verifyMsg?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = if (verifyMsg?.contains("✓") == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                }
                TextButton(
                    onClick = {
                        val s = VaultRepository.totpSecret()
                        val appCode = s?.let { Totp.code(it) }
                        when {
                            s == null -> verifyMsg = "无密钥"
                            Totp.verify(s, code) -> verifyMsg = "校验通过 ✓ 可直接把这个码用于存取"
                            else -> verifyMsg = "未通过：你输入=$code，App 此刻算出=$appCode。两者不同 = 验证器里的密钥和 App 不一样，请删除验证器旧账号、重新添加下面这把密钥"
                        }
                    }
                ) { Text("校验") }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                VaultRepository.rebindAuthenticator()
                code = ""
                verifyMsg = "已生成新密钥！请立刻到微软验证器删除旧账号、添加下面这把新密钥。⚠️ 不要再点『重新生成密钥』——每点一次密钥就变一次，验证器就要重新添加。"
                gen++
            }, modifier = Modifier.weight(1f)) { Text("重新生成密钥") }
            OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.weight(1f)) {
                Text("账号：${state.value.username}")
            }
        }
    }
}
