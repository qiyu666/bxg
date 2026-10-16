package com.qiyu.safe.ui

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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.qiyu.safe.data.OpResult
import com.qiyu.safe.data.QuickTxn
import com.qiyu.safe.data.TermType
import com.qiyu.safe.data.VaultRepository
import com.qiyu.safe.data.formatYuan
import java.math.BigDecimal

/** 首页：余额总览 + 存入 / 取出（双重校验）。 */
@Composable
fun HomeScreen(activity: FragmentActivity, onOpenAuthenticator: () -> Unit) {
    val state = VaultRepository.uiState.collectAsState()
    val s = state.value
    var showDeposit by remember { mutableStateOf(false) }
    var showWithdraw by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text("总余额", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    formatYuan(s.snapshot.balances.totalFen),
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BalanceCard("活期可用", s.snapshot.balances.demandFen, Modifier.weight(1f))
            BalanceCard("死期锁定", s.snapshot.balances.fixedLockedFen, Modifier.weight(1f))
        }

        val goal = s.snapshot.goalFen
        if (goal > 0) {
            val total = s.snapshot.balances.totalFen
            val progress = if (total >= goal) 1f else (total.toFloat() / goal).coerceIn(0f, 1f)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text("储蓄目标", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text(if (total >= goal) "已达成 🎉" else "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("已存 ${formatYuan(total)} / 目标 ${formatYuan(goal)}", style = MaterialTheme.typography.bodySmall)
                        Text("还差 ${formatYuan((goal - total).coerceAtLeast(0))}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        if (!s.totpConfigured) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("尚未绑定微软验证器", style = MaterialTheme.typography.titleSmall)
                        Text("存入 / 取出前请先在「验证器」页扫码绑定。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = onOpenAuthenticator) { Text("去绑定") }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { showDeposit = true }, modifier = Modifier.weight(1f)) { Text("＋ 存入") }
            OutlinedButton(onClick = { showWithdraw = true }, modifier = Modifier.weight(1f)) { Text("－ 取出") }
        }

        Text("存单明细", style = MaterialTheme.typography.titleMedium)
        if (s.snapshot.deposits.isEmpty()) {
            Text("还没有存单。点击上方「存入」开始存钱。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            s.snapshot.deposits.forEach { d ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            val tag = if (d.term == TermType.DEMAND) "活期" else
                                if (d.converted) "死期(已转活期)" else "死期 ${d.periodMonths} 个月"
                            Text(tag, style = MaterialTheme.typography.titleSmall)
                            val mature = if (d.matureTs > 0) {
                                "到期 " + java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).format(java.util.Date(d.matureTs))
                            } else "随存随取"
                            Text(mature, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(formatYuan(d.amountFen), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }

    if (showDeposit) DepositDialog(activity, onDismiss = { showDeposit = false })
    if (showWithdraw) WithdrawDialog(activity, onDismiss = { showWithdraw = false })
}

@Composable
private fun BalanceCard(title: String, fen: Long, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatYuan(fen), style = MaterialTheme.typography.titleLarge)
        }
    }
}

/** 存入对话框：金额 + 活期/死期 + 期限 + 备注 + 验证器动态码 + 支付密码。 */
@Composable
fun DepositDialog(activity: FragmentActivity, onDismiss: () -> Unit) {
    val quickTxn = QuickTxn.enabled.collectAsState()
    var amountYuan by remember { mutableStateOf("") }
    var term by remember { mutableStateOf(TermType.DEMAND) }
    var periodMonths by remember { mutableLongStateOf(3L) }
    var note by remember { mutableStateOf("") }
    var totp by remember { mutableStateOf("") }
    var payPass by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val options = listOf(3L to "3 个月", 6L to "6 个月", 12L to "1 年", 24L to "2 年")

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("存入") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = amountYuan,
                    onValueChange = { amountYuan = it },
                    label = { Text("金额（元）") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                if (quickTxn.value) {
                    Button(
                        onClick = {
                            val fen = parseYuanToFen(amountYuan)
                            if (fen == null || fen <= 0) {
                                error = "请先输入大于 0 的金额"
                                return@Button
                            }
                            busy = true
                            BiometricHelper.confirm(
                                activity,
                                activity,
                                onSuccess = {
                                    when (val r = VaultRepository.quickDeposit(fen, term, periodMonths.toInt(), note)) {
                                        is OpResult.Ok -> onDismiss()
                                        is OpResult.Err -> {
                                            error = r.message
                                            busy = false
                                        }
                                    }
                                },
                                onFail = { fail ->
                                    error = "$fail，请改用支付密码 + 验证器码"
                                    busy = false
                                }
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("👆 指纹快速存入（快捷交易已开启）") }
                }
                Text("期限", style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = term == TermType.DEMAND, onClick = { term = TermType.DEMAND }, label = { Text("活期") })
                    FilterChip(selected = term == TermType.FIXED, onClick = { term = TermType.FIXED }, label = { Text("死期") })
                }
                if (term == TermType.FIXED) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        options.forEach { (m, label) ->
                            FilterChip(
                                selected = periodMonths == m,
                                onClick = { periodMonths = m },
                                label = { Text(label) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注（可选）") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = totp,
                    onValueChange = { totp = it },
                    label = { Text("微软验证器动态码（6 位）") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                OutlinedTextField(
                    value = payPass,
                    onValueChange = { payPass = it },
                    label = { Text("支付密码") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    busy = true
                    val fen = parseYuanToFen(amountYuan)
                    when {
                        fen == null || fen <= 0 -> {
                            error = "请输入大于 0 的金额"; busy = false
                        }

                        else -> {
                            val r = VaultRepository.deposit(fen, term, periodMonths.toInt(), note, totp, payPass)
                            when (r) {
                                is OpResult.Ok -> { onDismiss() }
                                is OpResult.Err -> { error = r.message; busy = false }
                            }
                        }
                    }
                },
                enabled = !busy
            ) { Text("存入") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") }
        }
    )
}

/** 取出对话框：金额 + 验证器动态码 + 支付密码。 */
@Composable
fun WithdrawDialog(activity: FragmentActivity, onDismiss: () -> Unit) {
    val quickTxn = QuickTxn.enabled.collectAsState()
    var amountYuan by remember { mutableStateOf("") }
    var totp by remember { mutableStateOf("") }
    var payPass by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("取出") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("活期可用：", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = amountYuan,
                    onValueChange = { amountYuan = it },
                    label = { Text("金额（元）") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                if (quickTxn.value) {
                    Button(
                        onClick = {
                            val fen = parseYuanToFen(amountYuan)
                            if (fen == null || fen <= 0) {
                                error = "请先输入大于 0 的金额"
                                return@Button
                            }
                            busy = true
                            BiometricHelper.confirm(
                                activity,
                                activity,
                                onSuccess = {
                                    when (val r = VaultRepository.quickWithdraw(fen)) {
                                        is OpResult.Ok -> onDismiss()
                                        is OpResult.Err -> {
                                            error = r.message
                                            busy = false
                                        }
                                    }
                                },
                                onFail = { fail ->
                                    error = "$fail，请改用支付密码 + 验证器码"
                                    busy = false
                                }
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("👆 指纹快速取出（快捷交易已开启）") }
                }
                OutlinedTextField(
                    value = totp,
                    onValueChange = { totp = it },
                    label = { Text("微软验证器动态码（6 位）") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                OutlinedTextField(
                    value = payPass,
                    onValueChange = { payPass = it },
                    label = { Text("支付密码") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    busy = true
                    val fen = parseYuanToFen(amountYuan)
                    when {
                        fen == null || fen <= 0 -> {
                            error = "请输入大于 0 的金额"; busy = false
                        }

                        else -> {
                            val r = VaultRepository.withdraw(fen, totp, payPass)
                            when (r) {
                                is OpResult.Ok -> onDismiss()
                                is OpResult.Err -> { error = r.message; busy = false }
                            }
                        }
                    }
                },
                enabled = !busy
            ) { Text("取出") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") }
        }
    )
}

/** 「100.50」-> 10050 分。 */
internal fun parseYuanToFen(s: String): Long? {
    return s.trim().toBigDecimalOrNull()?.let { it.multiply(BigDecimal(100)) }?.let { it.setScale(0, java.math.RoundingMode.HALF_UP).toLong() }
}
