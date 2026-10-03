package com.xbox123z.zlib.ui

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xbox123z.zlib.ZLibApp
import com.xbox123z.zlib.data.HistoryItem
import kotlinx.coroutines.launch

@Composable
fun HistoryScreen(app: ZLibApp) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<HistoryItem>>(emptyList()) }
    var page by remember { mutableIntStateOf(1) }
    var totalPages by remember { mutableIntStateOf(1) }
    var busy by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    fun load(p: Int) {
        scope.launch {
            busy = true; error = null
            try {
                val r = app.client.history(p)
                items = r.items; page = r.page; totalPages = r.totalPages.coerceAtLeast(1)
            } catch (e: Exception) {
                error = e.message ?: "加载失败"
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(Unit) { load(1) }

    when {
        busy && items.isEmpty() -> LoadingBox()
        error != null && items.isEmpty() -> ErrorBox(error ?: "加载失败", onRetry = { load(page) })
        items.isEmpty() -> EmptyBox("还没有下载记录")
        else -> LazyColumn(
            Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items, key = { it.ref + it.date }) { item ->
                Card {
                    Column(Modifier.padding(12.dp)) {
                        Text(item.name, style = MaterialTheme.typography.titleSmall,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            listOf(item.date, item.extension.uppercase(), item.size)
                                .filter { it.isNotBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        try {
                                            val link = app.client.resolveFileUrl(item.ref)
                                            val name = (item.name.ifBlank { "book" })
                                                .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                                                .take(120) + "." + item.extension.lowercase().ifBlank { "epub" }
                                            app.downloads.enqueue(link, name, app.prefs)
                                            Toast.makeText(context, "已开始重新下载", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, e.message ?: "下载失败", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                enabled = item.ref.isNotEmpty()
                            ) { Text("重新下载") }
                        }
                    }
                }
            }
            item { PagerBar(page, totalPages, busy) { load(it) } }
        }
    }
}
