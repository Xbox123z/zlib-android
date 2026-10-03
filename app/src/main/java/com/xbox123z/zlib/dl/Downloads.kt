package com.xbox123z.zlib.dl

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import com.xbox123z.zlib.data.Prefs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

data class DownloadEntry(
    val id: Long,
    val title: String,
    val status: Int,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val localUri: String?
) {
    val progress: Float
        get() = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f
    val isRunning: Boolean
        get() = status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PENDING
                || status == DownloadManager.STATUS_PAUSED
    val isDone: Boolean get() = status == DownloadManager.STATUS_SUCCESSFUL
    val isFailed: Boolean get() = status == DownloadManager.STATUS_FAILED
}

class Downloads(private val context: Context) {

    private val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    /** Enqueue the resolved CDN link. Saved into the public Downloads folder. */
    fun enqueue(url: String, displayName: String, prefs: Prefs): Long {
        val req = DownloadManager.Request(Uri.parse(url))
            .setTitle(displayName)
            .setDescription(url)
            .setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, displayName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
        if (prefs.userId.isNotEmpty()) req.addRequestHeader("remix-userid", prefs.userId)
        if (prefs.userKey.isNotEmpty()) req.addRequestHeader("remix-userkey", prefs.userKey)
        req.addRequestHeader("Cookie", "remix_userid=${prefs.userId}; remix_userkey=${prefs.userKey}")
        return dm.enqueue(req)
    }

    fun list(): List<DownloadEntry> {
        val out = mutableListOf<DownloadEntry>()
        val c: Cursor? = dm.query(DownloadManager.Query())
        c?.use {
            val idCol = it.getColumnIndex(DownloadManager.COLUMN_ID)
            val titleCol = it.getColumnIndex(DownloadManager.COLUMN_TITLE)
            val statusCol = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val soFarCol = it.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val totalCol = it.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            val uriCol = it.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
            while (it.moveToNext()) {
                out.add(
                    DownloadEntry(
                        id = it.getLong(idCol),
                        title = it.getString(titleCol) ?: "download",
                        status = it.getInt(statusCol),
                        downloadedBytes = it.getLong(soFarCol),
                        totalBytes = it.getLong(totalCol),
                        localUri = it.getString(uriCol)
                    )
                )
            }
        }
        return out.sortedByDescending { it.id }
    }

    /** Ticks the download list while any entry is still active. */
    fun watch(activeOnly: Boolean = false): Flow<List<DownloadEntry>> = flow {
        while (true) {
            val l = list()
            emit(if (activeOnly) l.filter { it.isRunning } else l)
            if (activeOnly && l.none { it.isRunning }) break
            delay(1000)
        }
    }

    fun openUri(entry: DownloadEntry): Uri? {
        val id = entry.id
        return dm.getUriForDownloadedFile(id)
    }

    fun remove(id: Long) = dm.remove(id)
}
