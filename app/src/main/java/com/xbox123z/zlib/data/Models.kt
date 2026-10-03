package com.xbox123z.zlib.data

data class Book(
    val id: String = "",
    val hash: String = "",
    val isbn: String = "",
    val url: String = "",
    val cover: String = "",
    val name: String = "",
    val author: String = "",
    val publisher: String = "",
    val year: String = "",
    val language: String = "",
    val extension: String = "",
    val size: String = "",
    val rating: String = "",
    val quality: String = "",
    val description: String = "",
    val downloadUrl: String = ""
) {
    val ref: String get() = if (id.isNotEmpty() && hash.isNotEmpty()) "$id:$hash" else ""
}

data class SearchResult(
    val books: List<Book> = emptyList(),
    val page: Int = 1,
    val totalPages: Int = 1
)

data class SearchOptions(
    val exact: Boolean = false,
    val fromYear: Int = 0,
    val toYear: Int = 0,
    val languages: List<String> = emptyList(),
    val extensions: List<String> = emptyList()
)

data class DownloadQuota(
    val today: Int = 0,
    val allowed: Int = 0,
    val remaining: Int = 0
)

data class HistoryItem(
    val id: String = "",
    val hash: String = "",
    val name: String = "",
    val url: String = "",
    val downloadUrl: String = "",
    val extension: String = "",
    val size: String = "",
    val date: String = ""
) {
    val ref: String get() = if (id.isNotEmpty() && hash.isNotEmpty()) "$id:$hash" else ""
}

data class HistoryResult(
    val items: List<HistoryItem> = emptyList(),
    val page: Int = 1,
    val totalPages: Int = 1
)

enum class DomainStatus { HEALTHY, CHALLENGED, DIAMWALL_BLOCKED, HTTP_ERROR, NETWORK_ERROR }

data class DomainProbe(
    val domain: String,
    val status: DomainStatus,
    val detail: String = ""
)

val EXTENSIONS = listOf("EPUB", "PDF", "MOBI", "AZW3", "FB2", "TXT", "RTF", "DJVU", "LIT", "AZW")

val LANGUAGES = listOf(
    "chinese", "english", "russian", "french", "german", "spanish",
    "japanese", "korean", "italian", "portuguese", "arabic", "dutch",
    "polish", "turkish", "hindi"
)

val CANDIDATE_DOMAINS = listOf(
    "https://z-lib.gd",
    "https://z-lib.gl",
    "https://z-library.ec",
    "https://zlib.bz",
    "https://article.sk",
    "https://articles.sk",
    "https://1lib.sk",
    "https://z-lib.fm",
    "https://z-lib.sk"
)

const val DEFAULT_DOMAIN = "https://z-lib.gd"
