package com.arm.downloader

import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class MediaExtractor {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    // ─── Platform Detection ───────────────────────────────────────────────────
    fun detectPlatform(url: String): String = when {
        url.contains("youtube.com") || url.contains("youtu.be") -> "YouTube"
        url.contains("tiktok.com") -> "TikTok"
        url.contains("instagram.com") -> "Instagram"
        url.contains("facebook.com") || url.contains("fb.watch") -> "Facebook"
        else -> "Universal (yt-dlp)"
    }

    // ─── Main Extractor ───────────────────────────────────────────────────────
    suspend fun extractInfo(url: String, platform: String): MediaInfo = withContext(Dispatchers.IO) {
        if (platform == "TikTok") {
            try {
                return@withContext extractTikTok(url)
            } catch (e: Exception) {
                // If Tikwm ever fails, gracefully fall back to native yt-dlp below!
                android.util.Log.w("ARM", "Tikwm fallback to yt-dlp: ${e.message}")
            }
        }
        return@withContext extractWithYtDlp(url, platform)
    }

    // ─── TikTok via Tikwm (Instant & No-Watermark) ─────────────────────────────
    private fun extractTikTok(url: String): MediaInfo {
        val apiUrl = "https://www.tikwm.com/api/?url=${url.encodeUrl()}"
        val req = Request.Builder().url(apiUrl)
            .addHeader("User-Agent", "ARM-Downloader/2.0")
            .build()
        val response = client.newCall(req).execute()
        val body = response.body?.string() ?: throw Exception("Empty response from Tikwm")
        val json = JSONObject(body)
        val data = json.optJSONObject("data") ?: throw Exception("No data in Tikwm response")

        val title = data.optString("title", "TikTok Video")
        val cover = data.optString("cover", "")
        val author = data.optJSONObject("author")
        val uploaderName = author?.optString("nickname") ?: data.optString("author", "TikTok User")
        val uniqueId = author?.optString("unique_id") ?: ""
        val avatarUrl = "https://unavatar.io/tiktok/$uniqueId"

        val hdUrl = data.optString("hdplay", data.optString("play", ""))
        val sdUrl = data.optString("play", "")
        val musicUrl = data.optString("music", "")

        val formats = mutableListOf<FormatItem>()
        if (hdUrl.isNotEmpty()) {
            formats.add(FormatItem("hd", "HD No-Watermark", "1080p", "~ 20 MB", "1080", false, hdUrl))
        }
        if (sdUrl.isNotEmpty()) {
            formats.add(FormatItem("sd", "SD No-Watermark", "720p", "~ 10 MB", "720", false, sdUrl))
        }
        if (musicUrl.isNotEmpty()) {
            formats.add(FormatItem("mp3", "Music / Audio", "MP3", "~ 3 MB", "mp3", true, musicUrl))
        }

        return MediaInfo(title, cover, uploaderName, avatarUrl, "TikTok", formats, url)
    }

    // ─── Native yt-dlp Extraction (YouTube, Instagram, FB, Universal) ──────────
    private fun extractWithYtDlp(url: String, platform: String): MediaInfo {
        val request = YoutubeDLRequest(url)
        val info = YoutubeDL.getInstance().getInfo(request)

        val title = info.title ?: "$platform Video"
        val uploader = info.uploader ?: info.channel ?: "$platform Creator"
        
        // Enhance HD Thumbnail for YouTube if applicable
        val videoIdRegex = Regex("[?&]v=([a-zA-Z0-9_-]{11})|youtu\\.be/([a-zA-Z0-9_-]{11})")
        val videoId = videoIdRegex.find(url)?.groupValues?.firstOrNull { it.length == 11 } ?: ""
        val hdThumb = if (videoId.isNotEmpty()) {
            "https://img.youtube.com/vi/$videoId/maxresdefault.jpg"
        } else {
            info.thumbnail ?: ""
        }

        // Reliably determine channel avatar
        val uploaderUrl = info.uploaderUrl ?: info.channelUrl ?: ""
        val handle = when {
            uploaderUrl.contains("@") -> uploaderUrl.substringAfter("@").substringBefore("/")
            uploaderUrl.contains("/channel/") -> uploaderUrl.substringAfterLast("/")
            else -> uploader.replace(" ", "").replace(Regex("[^a-zA-Z0-9_.-]"), "")
        }
        val site = when (platform.lowercase()) {
            "youtube" -> "youtube"
            "instagram" -> "instagram"
            "facebook" -> "facebook"
            else -> "github"
        }
        val avatarUrl = if (handle.isNotEmpty()) "https://unavatar.io/$site/$handle" else ""

        val formats = if (platform == "YouTube" || videoId.isNotEmpty()) {
            listOf(
                FormatItem("2160", "4K Ultra HD Video", "4K", "Direct yt-dlp stream", "2160", false),
                FormatItem("1080", "Full HD 1080p Video", "1080p", "Direct yt-dlp stream", "1080", false),
                FormatItem("720", "HD Ready 720p Video", "720p", "Direct yt-dlp stream", "720", false),
                FormatItem("480", "Standard 480p Video", "480p", "Direct yt-dlp stream", "480", false),
                FormatItem("mp3", "High Quality Audio", "MP3", "320kbps MP3 Audio", "mp3", true)
            )
        } else {
            listOf(
                FormatItem("1080", "Best HD Quality", "HD", "Direct yt-dlp stream", "1080", false),
                FormatItem("720", "Standard Quality", "SD", "Direct yt-dlp stream", "720", false),
                FormatItem("mp3", "Audio Only", "MP3", "MP3 Audio Extract", "mp3", true)
            )
        }

        return MediaInfo(title, hdThumb, uploader, avatarUrl, platform, formats, url)
    }

    // ─── Profile Picture ─────────────────────────────────────────────────────
    fun getProfileAvatarUrl(username: String, platform: String): String {
        val site = when (platform.lowercase()) {
            "tiktok" -> "tiktok"
            "youtube" -> "youtube"
            "instagram" -> "instagram"
            "facebook" -> "facebook"
            else -> "github"
        }
        return "https://unavatar.io/$site/$username"
    }

    private fun String.encodeUrl() = java.net.URLEncoder.encode(this, "UTF-8")
}
