package com.arm.downloader

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import java.io.File

object DownloadHelper {

    fun download(context: Context, url: String, filename: String, mimeType: String = "video/mp4") {
        try {
            val isAudio = mimeType.contains("audio") || filename.endsWith(".mp3")
            val isImage = mimeType.contains("image") || filename.endsWith(".jpg") || filename.endsWith(".png")

            // Save to dedicated Gallery-visible Media directories instead of plain Downloads
            val targetDir = when {
                isAudio -> Environment.DIRECTORY_MUSIC
                isImage -> Environment.DIRECTORY_PICTURES
                else    -> Environment.DIRECTORY_MOVIES
            }
            val subFolder = "ARM Downloader/$filename"

            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle(filename)
                setDescription("ARM DOWNLOADER — Saving directly to Gallery...")
                setMimeType(mimeType)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(targetDir, subFolder)
                addRequestHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            }
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
            Toast.makeText(context, "Download started! Saving directly to Gallery 🎬", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // ─── Instant MediaStore Gallery Indexing for Native yt-dlp Output ──────────
    fun registerInGallery(context: Context, file: File, mimeType: String) {
        if (!file.exists()) return
        MediaScannerConnection.scanFile(
            context,
            arrayOf(file.absolutePath),
            arrayOf(mimeType)
        ) { path, uri ->
            android.util.Log.i("ARM-Gallery", "File scanned directly into phone gallery: $path -> $uri")
        }
        Toast.makeText(context, "✔ Successfully Saved & Ready in Phone Gallery!", Toast.LENGTH_SHORT).show()
    }

    // ─── Determine Target Directory for yt-dlp Native Output ───────────────────
    fun getGalleryDirectory(isAudio: Boolean, isImage: Boolean = false): File {
        val type = when {
            isAudio -> Environment.DIRECTORY_MUSIC
            isImage -> Environment.DIRECTORY_PICTURES
            else    -> Environment.DIRECTORY_MOVIES
        }
        val dir = File(Environment.getExternalStoragePublicDirectory(type), "ARM Downloader")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    // ─── Quick Open Phone Gallery ──────────────────────────────────────────────
    fun openGallery(context: Context) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, MediaStore.Video.Media.EXTERNAL_CONTENT_URI).apply {
                type = "video/*"
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(Intent.createChooser(intent, "Open ARM Downloader Videos in Gallery"))
        } catch (e: Exception) {
            try {
                val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                    type = "image/*"
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(Intent.createChooser(fallbackIntent, "Open Gallery"))
            } catch (ex: Exception) {
                Toast.makeText(context, "No gallery viewer found on this device", Toast.LENGTH_SHORT).show()
            }
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
