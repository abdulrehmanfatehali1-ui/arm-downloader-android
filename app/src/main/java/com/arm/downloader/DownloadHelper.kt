package com.arm.downloader

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast

class DownloadHelper(private val context: Context) {

    fun downloadFile(url: String, fileName: String, isAudio: Boolean = false, isImage: Boolean = false) {
        try {
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle(fileName)
                .setDescription("Downloading via ARM DOWNLOADER...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            val directory = when {
                isAudio -> Environment.DIRECTORY_MUSIC
                isImage -> Environment.DIRECTORY_PICTURES
                else -> Environment.DIRECTORY_MOVIES
            }

            request.setDestinationInExternalPublicDir(directory, "ARM_DOWNLOADER/$fileName")

            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.enqueue(request)

            Toast.makeText(context, "Download started: $fileName", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Download failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}
