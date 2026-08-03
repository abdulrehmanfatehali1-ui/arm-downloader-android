package com.arm.downloader

import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.google.android.material.tabs.TabLayout
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.*
import java.io.File

class MainActivity : AppCompatActivity() {

    private val extractor = MediaExtractor()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Views
    private lateinit var tabMain: TabLayout
    private lateinit var tabProfile: TabLayout
    private lateinit var etUrl: EditText
    private lateinit var btnPaste: Button
    private lateinit var btnFetch: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvLoadingMsg: TextView
    private lateinit var layoutPreview: LinearLayout
    private lateinit var imgThumb: ImageView
    private lateinit var imgAvatar: ImageView
    private lateinit var tvUploader: TextView
    private lateinit var tvPlatform: TextView
    private lateinit var tvTitle: TextView
    private lateinit var btnSaveThumbnail: Button
    private lateinit var btnSaveAvatar: Button
    private lateinit var layoutFormats: LinearLayout
    private lateinit var tvSelectQuality: TextView
    private lateinit var btnDownload: Button
    private lateinit var layoutProgress: LinearLayout
    private lateinit var downloadProgressBar: ProgressBar
    private lateinit var tvProgress: TextView
    private lateinit var tvDownloadStatus: TextView

    // State
    private var currentMediaInfo: MediaInfo? = null
    private var selectedFormat: FormatItem? = null
    private var currentTab = 0       // Main tab index
    private var profileTab = 0       // Profile sub-tab index

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initNativeEngine()
        bindViews()
        setupTabs()
        setupButtons()
    }

    private fun initNativeEngine() {
        try {
            YoutubeDL.getInstance().init(applicationContext)
            FFmpeg.getInstance().init(applicationContext)
            scope.launch(Dispatchers.IO) {
                try {
                    YoutubeDL.getInstance().updateYoutubeDL(applicationContext, YoutubeDL.UpdateChannel.STABLE)
                } catch (e: Exception) {
                    // ignore network error during silent update check
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ARM", "Failed to init youtubedl-android", e)
        }
    }

    private fun bindViews() {
        tabMain         = findViewById(R.id.tabLayoutMain)
        tabProfile      = findViewById(R.id.tabLayoutProfile)
        etUrl           = findViewById(R.id.etUrl)
        btnPaste        = findViewById(R.id.btnPaste)
        btnFetch        = findViewById(R.id.btnFetch)
        progressBar     = findViewById(R.id.progressBarLoading)
        tvLoadingMsg    = findViewById(R.id.tvLoadingMsg)
        layoutPreview   = findViewById(R.id.layoutPreview)
        imgThumb        = findViewById(R.id.imgThumb)
        imgAvatar       = findViewById(R.id.imgAvatar)
        tvUploader      = findViewById(R.id.tvUploader)
        tvPlatform      = findViewById(R.id.tvPlatform)
        tvTitle         = findViewById(R.id.tvTitle)
        btnSaveThumbnail = findViewById(R.id.btnSaveThumbnail)
        btnSaveAvatar   = findViewById(R.id.btnSaveAvatar)
        layoutFormats   = findViewById(R.id.layoutFormats)
        tvSelectQuality = findViewById(R.id.tvSelectQuality)
        btnDownload     = findViewById(R.id.btnDownload)
        layoutProgress  = findViewById(R.id.layoutProgress)
        downloadProgressBar = findViewById(R.id.downloadProgressBar)
        tvProgress      = findViewById(R.id.tvProgress)
        tvDownloadStatus = findViewById(R.id.tvDownloadStatus)
    }

    private fun setupTabs() {
        tabMain.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentTab = tab?.position ?: 0
                tabProfile.visibility = if (currentTab == 6) View.VISIBLE else View.GONE
                updateHint()
                resetPreview()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        tabProfile.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                profileTab = tab?.position ?: 0
                updateHint()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun updateHint() {
        etUrl.hint = when {
            currentTab == 6 -> {
                val platform = listOf("TikTok", "YouTube", "Instagram", "Facebook")[profileTab]
                "Enter $platform username (e.g. @username)"
            }
            currentTab == 5 -> "Paste YouTube / TikTok / Instagram link..."
            else -> "Paste YouTube, TikTok, Instagram, Facebook link..."
        }
    }

    private fun setupButtons() {
        btnPaste.setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = cm.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
            if (text.isNotEmpty()) {
                etUrl.setText(text)
                etUrl.setSelection(text.length)
            } else {
                toast("Clipboard is empty")
            }
        }

        btnFetch.setOnClickListener {
            val url = etUrl.text.toString().trim()
            if (url.isEmpty()) { toast("Please enter a URL or username"); return@setOnClickListener }
            if (currentTab == 6) fetchProfile(url) else fetchMedia(url)
        }

        btnDownload.setOnClickListener {
            val info = currentMediaInfo ?: return@setOnClickListener
            val fmt  = selectedFormat ?: return@setOnClickListener
            startDownload(info, fmt)
        }

        btnSaveThumbnail.setOnClickListener {
            val info = currentMediaInfo ?: return@setOnClickListener
            if (info.thumbnailUrl.isNotEmpty()) {
                DownloadHelper.download(this, info.thumbnailUrl,
                    "thumb_${System.currentTimeMillis()}.jpg", "image/jpeg")
            }
        }

        btnSaveAvatar.setOnClickListener {
            val info = currentMediaInfo ?: return@setOnClickListener
            if (info.avatarUrl.isNotEmpty()) {
                DownloadHelper.download(this, info.avatarUrl,
                    "pfp_${info.uploader.replace(Regex("[^a-zA-Z0-9_.-]"), "")}_${System.currentTimeMillis()}.jpg", "image/jpeg")
            }
        }
    }

    // ─── Fetch Media ──────────────────────────────────────────────────────────
    private fun fetchMedia(url: String) {
        val platform = when (currentTab) {
            1 -> "YouTube"
            2 -> "TikTok"
            3 -> "Instagram"
            4 -> "Facebook"
            5 -> "YouTube" // Thumbnail mode
            else -> extractor.detectPlatform(url)
        }

        showLoading(true)
        resetPreview()

        scope.launch {
            try {
                val info = withContext(Dispatchers.IO) {
                    extractor.extractInfo(url, platform)
                }
                currentMediaInfo = info
                showPreview(info)
                showLoading(false)
            } catch (e: Exception) {
                showLoading(false)
                toast("Error: ${e.message}")
            }
        }
    }

    // ─── Profile Fetch ────────────────────────────────────────────────────────
    private fun fetchProfile(username: String) {
        val platform = listOf("TikTok", "YouTube", "Instagram", "Facebook")[profileTab]
        val cleanUser = username.trimStart('@')
        val avatarUrl = extractor.getProfileAvatarUrl(cleanUser, platform)

        currentMediaInfo = MediaInfo(
            title = "$platform Profile",
            thumbnailUrl = avatarUrl,
            uploader = "@$cleanUser",
            avatarUrl = avatarUrl,
            platform = platform,
            formats = emptyList(),
            originalUrl = avatarUrl
        )
        showPreview(currentMediaInfo!!, profileOnly = true)
    }

    // ─── Show Preview ─────────────────────────────────────────────────────────
    private fun showPreview(info: MediaInfo, profileOnly: Boolean = false) {
        layoutPreview.visibility = View.VISIBLE

        // Thumbnail
        if (info.thumbnailUrl.isNotEmpty()) {
            Glide.with(this).load(info.thumbnailUrl).into(imgThumb)
        }

        // Avatar with fallback
        val avatarSrc = info.avatarUrl.ifEmpty {
            "https://ui-avatars.com/api/?name=${info.uploader}&background=8b5cf6&color=fff&size=128"
        }
        Glide.with(this).load(avatarSrc).transform(CircleCrop()).into(imgAvatar)

        tvUploader.text = info.uploader
        tvPlatform.text = info.platform
        tvTitle.text = if (profileOnly) "${info.platform} Profile" else info.title

        if (profileOnly || currentTab == 5) {
            tvSelectQuality.visibility = View.GONE
            layoutFormats.visibility = View.GONE
            btnDownload.visibility = View.GONE
        } else {
            tvSelectQuality.visibility = View.VISIBLE
            layoutFormats.visibility = View.VISIBLE
            btnDownload.visibility = View.VISIBLE

            renderFormats(info.formats)
        }
    }

    // ─── Render Formats cleanly in LinearLayout (No RecyclerView clipping bugs!) ──
    private fun renderFormats(formats: List<FormatItem>) {
        layoutFormats.removeAllViews()
        val cardViews = mutableListOf<View>()
        selectedFormat = formats.firstOrNull()

        formats.forEachIndexed { index, fmt ->
            val view = layoutInflater.inflate(R.layout.item_format_card, layoutFormats, false)
            val badge = view.findViewById<TextView>(R.id.tvQualityBadge)
            val label = view.findViewById<TextView>(R.id.tvQualityLabel)
            val size  = view.findViewById<TextView>(R.id.tvQualitySize)
            val selectedIcon = view.findViewById<ImageView>(R.id.ivSelected)
            val root = view.findViewById<View>(R.id.cardRoot)

            badge.text = fmt.qualityBadge
            label.text = fmt.qualityLabel
            size.text = fmt.sizeEstimate

            if (index == 0) {
                selectedIcon.visibility = View.VISIBLE
                root.setBackgroundResource(R.drawable.format_card_selected_bg)
            } else {
                selectedIcon.visibility = View.INVISIBLE
                root.setBackgroundResource(R.drawable.format_card_bg)
            }

            view.setOnClickListener {
                selectedFormat = fmt
                cardViews.forEachIndexed { i, v ->
                    val icon = v.findViewById<ImageView>(R.id.ivSelected)
                    val r    = v.findViewById<View>(R.id.cardRoot)
                    if (formats[i] == fmt) {
                        icon.visibility = View.VISIBLE
                        r.setBackgroundResource(R.drawable.format_card_selected_bg)
                    } else {
                        icon.visibility = View.INVISIBLE
                        r.setBackgroundResource(R.drawable.format_card_bg)
                    }
                }
            }

            cardViews.add(view)
            layoutFormats.addView(view)
        }
    }

    // ─── Download (Direct via DownloadManager for TikTok, Native yt-dlp for YT/IG/FB) ──
    private fun startDownload(info: MediaInfo, fmt: FormatItem) {
        // If we already have a direct link (e.g., Tikwm watermark-free MP4/MP3)
        if (!fmt.directUrl.isNullOrEmpty()) {
            val filename = DownloadHelper.filename(info.title, fmt.qualityBadge, fmt.isAudio)
            val mime = DownloadHelper.mimeType(fmt.isAudio, fmt.directUrl)
            DownloadHelper.download(this, fmt.directUrl, filename, mime)
            return
        }

        // Otherwise invoke Native yt-dlp Engine!
        layoutProgress.visibility = View.VISIBLE
        btnDownload.isEnabled = false
        tvDownloadStatus.text = "Initializing native yt-dlp engine..."
        downloadProgressBar.isIndeterminate = false
        downloadProgressBar.progress = 0
        tvProgress.text = "0%"

        val downloadDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "ARM")
        if (!downloadDir.exists()) downloadDir.mkdirs()

        val filename = DownloadHelper.filename(info.title, fmt.qualityBadge, fmt.isAudio)
        val targetFile = File(downloadDir, filename)
        if (targetFile.exists()) targetFile.delete()

        val request = YoutubeDLRequest(info.originalUrl).apply {
            addOption("-o", targetFile.absolutePath)
            addOption("--no-mtime")
            if (fmt.isAudio) {
                addOption("-x")
                addOption("--audio-format", "mp3")
                addOption("--audio-quality", "0")
            } else {
                when (fmt.cobaltQuality) {
                    "2160" -> addOption("-f", "bestvideo[height<=2160]+bestaudio/best[height<=2160]/best")
                    "1080" -> addOption("-f", "bestvideo[height<=1080]+bestaudio/best[height<=1080]/best")
                    "720"  -> addOption("-f", "bestvideo[height<=720]+bestaudio/best[height<=720]/best")
                    "480"  -> addOption("-f", "bestvideo[height<=480]+bestaudio/best[height<=480]/best")
                    else   -> addOption("-f", "bestvideo+bestaudio/best")
                }
            }
        }

        val processId = "DL_${System.currentTimeMillis()}"
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    YoutubeDL.getInstance().execute(request, { progress, eta ->
                        scope.launch {
                            downloadProgressBar.progress = progress.toInt()
                            tvProgress.text = "${progress.toInt()}%"
                            tvDownloadStatus.text = if (eta > 0) "Downloading (ETA: ${eta}s)..." else "Downloading..."
                        }
                    }, processId)
                }
                tvDownloadStatus.text = "Download Complete! Saved in Downloads/ARM/"
                downloadProgressBar.progress = 100
                tvProgress.text = "100%"
                toast("Saved to Downloads/ARM/$filename")
                btnDownload.isEnabled = true
            } catch (e: Exception) {
                android.util.Log.e("ARM", "Download failure", e)
                toast("Download failed: ${e.message}")
                layoutProgress.visibility = View.GONE
                btnDownload.isEnabled = true
            }
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────
    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        tvLoadingMsg.visibility = if (show) View.VISIBLE else View.GONE
        btnFetch.isEnabled = !show
    }

    private fun resetPreview() {
        layoutPreview.visibility = View.GONE
        layoutProgress.visibility = View.GONE
        currentMediaInfo = null
        selectedFormat = null
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
