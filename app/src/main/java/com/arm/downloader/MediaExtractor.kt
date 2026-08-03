package com.arm.downloader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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
        else -> "Unknown"
    }

    // ─── Main Extractor ───────────────────────────────────────────────────────
    suspend fun extractInfo(url: String, platform: String): MediaInfo = withContext(Dispatchers.IO) {
        return@withContext when (platform) {
            "TikTok" -> extractTikTok(url)
            "YouTube" -> extractYouTube(url)
            "Instagram" -> extractCobalt(url, "Instagram")
            "Facebook" -> extractCobalt(url, "Facebook")
            else -> extractCobalt(url, "Unknown")
        }
    }

    // ─── TikTok via Tikwm ─────────────────────────────────────────────────────
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

    // ─── YouTube via noembed + Cobalt ─────────────────────────────────────────
    private fun extractYouTube(url: String): MediaInfo {
        // Get metadata via noembed
        val noembedUrl = "https://noembed.com/embed?url=${url.encodeUrl()}"
        val metaReq = Request.Builder().url(noembedUrl)
            .addHeader("User-Agent", "ARM-Downloader/2.0")
            .build()
        val metaBody = try {
            val resp = client.newCall(metaReq).execute()
            resp.body?.string() ?: "{}"
        } catch (e: Exception) { "{}" }

        val meta = JSONObject(metaBody)
        val title = meta.optString("title", "YouTube Video")
        val author = meta.optString("author_name", "YouTube Creator")
        val thumbnail = meta.optString("thumbnail_url", "")

        // Extract video ID for avatar
        val videoIdRegex = Regex("[?&]v=([a-zA-Z0-9_-]{11})|youtu\\.be/([a-zA-Z0-9_-]{11})")
        val videoId = videoIdRegex.find(url)?.groupValues?.firstOrNull { it.length == 11 } ?: ""
        val channelUrl = meta.optString("author_url", "")
        val channelId = channelUrl.substringAfterLast("/")

        val formats = listOf(
            FormatItem("2160", "4K Ultra HD", "4K", "~ 500 MB", "2160", false),
            FormatItem("1080", "Full HD", "1080p", "~ 100 MB", "1080", false),
            FormatItem("720", "HD Ready", "720p", "~ 50 MB", "720", false),
            FormatItem("480", "Standard", "480p", "~ 25 MB", "480", false),
            FormatItem("360", "Low Quality", "360p", "~ 12 MB", "360", false),
            FormatItem("mp3", "Audio Only", "MP3", "~ 5 MB", "mp3", true)
        )

        val hdThumb = if (videoId.isNotEmpty())
            "https://img.youtube.com/vi/$videoId/maxresdefault.jpg"
        else thumbnail

        return MediaInfo(title, hdThumb, author, "", "YouTube", formats, url)
    }

    // ─── Instagram / Facebook via Cobalt ─────────────────────────────────────
    private fun extractCobalt(url: String, platform: String): MediaInfo {
        val formats = listOf(
            FormatItem("1080", "Full HD", "1080p", "~ 80 MB", "1080", false),
            FormatItem("720", "HD Ready", "720p", "~ 40 MB", "720", false),
            FormatItem("480", "Standard", "480p", "~ 20 MB", "480", false),
            FormatItem("mp3", "Audio Only", "MP3", "~ 4 MB", "mp3", true)
        )
        return MediaInfo("$platform Video", "", platform, "", platform, formats, url)
    }

    // ─── Cobalt Download URL Resolver ─────────────────────────────────────────
    suspend fun resolveDownloadUrl(mediaUrl: String, quality: String, isAudio: Boolean, directUrl: String?): String =
        withContext(Dispatchers.IO) {
            // If TikTok direct URL — return as-is
            if (!directUrl.isNullOrEmpty()) return@withContext directUrl

            // Call Cobalt API
            val jsonBody = JSONObject().apply {
                put("url", mediaUrl)
                put("videoQuality", if (isAudio) "max" else quality)
                put("audioFormat", "mp3")
                put("downloadMode", if (isAudio) "audio" else "auto")
                put("filenameStyle", "pretty")
            }.toString()

            val body = jsonBody.toRequestBody("application/json".toMediaType())
            val req = Request.Builder()
                .url("https://api.cobalt.tools/")
                .post(body)
                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", "application/json")
                .addHeader("User-Agent", "ARM-Downloader/2.0")
                .build()

            val resp = client.newCall(req).execute()
            val respBody = resp.body?.string() ?: throw Exception("Cobalt returned empty response")
            val json = JSONObject(respBody)
            val status = json.optString("status")

            return@withContext when (status) {
                "stream", "redirect", "tunnel" -> json.optString("url", "")
                "picker" -> {
                    val picker = json.optJSONArray("picker")
                    picker?.optJSONObject(0)?.optString("url") ?: ""
                }
                else -> {
                    val errMsg = json.optJSONObject("error")?.optString("code") ?: "Unknown Cobalt error"
                    throw Exception("Cobalt error: $errMsg")
                }
            }
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
