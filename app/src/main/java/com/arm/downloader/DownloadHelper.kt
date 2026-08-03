package com.arm.downloader

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast

object DownloadHelper {

    fun download(context: Context, url: String, filename: String, mimeType: String = "video/mp4") {
        try {
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle(filename)
                setDescription("ARM DOWNLOADER - Saving to Downloads")
                setMimeType(mimeType)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "ARM/$filename")
                addRequestHeader("User-Agent", "Mozilla/5.0")
            }
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
            Toast.makeText(context, "Download started! Check notifications.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun mimeType(isAudio: Boolean, url: String = ""): String = when {
        isAudio -> "audio/mpeg"
        url.endsWith(".jpg") || url.endsWith(".jpeg") || url.endsWith(".png") -> "image/jpeg"
        else -> "video/mp4"
    }

    fun filename(title: String, badge: String, isAudio: Boolean): String {
        val clean = title.replace(Regex("[^a-zA-Z0-9 _-]"), "").trim().take(40)
        val ext = if (isAudio) "mp3" else "mp4"
        return "${clean}_${badge}.$ext"
    }
}
