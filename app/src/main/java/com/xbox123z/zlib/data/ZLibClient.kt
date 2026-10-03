package com.xbox123z.zlib.data

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

private const val USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

class ApiException(message: String) : Exception(message)
class BotProtectionException(val statusCode: Int, val url: String) :
    Exception("blocked by anti-bot protection (HTTP $statusCode) at $url")
class LoginException(message: String) : Exception(message)
class SessionExpiredException : Exception("session expired, please log in again")

class ZLibClient(private val prefs: Prefs) {

    private val gson = Gson()

    // In-memory cookie store; remix credentials are mirrored into Prefs so a
    // restarted app resumes the session without a fresh login.
    private val cookieStore = mutableMapOf<String, MutableList<Cookie>>()

    @Volatile
    var domain: String = prefs.domain
        private set

    private val cookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            synchronized(cookieStore) {
                val list = cookieStore.getOrPut(url.host) { mutableListOf() }
                cookies.forEach { c -> list.removeAll { it.name == c.name }; list.add(c) }
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val fromStore = synchronized(cookieStore) {
                cookieStore[url.host]?.filter { !it.expiresAt.let { t -> t < System.currentTimeMillis() } }
                    ?.toMutableList() ?: mutableListOf()
            }
            // EAPI authenticates on remix_userid/remix_userkey; inject them
            // even when the jar has no copy (e.g. after process restart).
            if (prefs.isLoggedIn) {
                if (fromStore.none { it.name == "remix_userid" }) {
                    fromStore.add(Cookie.Builder().name("remix_userid").value(prefs.userId)
                        .domain(url.host).path("/").build())
                }
                if (fromStore.none { it.name == "remix_userkey" }) {
                    fromStore.add(Cookie.Builder().name("remix_userkey").value(prefs.userKey)
                        .domain(url.host).path("/").build())
                }
            }
            return fromStore
        }
    }

    @Volatile
    private var httpClient: OkHttpClient = buildClient()

    private fun buildClient(): OkHttpClient {
        val b = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
        val proxyConf = prefs.proxy.trim()
        if (proxyConf.isNotEmpty()) {
            val hp = proxyConf.removePrefix("http://").removePrefix("https://").split(":")
            if (hp.size == 2) {
                hp[1].toIntOrNull()?.let { port ->
                    b.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(hp[0], port)))
                }
            }
        }
        return b.build()
    }

    /** Apply current Prefs (domain / proxy); rebuilds the transport. */
    fun refreshConfig() {
        domain = prefs.domain
        httpClient = buildClient()
    }

    fun logout() {
        prefs.logout()
        synchronized(cookieStore) { cookieStore.clear() }
    }

    // ---------------------------------------------------------------- login

    /** EAPI login; falls back to the HTML form when /eapi/user/login refuses. */
    suspend fun login(email: String, password: String): Unit = withContext(Dispatchers.IO) {
        try {
            val body = FormBody.Builder()
                .add("email", email)
                .add("password", password)
                .build()
            val json = eapiRequest("POST", "/eapi/user/login", body)
            val success = json.int("success")
            if (success == 1) {
                val user = json.obj("user")
                val userId = user?.anyAsString("id") ?: ""
                val key = user?.str("remix_userkey")?.ifEmpty { user.str("remix_user_key") } ?: ""
                if (userId.isEmpty() || key.isEmpty()) {
                    throw LoginException("EAPI response did not contain remix credentials")
                }
                prefs.saveSession(userId, key, domain)
                prefs.email = email
                return@withContext
            }
            val eapiError = json.str("error") ?: ""
            loginViaHtml(email, password, eapiError)
        } catch (e: LoginException) {
            throw e
        }
    }

    private fun loginViaHtml(email: String, password: String, eapiError: String) {
        val body = FormBody.Builder()
            .add("isModal", "true")
            .add("email", email)
            .add("password", password)
            .add("site_mode", "books")
            .add("action", "login")
            .add("isSingleLogin", "1")
            .add("redirectUrl", "")
            .add("gg_json_mode", "1")
            .build()
        val req = baseRequest("$domain/rpc.php").post(body).build()
        httpClient.newCall(req).execute().use { resp ->
            val raw = resp.body?.string() ?: ""
            checkBotProtection(resp.code, resp.header("Content-Type"), raw, "$domain/rpc.php")
            if (resp.code !in 200..299) throw LoginException("unexpected HTTP status ${resp.code}")
            val json = try {
                JsonParser.parseString(raw).asJsonObject
            } catch (e: Exception) {
                throw LoginException("invalid response: ${e.message}")
            }
            val validationError = json.obj("response")?.get("validationError")
            if (validationError != null && !validationError.isJsonNull) {
                throw LoginException(validationError.toString())
            }
        }
        val host = domain.toHttpUrl().host
        val cookies = synchronized(cookieStore) { cookieStore[host]?.toList() ?: emptyList() }
        val userId = cookies.firstOrNull { it.name == "remix_userid" }?.value ?: ""
        val userKey = cookies.firstOrNull { it.name == "remix_userkey" }?.value ?: ""
        if (userId.isEmpty() || userKey.isEmpty()) {
            throw LoginException(
                "EAPI login failed (${eapiError.ifEmpty { "login was rejected" }}) " +
                    "and the HTML login returned no remix credentials"
            )
        }
        prefs.saveSession(userId, userKey, domain)
        prefs.email = email
    }

    // ---------------------------------------------------------------- search

    suspend fun search(
        query: String,
        page: Int = 1,
        limit: Int = 25,
        opts: SearchOptions = SearchOptions()
    ): SearchResult = withContext(Dispatchers.IO) {
        val b = FormBody.Builder()
            .add("message", query)
            .add("page", page.toString())
            .add("limit", limit.toString())
        if (opts.exact) b.add("e", "1")
        if (opts.fromYear > 0) b.add("yearFrom", opts.fromYear.toString())
        if (opts.toYear > 0) b.add("yearTo", opts.toYear.toString())
        opts.languages.forEach { b.add("languages[]", it.lowercase()) }
        opts.extensions.forEach { b.add("extensions[]", it.lowercase()) }

        val json = eapiRequest("POST", "/eapi/book/search", b.build())
        ensureSuccess(json, "EAPI search failed")
        val books = json.arr("books")?.mapNotNull { it.asJsonObjectOrNull() } ?: emptyList()
        val pagination = json.obj("pagination")
        var current = pagination?.int("current") ?: 0
        if (current == 0) current = page
        SearchResult(
            books = books.map { mapBook(it) },
            page = current,
            totalPages = pagination?.int("total_pages") ?: 1
        )
    }

    // ---------------------------------------------------------------- book

    suspend fun book(ref: String): Book = withContext(Dispatchers.IO) {
        val (id, hash) = parseRef(ref)
        val json = eapiRequest("GET", "/eapi/book/${enc(id)}/${enc(hash)}", null)
        ensureSuccess(json, "EAPI book failed")
        val obj = json.obj("book") ?: throw ApiException("EAPI book response missing book")
        mapBook(obj, fallbackId = id, fallbackHash = hash)
    }

    /** Resolve /eapi/book/{id}/{hash}/file into the real CDN download link. */
    suspend fun resolveFileUrl(ref: String): String = withContext(Dispatchers.IO) {
        val (id, hash) = parseRef(ref)
        val json = eapiRequest("GET", "/eapi/book/${enc(id)}/${enc(hash)}/file", null)
        ensureSuccess(json, "EAPI file request failed")
        val link = json.obj("file")?.str("downloadLink")
            ?: json.str("downloadLink")
            ?: json.str("url")
            ?: json.str("link")
        if (link.isNullOrEmpty()) throw ApiException("EAPI returned no file link")
        absolutize(link)
    }

    // ---------------------------------------------------------------- profile

    suspend fun quota(): DownloadQuota = withContext(Dispatchers.IO) {
        val json = eapiRequest("GET", "/eapi/user/profile", null)
        ensureSuccess(json, "EAPI profile failed")
        val user = json.obj("user") ?: JsonObject()
        val today = user.anyAsInt("downloads_today").takeIf { it > 0 }
            ?: user.anyAsInt("dailyDownloadsCount")
        val allowed = user.anyAsInt("downloads_limit").takeIf { it > 0 }
            ?: user.anyAsInt("dailyDownloadLimit")
        DownloadQuota(
            today = today,
            allowed = allowed,
            remaining = (allowed - today).coerceAtLeast(0)
        )
    }

    suspend fun history(page: Int = 1, limit: Int = 50): HistoryResult = withContext(Dispatchers.IO) {
        val url = ("$domain/eapi/user/book/downloaded?page=$page&limit=$limit").toHttpUrl()
        val req = authedRequest(Request.Builder().url(url).get())
        httpClient.newCall(req.build()).execute().use { resp ->
            val raw = resp.body?.string() ?: ""
            val json = decodeEapi(resp, raw, url.toString())
            ensureSuccess(json, "EAPI history failed")
            val books = json.arr("books")?.mapNotNull { it.asJsonObjectOrNull() } ?: emptyList()
            val pagination = json.obj("pagination")
            var current = pagination?.int("current") ?: 0
            if (current == 0) current = page
            HistoryResult(
                items = books.map { o ->
                    val b = mapBook(o)
                    HistoryItem(
                        id = b.id, hash = b.hash, name = b.name, url = b.url,
                        downloadUrl = b.downloadUrl, extension = b.extension, size = b.size,
                        date = o.str("date")?.ifEmpty { o.str("downloaded_at") } ?: ""
                    )
                },
                page = current,
                totalPages = pagination?.int("total_pages") ?: 1
            )
        }
    }

    // ---------------------------------------------------------------- doctor

    suspend fun probe(candidate: String): DomainProbe = withContext(Dispatchers.IO) {
        val base = candidate.trim().trimEnd('/')
        val url = "$base/eapi/user/profile"
        try {
            val req = Request.Builder().url(url).get()
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .build()
            httpClient.newCall(req).execute().use { resp ->
                val raw = resp.body?.string() ?: ""
                val ct = resp.header("Content-Type") ?: ""
                when {
                    resp.code == 513 || resp.code == 517 ->
                        DomainProbe(base, DomainStatus.DIAMWALL_BLOCKED, "HTTP ${resp.code}")
                    isDiamWallTitle(ct, raw) ->
                        DomainProbe(base, DomainStatus.DIAMWALL_BLOCKED, "DiamWall interstitial")
                    resp.code == 503 && raw.length < 20000 && raw.contains("challenge", true) ->
                        DomainProbe(base, DomainStatus.CHALLENGED, "proof-of-work page (usable)")
                    resp.code in 200..299 || isEapiEnvelope(raw) ->
                        DomainProbe(base, DomainStatus.HEALTHY, "EAPI reachable")
                    resp.code in 300..399 ->
                        DomainProbe(base, DomainStatus.HTTP_ERROR, "redirect HTTP ${resp.code}")
                    else -> DomainProbe(base, DomainStatus.HTTP_ERROR, "HTTP ${resp.code}")
                }
            }
        } catch (e: Exception) {
            DomainProbe(base, DomainStatus.NETWORK_ERROR, e.message ?: e.javaClass.simpleName)
        }
    }

    suspend fun probeAll(candidates: List<String> = CANDIDATE_DOMAINS): List<DomainProbe> =
        withContext(Dispatchers.IO) {
            candidates.map { c -> async { probe(c) } }.map { it.await() }
        }

    // ---------------------------------------------------------------- core

    private fun baseRequest(url: String): Request.Builder =
        Request.Builder().url(url)
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)

    private fun authedRequest(b: Request.Builder): Request.Builder {
        if (prefs.userId.isNotEmpty()) b.header("remix-userid", prefs.userId)
        if (prefs.userKey.isNotEmpty()) b.header("remix-userkey", prefs.userKey)
        return b.header("Accept", "application/json").header("User-Agent", USER_AGENT)
    }

    private fun eapiRequest(method: String, path: String, body: FormBody?): JsonObject {
        val url = "$domain$path"
        val b = baseRequest(url)
        authedRequest(b)
        if (method == "POST") {
            requireNotNull(body)
            b.post(body)
        } else {
            b.get()
        }
        httpClient.newCall(b.build()).execute().use { resp ->
            val raw = resp.body?.string() ?: ""
            return decodeEapi(resp, raw, url)
        }
    }

    private fun decodeEapi(resp: okhttp3.Response, raw: String, url: String): JsonObject {
        checkBotProtection(resp.code, resp.header("Content-Type"), raw, url)
        if (resp.code !in 200..299 && !isEapiEnvelope(raw)) {
            throw ApiException("unexpected HTTP status ${resp.code}")
        }
        val json = try {
            JsonParser.parseString(raw).asJsonObject
        } catch (e: Exception) {
            throw ApiException("invalid EAPI response: ${e.message}")
        }
        if (resp.code == 401 || resp.code == 403) {
            val err = json.str("error") ?: ""
            if (err.contains("authorized", true) || err.contains("login", true)) {
                throw SessionExpiredException()
            }
        }
        return json
    }

    private fun ensureSuccess(json: JsonObject, prefix: String) {
        if (json.int("success") != 1) {
            throw ApiException("$prefix: ${json.str("error") ?: "request was rejected"}")
        }
    }

    private fun checkBotProtection(code: Int, contentType: String?, body: String, url: String) {
        if (code == 513 || code == 517 || isDiamWallTitle(contentType ?: "", body)) {
            throw BotProtectionException(code, url)
        }
    }

    private fun isDiamWallTitle(contentType: String, body: String): Boolean {
        if (contentType.isNotEmpty() &&
            !contentType.startsWith("text/html") &&
            !contentType.startsWith("application/xhtml")
        ) return false
        val m = Regex("<title[^>]*>([^<]*)</title>", RegexOption.IGNORE_CASE).find(body)
        return m?.groupValues?.get(1)?.contains("diamwall", true) == true
    }

    private fun isEapiEnvelope(raw: String): Boolean = try {
        JsonParser.parseString(raw).asJsonObject.has("success")
    } catch (e: Exception) {
        false
    }

    private fun mapBook(o: JsonObject, fallbackId: String = "", fallbackHash: String = ""): Book {
        val id = o.anyAsString("id").ifEmpty { fallbackId }
        val hash = o.str("hash")?.ifEmpty { fallbackHash } ?: fallbackHash
        val isbn = o.anyAsString("identifier").ifEmpty { o.anyAsString("isbn") }
        val size = o.str("filesizeString")?.ifEmpty { o.anyAsString("filesize") } ?: ""
        val downloadUrl = if (id.isNotEmpty() && hash.isNotEmpty()) {
            "$domain/eapi/book/${enc(id)}/${enc(hash)}/file"
        } else {
            absolutize(o.str("dl") ?: "")
        }
        return Book(
            id = id,
            hash = hash,
            isbn = isbn,
            url = absolutize(o.str("href") ?: ""),
            cover = absolutize(o.str("cover") ?: ""),
            name = o.str("title") ?: "",
            author = o.str("author") ?: "",
            publisher = o.str("publisher") ?: "",
            year = o.anyAsString("year"),
            language = o.str("language") ?: "",
            extension = o.str("extension") ?: "",
            size = size,
            rating = o.anyAsString("interestScore"),
            quality = o.anyAsString("qualityScore"),
            description = o.str("description") ?: "",
            downloadUrl = downloadUrl
        )
    }

    private fun absolutize(href: String): String {
        if (href.isEmpty()) return ""
        if (href.startsWith("http://") || href.startsWith("https://")) return href
        return domain + if (href.startsWith("/")) href else "/$href"
    }

    private fun parseRef(ref: String): Pair<String, String> {
        val idx = ref.trim().indexOf(':')
        if (idx <= 0 || idx == ref.trim().length - 1) {
            throw ApiException("EAPI book ID must use id:hash format")
        }
        return ref.trim().substring(0, idx) to ref.trim().substring(idx + 1)
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")

    // --------------------------------------------------------- json helpers

    private fun JsonObject.str(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive && !it.isJsonNull }?.asString

    private fun JsonObject.int(key: String): Int? =
        get(key)?.takeIf { it.isJsonPrimitive }?.let {
            try { it.asInt } catch (e: Exception) { null }
        }

    private fun JsonObject.obj(key: String): JsonObject? =
        get(key)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.arr(key: String) =
        get(key)?.takeIf { it.isJsonArray }?.asJsonArray

    private fun JsonElement.asJsonObjectOrNull(): JsonObject? =
        takeIf { isJsonObject }?.asJsonObject

    private fun JsonObject.anyAsString(key: String): String {
        val e = get(key) ?: return ""
        return when {
            e.isJsonNull -> ""
            e.isJsonPrimitive -> e.asString
            else -> e.toString()
        }
    }

    private fun JsonObject.anyAsInt(key: String): Int =
        anyAsString(key).toIntOrNull() ?: 0
}
