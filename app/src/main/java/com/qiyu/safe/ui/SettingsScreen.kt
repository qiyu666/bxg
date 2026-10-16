package com.qiyu.safe.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.qiyu.safe.data.AppLock
import com.qiyu.safe.data.OpResult
import com.qiyu.safe.data.QuickTxn
import com.qiyu.safe.data.VaultRepository
import com.qiyu.safe.data.formatYuan
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 设置页：修改支付密码、了解规则、退出登录。 */
@Composable
fun SettingsScreen() {
    val state = VaultRepository.uiState.collectAsState()
    var oldPass by remember { mutableStateOf("") }
    var newPass by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    var goalYuan by remember { mutableStateOf("") }
    val context = LocalContext.current
    var backupMsg by remember { mutableStateOf<String?>(null) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<String?>(null) }
    var exportPw by remember { mutableStateOf("") }
    val autoLock by AppLock.autoLockEnabled.collectAsState()
    val quickTxn by QuickTxn.enabled.collectAsState()
    var showQuickAuth by remember { mutableStateOf(false) }
    var quickMsg by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val genResult = runCatching { VaultRepository.exportBackup(exportPw) }
        val json = genResult.getOrNull()
        if (json.isNullOrEmpty()) {
            val reason = genResult.exceptionOrNull()
                ?.let { "生成失败：${it::class.java.simpleName} ${it.message}" }
                ?: "内容为空（未登录或无数据）"
            backupMsg = "导出失败：$reason"
            return@rememberLauncherForActivityResult
        }
        try {
            val bytes = json.toByteArray(Charsets.UTF_8)
            // 兜底：先把加密内容复制到剪贴板，SAF 写不进文件也不会丢数据
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("savings-vault-backup", json))
            }
            val os = context.contentResolver.openOutputStream(uri)
            if (os == null) {
                backupMsg = "文件写入失败（无输出流），但内容已复制到剪贴板，可粘贴保存"
                return@rememberLauncherForActivityResult
            }
            os.use {
                it.write(bytes)
                it.flush()
            }
            // 回读校验真实落盘字节数
            val written = context.contentResolver.openInputStream(uri)?.use { it.readBytes().size } ?: -1
            if (written == bytes.size) {
                backupMsg = "已导出加密备份（落盘 $written 字节，务必记好支付密码；内容也已复制到剪贴板）"
            } else {
                backupMsg = "已生成 ${bytes.size} 字节，但文件落盘仅 $written 字节——请粘贴「已复制」的剪贴板内容保存"
            }
        } catch (e: Exception) {
            backupMsg = "写入文件失败：${e::class.java.simpleName} ${e.message}（内容已复制到剪贴板，可粘贴保存）"
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val text = try {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            } catch (e: Exception) {
                null
            }
            if (text.isNullOrBlank()) {
                backupMsg = "无法读取所选文件"
            } else {
                pendingImport = text
                showImportDialog = true
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("设置", style = MaterialTheme.typography.titleLarge)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("关于", style = MaterialTheme.typography.titleSmall)
                Text("· 所有数据仅保存在本设备的应用私有存储，不上传网络。", style = MaterialTheme.typography.bodySmall)
                Text("· 死期存单到期后会自动转成活期，可在首页查看。", style = MaterialTheme.typography.bodySmall)
                Text("· 存入 / 取出都需要「微软验证器动态码 + 支付密码」双重校验。", style = MaterialTheme.typography.bodySmall)
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("修改支付密码", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = oldPass,
                    onValueChange = { oldPass = it },
                    label = { Text("原支付密码") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = newPass,
                    onValueChange = { newPass = it },
                    label = { Text("新支付密码（至少 6 位）") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = { Text("确认新支付密码") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                msg?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = if (it.startsWith("已")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                }
                Button(onClick = {
                    if (newPass != confirm) {
                        msg = "两次新密码不一致"
                        return@Button
                    }
                    when (val r = VaultRepository.changePaymentPassword(oldPass, newPass)) {
                        is OpResult.Ok -> { msg = "已更新"; oldPass = ""; newPass = ""; confirm = "" }
                        is OpResult.Err -> msg = r.message
                    }
                }) { Text("保存新密码") }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("储蓄目标", style = MaterialTheme.typography.titleSmall)
                Text(
                    "当前目标：" + if (state.value.snapshot.goalFen > 0) formatYuan(state.value.snapshot.goalFen) else "未设置",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = goalYuan,
                    onValueChange = { goalYuan = it },
                    label = { Text("目标金额（元），填 0 清除目标") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        val fen = parseYuanToFen(goalYuan) ?: 0L
                        VaultRepository.setGoal(fen)
                        goalYuan = ""
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("保存目标") }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("数据备份（加密）", style = MaterialTheme.typography.titleSmall)
                Text(
                    "导出 / 导入加密 JSON 备份。文件用你的支付密码加密（AES-256），输错密码无法打开。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { showExportDialog = true }, modifier = Modifier.weight(1f)) { Text("导出 JSON") }
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*")) },
                        modifier = Modifier.weight(1f)
                    ) { Text("导入 JSON") }
                }
                backupMsg?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (it.startsWith("已")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text("切后台 / 冷启动自动锁定", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "关闭后回到应用不再要求解锁。存取款仍用「支付密码 + 验证器动态码」双重校验。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = autoLock, onCheckedChange = { AppLock.setAutoLock(it) })
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "快捷交易（指纹存取款）",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = quickTxn,
                        onCheckedChange = { on ->
                            if (on) showQuickAuth = true
                            else {
                                QuickTxn.disable()
                                quickMsg = "已关闭"
                            }
                        }
                    )
                }
                Text(
                    "开启后，存入 / 取出可用指纹或面容一步确认，无需再输支付密码与验证器码；开启需一次性双重授权。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                quickMsg?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (it.startsWith("已") || it.startsWith("快捷")) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        OutlinedButton(
            onClick = { AppLock.lock() },
            modifier = Modifier.fillMaxWidth()
        ) { Text("立即锁定（需生物识别 / 支付密码解锁）") }

        OutlinedButton(
            onClick = { VaultRepository.logout() },
            modifier = Modifier.fillMaxWidth()
        ) { Text("退出登录") }
    }

    if (showExportDialog) {
        BackupPasswordDialog(
            title = "导出加密备份",
            hint = "输入你的支付密码用于加密备份。导入时需再次输入同一密码。",
            confirmLabel = "加密并导出",
            onDismiss = { showExportDialog = false },
            onConfirm = { pw ->
                exportPw = pw
                showExportDialog = false
                val date = SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(Date())
                exportLauncher.launch("savings-vault-backup-$date.json")
            }
        )
    }
    if (showImportDialog) {
        BackupPasswordDialog(
            title = "导入加密备份",
            hint = "输入导出时使用的支付密码以解密。成功后将切换到此账号。",
            confirmLabel = "解密并导入",
            onDismiss = {
                showImportDialog = false
                pendingImport = null
            },
            onConfirm = { pw ->
                val target = pendingImport
                showImportDialog = false
                pendingImport = null
                if (target != null) {
                    when (val r = VaultRepository.importBackup(target, pw)) {
                        is OpResult.Ok -> backupMsg = r.message
                        is OpResult.Err -> backupMsg = r.message
                    }
                }
            }
        )
    }
    if (showQuickAuth) {
        QuickTxnAuthDialog(
            onConfirm = { pw, totp ->
                showQuickAuth = false
                when (val r = VaultRepository.authorizeQuickTxn(pw, totp)) {
                    is OpResult.Ok -> quickMsg = "快捷交易已开启，可在首页用指纹存取款"
                    is OpResult.Err -> quickMsg = r.message
                }
            },
            onDismiss = { showQuickAuth = false }
        )
    }
}

/** 备份口令输入对话框。 */
@Composable
private fun BackupPasswordDialog(
    title: String,
    hint: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var pw by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = pw,
                    onValueChange = { pw = it },
                    label = { Text("支付密码") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(pw) }, enabled = pw.length >= 6) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/** 快捷交易授权对话框：支付密码 + 验证器动态码 双重校验。 */
@Composable
private fun QuickTxnAuthDialog(
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var pw by remember { mutableStateOf("") }
    var totp by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("开启快捷交易") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "需输入支付密码 + 微软验证器 6 位动态码，验证通过后开启快捷交易。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = pw,
                    onValueChange = { pw = it },
                    label = { Text("支付密码") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = totp,
                    onValueChange = { totp = it },
                    label = { Text("验证器动态码（6 位）") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(pw, totp) }, enabled = pw.length >= 6 && totp.length == 6) {
                Text("验证并开启")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
