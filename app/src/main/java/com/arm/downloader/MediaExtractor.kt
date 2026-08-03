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
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    // ─── Platform Detection ───────────────────────────────────────────────────
    fun detectPlatform(url: String): String = when {
        url.contains("youtube.com") || url.contains("youtu.be") -> "YouTube"
        url.contains("tiktok.com") -> "TikTok"
        url.contains("instagram.com") -> "Instagram"
        url.contains("facebook.com") || url.contains("fb.watch") -> "Facebook"
        else -> "Universal Downloader"
    }

    // ─── Main Extractor ───────────────────────────────────────────────────────
    suspend fun extractInfo(url: String, platform: String): MediaInfo = withContext(Dispatchers.IO) {
        if (platform == "TikTok") {
            try {
                return@withContext extractTikTok(url)
            } catch (e: Exception) {
                android.util.Log.w("ARM", "Tikwm fallback: ${e.message}")
            }
        }
        return@withContext extractFast(url, platform)
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
            formats.add(FormatItem("hd", "HD No-Watermark", "1080p", "Ultra Fast Stream", "1080", false, hdUrl))
        }
        if (sdUrl.isNotEmpty()) {
            formats.add(FormatItem("sd", "SD No-Watermark", "720p", "Fast Stream", "720", false, sdUrl))
        }
        if (musicUrl.isNotEmpty()) {
            formats.add(FormatItem("mp3", "Music / Audio", "MP3", "320kbps MP3 Audio", "mp3", true, musicUrl))
        }

        return MediaInfo(title, cover, uploaderName, avatarUrl, "TikTok", formats, url)
    }

    // ─── High Speed Extraction (YouTube, Instagram, FB, Universal) ─────────────
    private fun extractFast(url: String, platform: String): MediaInfo {
        // Speed boost options for instantaneous info extraction!
        val request = YoutubeDLRequest(url).apply {
            addOption("--no-playlist")
            addOption("--no-check-certificate")
            addOption("--no-warnings")
            addOption("--skip-download")
            addOption("--socket-timeout", "10")
        }
        val info = YoutubeDL.getInstance().getInfo(request)

        val title = info.title ?: "$platform Video"
        val uploader = info.uploader ?: "$platform Creator"
        
        val videoIdRegex = Regex("[?&]v=([a-zA-Z0-9_-]{11})|youtu\\.be/([a-zA-Z0-9_-]{11})")
        val videoId = videoIdRegex.find(url)?.groupValues?.firstOrNull { it.length == 11 } ?: ""
        val hdThumb = if (videoId.isNotEmpty()) {
            "https://img.youtube.com/vi/$videoId/maxresdefault.jpg"
        } else {
            info.thumbnail ?: ""
        }

        val handle = uploader.replace(" ", "").replace(Regex("[^a-zA-Z0-9_.-]"), "")
        val site = when (platform.lowercase()) {
            "youtube" -> "youtube"
            "instagram" -> "instagram"
            "facebook" -> "facebook"
            else -> "github"
        }
        val avatarUrl = if (handle.isNotEmpty() && handle != "YouTubeCreator") "https://unavatar.io/$site/$handle" else ""

        val formats = if (platform == "YouTube" || videoId.isNotEmpty()) {
            listOf(
                FormatItem("2160", "4K Ultra HD Video", "4K", "High Speed Direct Stream", "2160", false),
                FormatItem("1080", "Full HD 1080p Video", "1080p", "High Speed Direct Stream", "1080", false),
                FormatItem("720", "HD Ready 720p Video", "720p", "High Speed Direct Stream", "720", false),
                FormatItem("480", "Standard 480p Video", "480p", "High Speed Direct Stream", "480", false),
                FormatItem("mp3", "High Quality Audio", "MP3", "320kbps MP3 Audio", "mp3", true)
            )
        } else {
            listOf(
                FormatItem("1080", "Best HD Quality", "HD", "High Speed Direct Stream", "1080", false),
                FormatItem("720", "Standard Quality", "SD", "High Speed Direct Stream", "720", false),
                FormatItem("mp3", "Audio Only", "MP3", "320kbps MP3 Audio", "mp3", true)
            )
        }

        return MediaInfo(title, hdThumb, uploader, avatarUrl, platform, formats, url)
    }

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
