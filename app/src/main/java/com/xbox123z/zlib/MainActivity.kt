package com.xbox123z.zlib

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.xbox123z.zlib.data.Book
import com.xbox123z.zlib.kindle.KindleConfig
import com.xbox123z.zlib.kindle.KindleSender
import com.xbox123z.zlib.ui.DetailScreen
import com.xbox123z.zlib.ui.DoctorScreen
import com.xbox123z.zlib.ui.DownloadsScreen
import com.xbox123z.zlib.ui.HistoryScreen
import com.xbox123z.zlib.ui.LoginScreen
import com.xbox123z.zlib.ui.ProfileScreen
import com.xbox123z.zlib.ui.SearchScreen
import com.xbox123z.zlib.ui.SettingsScreen
import com.xbox123z.zlib.ui.ZLibTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

private enum class Tab { SEARCH, DOWNLOADS, HISTORY, PROFILE }

private sealed interface Screen {
    data object Main : Screen
    data class Detail(val book: Book) : Screen
    data object Doctor : Screen
    data object Settings : Screen
}

class MainActivity : ComponentActivity() {

    private val kindleHttp by lazy {
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as ZLibApp
        setContent {
            var theme by remember { mutableStateOf(app.prefs.theme) }
            ZLibTheme(theme = theme) {
                Root(
                    app = app,
                    onSendToKindle = { book -> sendToKindle(app, book) },
                    onThemeChanged = { theme = app.prefs.theme }
                )
            }
        }
    }

    private fun sendToKindle(app: ZLibApp, book: Book) {
        val cfg = KindleConfig(
            to = app.prefs.kindleTo,
            from = app.prefs.kindleFrom,
            host = app.prefs.kindleHost,
            port = app.prefs.kindlePort
        )
        val err = cfg.validate()
        if (err != null) {
            Toast.makeText(this, "$err，请先在「我的 → 设置」里补全 Kindle 配置", Toast.LENGTH_LONG).show()
            return
        }
        val password = app.prefs.kindlePassword
        if (password.isBlank()) {
            Toast.makeText(this, "请先在设置里填写 SMTP 密码", Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(this, "正在准备 Kindle 投递…", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            runCatching {
                val link = app.client.resolveFileUrl(book.ref)
                val file = downloadToCache(app, link, book)
                KindleSender.send(file, cfg, password)
                file.delete()
            }.onSuccess {
                Toast.makeText(this@MainActivity, "已发送到 Kindle，请稍后查收", Toast.LENGTH_LONG).show()
            }.onFailure { e ->
                Toast.makeText(this@MainActivity, "Kindle 投递失败：${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun downloadToCache(app: ZLibApp, url: String, book: Book): File =
        withContext(Dispatchers.IO) {
            val b = Request.Builder().url(url)
            if (app.prefs.userId.isNotEmpty()) b.header("remix-userid", app.prefs.userId)
            if (app.prefs.userKey.isNotEmpty()) b.header("remix-userkey", app.prefs.userKey)
            kindleHttp.newCall(b.build()).execute().use { resp ->
                if (!resp.isSuccessful) throw Exception("下载失败：HTTP ${resp.code}")
                val ext = book.extension.ifBlank { "epub" }.lowercase()
                val out = File(cacheDir, "kindle_${book.id}_${System.currentTimeMillis()}.$ext")
                resp.body?.byteStream()?.use { input ->
                    out.outputStream().use { input.copyTo(it) }
                } ?: throw Exception("下载失败：响应为空")
                out
            }
        }
}

@Composable
private fun Root(
    app: ZLibApp,
    onSendToKindle: (Book) -> Unit,
    onThemeChanged: () -> Unit
) {
    var loggedIn by remember { mutableStateOf(app.prefs.isLoggedIn) }
    var screen by remember { mutableStateOf<Screen>(Screen.Main) }
    var tab by remember { mutableStateOf(Tab.SEARCH) }

    if (!loggedIn) {
        when (screen) {
            is Screen.Doctor -> DoctorScreen(app, onBack = { screen = Screen.Main })
            else -> LoginScreen(
                app,
                onLoggedIn = { loggedIn = true; screen = Screen.Main },
                onDoctor = { screen = Screen.Doctor }
            )
        }
        return
    }

    when (val s = screen) {
        is Screen.Detail -> DetailScreen(app, s.book, onSendToKindle = onSendToKindle, onBack = { screen = Screen.Main })
        Screen.Doctor -> DoctorScreen(app, onBack = { screen = Screen.Main })
        Screen.Settings -> SettingsScreen(app, onBack = {
            onThemeChanged()
            screen = Screen.Main
        })
        Screen.Main -> Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == Tab.SEARCH,
                        onClick = { tab = Tab.SEARCH },
                        icon = { Icon(Icons.Default.Search, contentDescription = null) },
                        label = { Text("搜索") }
                    )
                    NavigationBarItem(
                        selected = tab == Tab.DOWNLOADS,
                        onClick = { tab = Tab.DOWNLOADS },
                        icon = { Icon(Icons.Default.Download, contentDescription = null) },
                        label = { Text("下载") }
                    )
                    NavigationBarItem(
                        selected = tab == Tab.HISTORY,
                        onClick = { tab = Tab.HISTORY },
                        icon = { Icon(Icons.Default.History, contentDescription = null) },
                        label = { Text("历史") }
                    )
                    NavigationBarItem(
                        selected = tab == Tab.PROFILE,
                        onClick = { tab = Tab.PROFILE },
                        icon = { Icon(Icons.Default.Person, contentDescription = null) },
                        label = { Text("我的") }
                    )
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (tab) {
                    Tab.SEARCH -> SearchScreen(app, onOpenBook = { screen = Screen.Detail(it) })
                    Tab.DOWNLOADS -> DownloadsScreen(app)
                    Tab.HISTORY -> HistoryScreen(app)
                    Tab.PROFILE -> ProfileScreen(
                        app,
                        onSettings = { screen = Screen.Settings },
                        onLogout = {
                            app.client.logout()
                            loggedIn = false
                            screen = Screen.Main
                        }
                    )
                }
            }
        }
    }
}
