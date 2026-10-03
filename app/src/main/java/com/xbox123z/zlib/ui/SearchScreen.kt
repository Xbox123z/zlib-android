package com.xbox123z.zlib.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.xbox123z.zlib.ZLibApp
import com.xbox123z.zlib.data.Book
import com.xbox123z.zlib.data.EXTENSIONS
import com.xbox123z.zlib.data.LANGUAGES
import com.xbox123z.zlib.data.SearchOptions
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(app: ZLibApp, onOpenBook: (Book) -> Unit) {
    var query by remember { mutableStateOf("") }
    var page by remember { mutableIntStateOf(1) }
    var books by remember { mutableStateOf<List<Book>>(emptyList()) }
    var totalPages by remember { mutableIntStateOf(1) }
    var searched by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    var showFilters by remember { mutableStateOf(false) }
    var exact by remember { mutableStateOf(false) }
    var fromYear by remember { mutableStateOf("") }
    var toYear by remember { mutableStateOf("") }
    var langs by remember { mutableStateOf(setOf<String>()) }
    var exts by remember { mutableStateOf(setOf<String>()) }

    val scope = rememberCoroutineScope()

    fun runSearch(p: Int) {
        scope.launch {
            busy = true; error = null
            try {
                val opts = SearchOptions(
                    exact = exact,
                    fromYear = fromYear.toIntOrNull() ?: 0,
                    toYear = toYear.toIntOrNull() ?: 0,
                    languages = langs.toList(),
                    extensions = exts.toList()
                )
                val r = app.client.search(query.trim(), p, 25, opts)
                books = r.books
                page = r.page
                totalPages = r.totalPages.coerceAtLeast(1)
                searched = true
            } catch (e: Exception) {
                error = e.message ?: "搜索失败"
            } finally {
                busy = false
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("书名 / 作者 / ISBN / 出版社") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { if (query.isNotBlank()) runSearch(1) }),
                trailingIcon = {
                    IconButton(onClick = { if (query.isNotBlank()) runSearch(1) }) {
                        Icon(Icons.Default.Search, contentDescription = "搜索")
                    }
                }
            )
            IconButton(onClick = { showFilters = true }) {
                Icon(Icons.Default.FilterList, contentDescription = "筛选")
            }
        }

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 8.dp))
        }

        when {
            busy -> LoadingBox()
            !searched -> EmptyBox("输入关键词开始搜索\n点右上角漏斗可按格式 / 语言 / 年份筛选")
            books.isEmpty() -> EmptyBox("没有匹配的结果")
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(books, key = { it.ref.ifEmpty { it.id + it.name } }) { book ->
                    BookRow(book) { onOpenBook(book) }
                }
                item { PagerBar(page, totalPages, busy) { runSearch(it) } }
            }
        }
    }

    if (showFilters) {
        ModalBottomSheet(
            onDismissRequest = { showFilters = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
                Text("筛选", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = exact, onCheckedChange = { exact = it })
                    Text("精确匹配标题")
                }
                Spacer(Modifier.height(8.dp))

                Text("年份范围", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = fromYear, onValueChange = { fromYear = it.filter(Char::isDigit) },
                        label = { Text("从") }, singleLine = true, modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = toYear, onValueChange = { toYear = it.filter(Char::isDigit) },
                        label = { Text("到") }, singleLine = true, modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(12.dp))

                Text("格式", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    EXTENSIONS.forEach { e ->
                        FilterChip(
                            selected = e in exts,
                            onClick = { exts = if (e in exts) exts - e else exts + e },
                            label = { Text(e) }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))

                Text("语言", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LANGUAGES.forEach { l ->
                        FilterChip(
                            selected = l in langs,
                            onClick = { langs = if (l in langs) langs - l else langs + l },
                            label = { Text(l) }
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { showFilters = false; if (query.isNotBlank()) runSearch(1) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("应用并搜索") }
            }
        }
    }
}
