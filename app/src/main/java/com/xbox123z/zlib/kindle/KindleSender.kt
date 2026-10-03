package com.xbox123z.zlib.kindle

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Socket
import javax.net.ssl.SSLSocketFactory

data class KindleConfig(
    val to: String,
    val from: String,
    val host: String,
    val port: Int
) {
    fun validate(): String? = when {
        to.isBlank() -> "Kindle 收件邮箱不能为空"
        from.isBlank() -> "发件邮箱不能为空"
        host.isBlank() -> "SMTP 服务器不能为空"
        port <= 0 -> "SMTP 端口无效"
        else -> null
    }
}

private val KINDLE_EXTENSIONS = setOf(
    "epub", "pdf", "mobi", "txt", "doc", "docx", "rtf", "html", "htm",
    "jpg", "jpeg", "png", "bmp", "gif"
)

private const val KINDLE_MAX_BYTES = 200L * 1024 * 1024
private const val GMAIL_MAX_MESSAGE_BYTES = 25L * 1024 * 1024

/** Minimal SMTP sender (SSL on 465, STARTTLS elsewhere, AUTH LOGIN). */
object KindleSender {

    suspend fun send(file: File, cfg: KindleConfig, password: String): Unit =
        withContext(Dispatchers.IO) {
            cfg.validate()?.let { throw IllegalArgumentException(it) }
            if (password.isBlank()) throw IllegalArgumentException("SMTP 密码不能为空")

            val ext = file.extension.lowercase()
            if (ext !in KINDLE_EXTENSIONS) {
                throw IllegalArgumentException("不支持的 Send-to-Kindle 格式: .$ext")
            }
            if (file.length() > KINDLE_MAX_BYTES) {
                throw IllegalArgumentException("文件超过 Amazon 200MB 上限")
            }
            val gmail = cfg.host.lowercase() in setOf("smtp.gmail.com", "smtp.googlemail.com")
            if (gmail && file.length() * 4 / 3 > GMAIL_MAX_MESSAGE_BYTES) {
                throw IllegalArgumentException("Gmail 附件实际限制约 25MB，该文件过大")
            }

            val message = buildMessage(file, cfg)
            deliver(cfg, password, message)
        }

    private fun buildMessage(file: File, cfg: KindleConfig): ByteArray {
        val boundary = "zlib-kindle-boundary"
        val head = buildString {
            append("From: ${cfg.from}\r\n")
            append("To: ${cfg.to}\r\n")
            append("Subject: ${file.name}\r\n")
            append("MIME-Version: 1.0\r\n")
            append("Content-Type: multipart/mixed; boundary=\"$boundary\"\r\n\r\n")
            append("--$boundary\r\n")
            append("Content-Type: text/plain; charset=utf-8\r\n\r\n")
            append("Sent from Z-Library for Android\r\n\r\n")
            append("--$boundary\r\n")
            append("Content-Type: application/octet-stream; name=\"${file.name}\"\r\n")
            append("Content-Transfer-Encoding: base64\r\n")
            append("Content-Disposition: attachment; filename=\"${file.name}\"\r\n\r\n")
        }
        val tail = "\r\n--$boundary--\r\n"
        val b64 = Base64.encode(file.readBytes(), Base64.DEFAULT)
        return head.toByteArray() + b64 + tail.toByteArray()
    }

    private class SmtpSession(val cfg: KindleConfig) {
        private lateinit var socket: Socket
        private lateinit var reader: BufferedReader
        private lateinit var out: OutputStream

        fun connect() {
            socket = if (cfg.port == 465) {
                SSLSocketFactory.getDefault().createSocket(cfg.host, cfg.port)
            } else {
                Socket(cfg.host, cfg.port)
            }
            socket.soTimeout = 60_000
            reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            out = socket.getOutputStream()
            expect(220)
            cmd("EHLO zlib-android")
            if (cfg.port != 465) {
                cmd("STARTTLS", 220)
                val sslFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
                val ssl = sslFactory.createSocket(socket, cfg.host, cfg.port, true)
                socket = ssl
                reader = BufferedReader(InputStreamReader(ssl.getInputStream()))
                out = ssl.getOutputStream()
                cmd("EHLO zlib-android")
            }
        }

        fun cmd(line: String, expectCode: Int = 250): String {
            out.write((line + "\r\n").toByteArray())
            out.flush()
            return expect(expectCode)
        }

        fun raw(bytes: ByteArray, expectCode: Int): String {
            out.write(bytes)
            out.write("\r\n.\r\n".toByteArray())
            out.flush()
            return expect(expectCode)
        }

        fun expect(code: Int): String {
            // SMTP replies may span multiple lines: "250-..." then "250 ..."
            var last: String
            while (true) {
                last = reader.readLine() ?: throw Exception("SMTP connection closed")
                if (last.length < 4 || last[3] != '-') break
            }
            val got = last.take(3).toIntOrNull() ?: 0
            if (got != code) throw Exception("SMTP error: expected $code, got: $last")
            return last
        }

        fun close() = runCatching { socket.close() }
    }

    private fun deliver(cfg: KindleConfig, password: String, message: ByteArray) {
        val s = SmtpSession(cfg)
        try {
            s.connect()
            val b64 = { v: String -> Base64.encodeToString(v.toByteArray(), Base64.NO_WRAP) }
            s.cmd("AUTH LOGIN", 334)
            s.cmd(b64(cfg.from), 334)
            s.cmd(b64(password), 235)
            s.cmd("MAIL FROM:<${cfg.from}>")
            s.cmd("RCPT TO:<${cfg.to}>")
            s.cmd("DATA", 354)
            s.raw(message, 250)
            s.cmd("QUIT", 221)
        } finally {
            s.close()
        }
    }
}
