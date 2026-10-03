package com.xbox123z.zlib.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.xbox123z.zlib.ZLibApp
import com.xbox123z.zlib.data.CANDIDATE_DOMAINS
import com.xbox123z.zlib.data.DomainProbe
import com.xbox123z.zlib.data.DomainStatus

private fun statusColor(status: DomainStatus): Color = when (status) {
    DomainStatus.HEALTHY -> Color(0xFF4CAF50)
    DomainStatus.CHALLENGED -> Color(0xFFFF9800)
    DomainStatus.DIAMWALL_BLOCKED -> Color(0xFFF44336)
    DomainStatus.HTTP_ERROR -> Color(0xFFF44336)
    DomainStatus.NETWORK_ERROR -> Color(0xFF9E9E9E)
}

private fun statusLabel(status: DomainStatus): String = when (status) {
    DomainStatus.HEALTHY -> "可用"
    DomainStatus.CHALLENGED -> "JS 计算题（可用）"
    DomainStatus.DIAMWALL_BLOCKED -> "DiamWall 拦截"
    DomainStatus.HTTP_ERROR -> "HTTP 错误"
    DomainStatus.NETWORK_ERROR -> "网络错误"
}

@Composable
fun DoctorScreen(app: ZLibApp, onBack: () -> Unit) {
    val context = LocalContext.current
    var probes by remember { mutableStateOf<List<DomainProbe>>(emptyList()) }
    var busy by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        busy = true; error = null
        runCatching { app.client.probeAll(CANDIDATE_DOMAINS) }
            .onSuccess { probes = it }
            .onFailure { error = it.message ?: "诊断失败" }
        busy = false
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("域名诊断", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = onBack) { Text("返回") }
        }
        Text(
            "点击任一域名将其设为主域名",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        when {
            busy -> LoadingBox()
            error != null -> ErrorBox(error!!)
            probes.isEmpty() -> EmptyBox("没有候选域名")
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(probes, key = { it.domain }) { probe ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                app.prefs.domain = probe.domain
                                app.client.refreshConfig()
                                Toast
                                    .makeText(
                                        context,
                                        "主域名已设为 ${probe.domain}",
                                        Toast.LENGTH_SHORT
                                    )
                                    .show()
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (probe.domain == app.prefs.domain)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    probe.domain,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    statusLabel(probe.status),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = statusColor(probe.status)
                                )
                            }
                            if (probe.detail.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    probe.detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (probe.domain == app.prefs.domain) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "当前主域名",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
