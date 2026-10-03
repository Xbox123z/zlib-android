package com.xbox123z.zlib.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xbox123z.zlib.ZLibApp
import com.xbox123z.zlib.data.DownloadQuota
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    app: ZLibApp,
    onSettings: () -> Unit,
    onLogout: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var quota by remember { mutableStateOf<DownloadQuota?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        loading = true; error = null
        runCatching { app.client.quota() }
            .onSuccess { quota = it }
            .onFailure { error = it.message ?: "加载失败" }
        loading = false
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("我的", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("账户信息", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Text("邮箱：${app.prefs.email}", style = MaterialTheme.typography.bodyMedium)
                Text("域名：${app.prefs.domain}", style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(Modifier.height(16.dp))

        when {
            loading -> LoadingBox()
            error != null -> ErrorBox(error!!, onRetry = {
                scope.launch {
                    loading = true; error = null
                    runCatching { app.client.quota() }
                        .onSuccess { quota = it }
                        .onFailure { error = it.message ?: "加载失败" }
                    loading = false
                }
            })
            quota != null -> QuotaCard(quota!!)
        }

        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onSettings, modifier = Modifier.weight(1f)) { Text("设置") }
            Button(
                onClick = onLogout,
                modifier = Modifier.weight(1f)
            ) { Text("退出登录") }
        }
    }
}

@Composable
private fun QuotaCard(q: DownloadQuota) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("今日下载配额", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                QuotaItem("已用", q.today.toString())
                QuotaItem("限额", q.allowed.toString())
                QuotaItem("剩余", q.remaining.toString())
            }
        }
    }
}

@Composable
private fun QuotaItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
