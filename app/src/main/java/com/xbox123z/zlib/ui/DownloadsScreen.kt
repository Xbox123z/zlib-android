package com.xbox123z.zlib.ui

import android.app.DownloadManager
import android.content.Intent
import android.widget.Toast
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xbox123z.zlib.ZLibApp
import com.xbox123z.zlib.dl.DownloadEntry

@Composable
fun DownloadsScreen(app: ZLibApp) {
    val context = LocalContext.current
    val entries by produceState(initialValue = emptyList<DownloadEntry>()) {
        app.downloads.watch().collect { value = it }
    }

    if (entries.isEmpty()) {
        EmptyBox("暂无下载任务\n在书籍详情页点击「下载」开始")
        return
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(entries, key = { it.id }) { e ->
            Card(onClick = {
                if (e.isDone) {
                    val uri = app.downloads.openUri(e)
                    if (uri != null) {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, null)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        runCatching { context.startActivity(intent) }
                            .onFailure {
                                Toast.makeText(context, "没有可打开该文件的应用", Toast.LENGTH_SHORT).show()
                            }
                    }
                }
            }) {
                Column(Modifier.padding(12.dp)) {
                    Text(e.title, style = MaterialTheme.typography.titleSmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(6.dp))
                    when {
                        e.isRunning -> {
                            LinearProgressIndicator(
                                progress = { e.progress },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${formatBytes(e.downloadedBytes)} / ${formatBytes(e.totalBytes)}",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        e.isDone -> Text(
                            "已完成 · ${formatBytes(e.totalBytes)} · 点击打开",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        e.isFailed -> Text(
                            "下载失败", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        else -> Text("状态 ${e.status}", style = MaterialTheme.typography.labelSmall)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { app.downloads.remove(e.id) }) { Text("移除记录") }
                    }
                }
            }
        }
    }
}
