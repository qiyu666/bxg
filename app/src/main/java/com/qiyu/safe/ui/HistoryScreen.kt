package com.qiyu.safe.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.qiyu.safe.data.Transaction
import com.qiyu.safe.data.VaultRepository
import com.qiyu.safe.data.formatYuan
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val df = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT)

/** 资金流水页。 */
@Composable
fun HistoryScreen() {
    val state = VaultRepository.uiState.collectAsState()
    val txns = state.value.snapshot.transactions

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("资金流水", style = MaterialTheme.typography.titleLarge)
        if (txns.isEmpty()) {
            Text("暂无流水记录。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            txns.forEach { t ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t.kind.label, style = MaterialTheme.typography.titleSmall)
                            val time = df.format(Date(t.ts))
                            val note = if (t.note.isEmpty()) time else "$time · ${t.note}"
                            Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (t.kind != Transaction.Kind.CONVERT) {
                            val sign = if (t.kind == Transaction.Kind.DEPOSIT) "+" else "−"
                            Text(
                                sign + formatYuan(t.amountFen),
                                style = MaterialTheme.typography.titleMedium,
                                color = if (t.kind == Transaction.Kind.DEPOSIT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}
