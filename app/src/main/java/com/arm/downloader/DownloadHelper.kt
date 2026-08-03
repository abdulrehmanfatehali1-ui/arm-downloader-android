package com.arm.downloader

import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DownloadHelper {

    private const val PREFS_NAME = "arm_download_history"
    private const val KEY_HISTORY = "history_list"

    fun download(context: Context, url: String, filename: String, mimeType: String = "video/mp4") {
        try {
            val isAudio = mimeType.contains("audio") || filename.endsWith(".mp3")
            val isImage = mimeType.contains("image") || filename.endsWith(".jpg") || filename.endsWith(".png")

            val targetDir = when {
                isAudio -> Environment.DIRECTORY_MUSIC
                isImage -> Environment.DIRECTORY_PICTURES
                else    -> Environment.DIRECTORY_MOVIES
            }
            val subFolder = "ARM Downloader/$filename"

            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle(filename)
                setDescription("Saving high speed media directly to Gallery...")
                setMimeType(mimeType)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(targetDir, subFolder)
                addRequestHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            }
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
            addToHistory(context, filename, if (isAudio) "MP3 Audio" else "HD Video")
            Toast.makeText(context, "High speed download started! Saving directly to Gallery 🎬", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // ─── Instant MediaStore Gallery Indexing ────────────────────────────────────
    fun registerInGallery(context: Context, file: File, mimeType: String) {
        if (!file.exists()) return
        MediaScannerConnection.scanFile(
            context,
            arrayOf(file.absolutePath),
            arrayOf(mimeType)
        ) { path, uri ->
            android.util.Log.i("ARM-Gallery", "File scanned directly into phone gallery: $path -> $uri")
        }
        addToHistory(context, file.name, if (mimeType.contains("audio")) "MP3 Audio" else "HD Video")
        Toast.makeText(context, "✔ Successfully Saved & Ready in Phone Gallery!", Toast.LENGTH_SHORT).show()
    }

    // ─── Download History Management ───────────────────────────────────────────
    fun addToHistory(context: Context, title: String, quality: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(KEY_HISTORY, LinkedHashSet()) ?: LinkedHashSet()
        val time = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date())
        val entry = "• $title  [$quality - $time]"
        
        val newList = LinkedHashSet(current)
        newList.add(entry)
        prefs.edit().putStringSet(KEY_HISTORY, newList).apply()
    }

    fun showHistoryDialog(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val history = prefs.getStringSet(KEY_HISTORY, LinkedHashSet())?.toList()?.reversed() ?: emptyList()
        
        val items = if (history.isEmpty()) arrayOf("No downloaded videos in history yet!") else history.toTypedArray()
        val builder = AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("📜 Download History")
            .setItems(items) { _, _ ->
                if (history.isNotEmpty()) openGallery(context)
            }
            .setPositiveButton("Close", null)
        
        if (history.isNotEmpty()) {
            builder.setNeutralButton("Clear History") { _, _ ->
                prefs.edit().remove(KEY_HISTORY).apply()
                Toast.makeText(context, "History cleared!", Toast.LENGTH_SHORT).show()
            }
        }
        builder.show()
    }

    // ─── Target Directory for Output ───────────────────────────────────────────
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
