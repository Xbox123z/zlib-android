package com.xbox123z.zlib.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xbox123z.zlib.ZLibApp
import com.xbox123z.zlib.data.Book
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun DetailScreen(app: ZLibApp, initial: Book, onSendToKindle: (Book) -> Unit, onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var book by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    var downloading by remember { mutableStateOf(false) }

    LaunchedEffect(initial.ref) {
        if (initial.ref.isNotEmpty()) {
            runCatching { app.client.book(initial.ref) }
                .onSuccess { book = it }
                .onFailure { /* list data is enough to render */ }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
        }
        Spacer(Modifier.height(8.dp))
        Row {
            BookCover(book.cover, Modifier.width(120.dp).height(172.dp))
            Spacer(Modifier.width(14.dp))
            Column {
                Text(book.name, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(book.author, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(16.dp))

        MetaLine("出版社", book.publisher)
        MetaLine("年份", book.year)
        MetaLine("语言", book.language)
        MetaLine("格式", book.extension.uppercase())
        MetaLine("大小", book.size)
        MetaLine("ISBN", book.isbn)
        MetaLine("评分", book.rating)
        MetaLine("质量", book.quality)
        MetaLine("引用", book.ref)

        if (book.description.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text("简介", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(book.description, style = MaterialTheme.typography.bodySmall)
        }

        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    scope.launch {
                        downloading = true; error = null
                        try {
                            val link = app.client.resolveFileUrl(book.ref)
                            val name = suggestFileName(book)
                            app.downloads.enqueue(link, name, app.prefs)
                            Toast.makeText(context, "已开始下载：$name", Toast.LENGTH_LONG).show()
                        } catch (e: Exception) {
                            error = e.message ?: "下载失败"
                        } finally {
                            downloading = false
                        }
                    }
                },
                enabled = !downloading && book.ref.isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) { Text(if (downloading) "解析中…" else "下载") }
            OutlinedButton(
                onClick = { onSendToKindle(book) },
                enabled = book.ref.isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) { Text("发送到 Kindle") }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "文件将保存到系统「下载」目录，可在「下载」页查看进度并打开。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun suggestFileName(book: Book): String {
    val base = buildString {
        append(book.name.ifBlank { "book" })
        if (book.author.isNotBlank()) append(" - ").append(book.author)
    }.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(120)
    val ext = book.extension.ifBlank { "epub" }.lowercase()
    return "$base.$ext"
}
