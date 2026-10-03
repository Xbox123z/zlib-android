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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.xbox123z.zlib.ZLibApp

@Composable
fun SettingsScreen(app: ZLibApp, onBack: () -> Unit) {
    var domain by remember { mutableStateOf(app.prefs.domain) }
    var proxy by remember { mutableStateOf(app.prefs.proxy) }
    var theme by remember { mutableStateOf(app.prefs.theme) }

    var kindleTo by remember { mutableStateOf(app.prefs.kindleTo) }
    var kindleFrom by remember { mutableStateOf(app.prefs.kindleFrom) }
    var kindleHost by remember { mutableStateOf(app.prefs.kindleHost) }
    var kindlePort by remember { mutableIntStateOf(app.prefs.kindlePort) }
    var kindlePassword by remember { mutableStateOf(app.prefs.kindlePassword) }
    var savePassword by remember { mutableStateOf(app.prefs.saveKindlePassword) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("设置", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = onBack) { Text("返回") }
        }
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = domain, onValueChange = { domain = it },
            label = { Text("镜像域名") }, singleLine = true,
            supportingText = { Text("留空使用默认镜像") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = proxy, onValueChange = { proxy = it },
            label = { Text("代理地址（可选）") }, singleLine = true,
            supportingText = { Text("格式：http://127.0.0.1:10808") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))

        Text("主题", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("auto", "light", "dark").forEach { t ->
                FilterChip(
                    selected = theme == t,
                    onClick = { theme = t },
                    label = { Text(if (t == "auto") "自动" else if (t == "light") "亮色" else "暗色") }
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        Divider()
        Spacer(Modifier.height(16.dp))
        Text("Kindle 推送（SMTP）", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = kindleTo, onValueChange = { kindleTo = it },
            label = { Text("Kindle 邮箱") }, singleLine = true,
            supportingText = { Text("example@kindle.com") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = kindleFrom, onValueChange = { kindleFrom = it },
            label = { Text("发件邮箱") }, singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = kindleHost, onValueChange = { kindleHost = it },
            label = { Text("SMTP 服务器") }, singleLine = true,
            supportingText = { Text("smtp.gmail.com / smtp.qq.com / ...") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = kindlePort.toString(),
                onValueChange = { kindlePort = it.toIntOrNull() ?: kindlePort },
                label = { Text("端口") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = kindlePassword, onValueChange = { kindlePassword = it },
                label = { Text("SMTP 密码") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.weight(1f)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = savePassword, onCheckedChange = { savePassword = it })
            Text("保存 SMTP 密码到本地", style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                app.prefs.domain = domain.trim().ifEmpty { "https://z-lib.gd" }
                app.prefs.proxy = proxy.trim()
                app.prefs.theme = theme
                app.prefs.kindleTo = kindleTo.trim()
                app.prefs.kindleFrom = kindleFrom.trim()
                app.prefs.kindleHost = kindleHost.trim()
                app.prefs.kindlePort = kindlePort
                app.prefs.saveKindlePassword = savePassword
                if (savePassword) app.prefs.kindlePassword = kindlePassword
                app.client.refreshConfig()
                onBack()
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("保存") }
        Spacer(Modifier.height(32.dp))
    }
}
