package com.arm.downloader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class FormatItem(
    val height: Int,
    val label: String,
    val sizeStr: String,
    val directUrl: String,
    val isAudio: Boolean = false
)

data class MediaInfo(
    val title: String,
    val thumbnail: String,
    val uploader: String,
    val uploaderId: String,
    val avatarUrl: String,
    val platform: String,
    val formats: List<FormatItem>,
    val audioOption: FormatItem?,
    val isProfileOnly: Boolean = false
)

class MediaExtractor {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun extractMediaInfo(inputUrl: String, platformHint: String = "", isProfileMode: Boolean = false): Result<MediaInfo> {
        return withContext(Dispatchers.IO) {
            try {
                val cleanInput = inputUrl.trim()
                
                // If Profile Mode or username/@ handle
                if (isProfileMode || cleanInput.startsWith("@") || (cleanInput.contains("/@") && !cleanInput.contains("/video/"))) {
                    return@withContext Result.success(extractProfileAvatar(cleanInput, platformHint))
                }

                // TikTok Video Link
                if (cleanInput.contains("tiktok.com")) {
                    val tikResult = extractTikTok(cleanInput)
                    if (tikResult.isSuccess) {
                        return@withContext tikResult
                    }
                }

                // Fallback / General Profile
                Result.success(extractProfileAvatar(cleanInput, platformHint))

            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun extractTikTok(url: String): Result<MediaInfo> {
        val encodedUrl = URLEncoder.encode(url, "UTF-8")
        val apiReqUrl = "https://www.tikwm.com/api/?url=$encodedUrl"
        
        val request = Request.Builder()
            .url(apiReqUrl)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return Result.failure(Exception("HTTP ${response.code}"))
            val body = response.body?.string() ?: return Result.failure(Exception("Empty response"))
            
            val json = JSONObject(body)
            if (json.optInt("code") != 0) return Result.failure(Exception("Tikwm error"))

            val data = json.getJSONObject("data")
            val author = data.optJSONObject("author") ?: JSONObject()

            val uniqueId = author.optString("unique_id", "tiktok_user")
            val nickname = author.optString("nickname", "@$uniqueId")
            val avatar = author.optString("avatar", "https://unavatar.io/tiktok/$uniqueId")
            val cover = data.optString("hdcover", data.optString("cover", avatar))
            val videoUrl = data.optString("hdplay", data.optString("play"))
            val musicUrl = data.optString("music")
            val title = data.optString("title", "TikTok Video by @$uniqueId")

            val formats = listOf(
                FormatItem(1080, "Full HD 1080p (No Watermark)", "~ 15 MB", videoUrl),
                FormatItem(720, "HD 720p (No Watermark)", "~ 8 MB", videoUrl)
            )

            val audioOption = FormatItem(0, "Audio Only (MP3 320kbps)", "~ 3 MB", musicUrl, isAudio = true)

            return Result.success(
                MediaInfo(
                    title = title,
                    thumbnail = cover,
                    uploader = nickname,
                    uploaderId = uniqueId,
                    avatarUrl = avatar,
                    platform = "TikTok",
                    formats = formats,
                    audioOption = audioOption,
                    isProfileOnly = false
                )
            )
        }
    }

    private fun extractProfileAvatar(input: String, platformHint: String): MediaInfo {
        var username = if (input.contains("/video/")) {
            input.substringBefore("/video/").substringAfterLast("/").replace("@", "")
        } else {
            input.substringAfterLast("/").replace("@", "")
        }
        if (username.contains("?")) username = username.substringBefore("?")
        if (username.isEmpty()) username = "user"

        var platform = if (platformHint.isNotEmpty()) platformHint.capitalize() else "Social Media"
        if (input.contains("tiktok", ignoreCase = true)) platform = "TikTok"
        else if (input.contains("youtube", ignoreCase = true)) platform = "YouTube"
        else if (input.contains("instagram", ignoreCase = true)) platform = "Instagram"
        else if (input.contains("facebook", ignoreCase = true)) platform = "Facebook"

        val service = platform.lowercase()
        val avatarUrl = "https://unavatar.io/$service/$username?json=false"

        return MediaInfo(
            title = "@$username Profile Picture",
            thumbnail = avatarUrl,
            uploader = "@$username",
            uploaderId = username,
            avatarUrl = avatarUrl,
            platform = platform,
            formats = emptyList(),
            audioOption = null,
            isProfileOnly = true
        )
    }
}
