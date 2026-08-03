package com.arm.downloader

import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.google.android.material.navigation.NavigationView
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.*
import java.io.File

class MainActivity : AppCompatActivity() {

    private val extractor = MediaExtractor()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Views
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var btnMenu: ImageView
    private lateinit var btnHeaderGallery: TextView

    // Mode Chips
    private lateinit var chipAll: TextView
    private lateinit var chipYt: TextView
    private lateinit var chipTiktok: TextView
    private lateinit var chipInsta: TextView
    private lateinit var chipFb: TextView
    private lateinit var chipThumb: TextView
    private lateinit var chipPfp: TextView

    // Profile Sub-Mode buttons
    private lateinit var layoutProfilePlatforms: LinearLayout
    private lateinit var btnPfpTiktok: Button
    private lateinit var btnPfpYt: Button
    private lateinit var btnPfpInsta: Button
    private lateinit var btnPfpFb: Button

    // Input & Loading
    private lateinit var etUrl: EditText
    private lateinit var btnPaste: Button
    private lateinit var btnFetch: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvLoadingMsg: TextView

    // Preview Section
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

    // Transforming Progress Button
    private lateinit var layoutTransformingBtn: RelativeLayout
    private lateinit var downloadProgressBar: ProgressBar
    private lateinit var tvTransformingText: TextView

    // State
    private var currentMediaInfo: MediaInfo? = null
    private var selectedFormat: FormatItem? = null
    private var currentMode = "All"
    private var pfpPlatform = "TikTok"
    private var isDownloading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initEngine()
        bindViews()
        setupSidebar()
        setupSocialChips()
        setupButtons()
    }

    private fun initEngine() {
        try {
            YoutubeDL.getInstance().init(applicationContext)
            FFmpeg.getInstance().init(applicationContext)
            scope.launch(Dispatchers.IO) {
                try {
                    YoutubeDL.getInstance().updateYoutubeDL(applicationContext, YoutubeDL.UpdateChannel.STABLE)
                } catch (e: Exception) {
                    // ignore offline network check
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ARM", "Failed to init downloader", e)
        }
    }

    private fun bindViews() {
        drawerLayout        = findViewById(R.id.drawerLayout)
        navigationView      = findViewById(R.id.navigationView)
        btnMenu             = findViewById(R.id.btnMenu)
        btnHeaderGallery    = findViewById(R.id.btnHeaderGallery)

        chipAll             = findViewById(R.id.chipAll)
        chipYt              = findViewById(R.id.chipYt)
        chipTiktok          = findViewById(R.id.chipTiktok)
        chipInsta           = findViewById(R.id.chipInsta)
        chipFb              = findViewById(R.id.chipFb)
        chipThumb           = findViewById(R.id.chipThumb)
        chipPfp             = findViewById(R.id.chipPfp)

        layoutProfilePlatforms = findViewById(R.id.layoutProfilePlatforms)
        btnPfpTiktok        = findViewById(R.id.btnPfpTiktok)
        btnPfpYt            = findViewById(R.id.btnPfpYt)
        btnPfpInsta         = findViewById(R.id.btnPfpInsta)
        btnPfpFb            = findViewById(R.id.btnPfpFb)

        etUrl               = findViewById(R.id.etUrl)
        btnPaste            = findViewById(R.id.btnPaste)
        btnFetch            = findViewById(R.id.btnFetch)
        progressBar         = findViewById(R.id.progressBarLoading)
        tvLoadingMsg        = findViewById(R.id.tvLoadingMsg)

        layoutPreview       = findViewById(R.id.layoutPreview)
        imgThumb            = findViewById(R.id.imgThumb)
        imgAvatar           = findViewById(R.id.imgAvatar)
        tvUploader          = findViewById(R.id.tvUploader)
        tvPlatform          = findViewById(R.id.tvPlatform)
        tvTitle             = findViewById(R.id.tvTitle)
        btnSaveThumbnail    = findViewById(R.id.btnSaveThumbnail)
        btnSaveAvatar       = findViewById(R.id.btnSaveAvatar)
        layoutFormats       = findViewById(R.id.layoutFormats)
        tvSelectQuality     = findViewById(R.id.tvSelectQuality)

        layoutTransformingBtn = findViewById(R.id.layoutTransformingBtn)
        downloadProgressBar   = findViewById(R.id.downloadProgressBar)
        tvTransformingText    = findViewById(R.id.tvTransformingText)
    }

    private fun setupSidebar() {
        btnMenu.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        btnHeaderGallery.setOnClickListener {
            DownloadHelper.openGallery(this)
        }

        navigationView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> drawerLayout.closeDrawer(GravityCompat.START)
                R.id.nav_gallery -> {
                    drawerLayout.closeDrawer(GravityCompat.START)
                    DownloadHelper.openGallery(this)
                }
                R.id.nav_history -> {
                    drawerLayout.closeDrawer(GravityCompat.START)
                    DownloadHelper.showHistoryDialog(this)
                }
                R.id.nav_platforms -> {
                    drawerLayout.closeDrawer(GravityCompat.START)
                    showInfoDialog("Supported Platforms 🎬",
                        "ARM DOWNLOADER PRO extracts high speed media from:\n\n" +
                        "📺 YouTube: Up to 4K Ultra HD Video & 320kbps MP3 Audio.\n" +
                        "🎵 TikTok: HD No-Watermark MP4 & original sound extraction.\n" +
                        "📸 Instagram: Reels, IGTV, Photos & clips.\n" +
                        "📘 Facebook: Public Watch videos & Shorts.\n\n" +
                        "✔ All downloads save directly to your phone Gallery!")
                }
                R.id.nav_about -> {
                    drawerLayout.closeDrawer(GravityCompat.START)
                    showInfoDialog("About ARM DOWNLOADER", "Version 2.0 Pro\nHigh Speed Media & Video Saver.\nDesigned for ultra fast downloads and studio quality offline viewing.")
                }
            }
            true
        }
    }

    private fun setupSocialChips() {
        val allChips = listOf(chipAll, chipYt, chipTiktok, chipInsta, chipFb, chipThumb, chipPfp)
        
        fun selectChip(selected: TextView, mode: String, hint: String) {
            currentMode = mode
            etUrl.hint = hint
            layoutProfilePlatforms.visibility = if (mode == "PFP") View.VISIBLE else View.GONE
            resetPreview()

            allChips.forEach { chip ->
                if (chip == selected) {
                    chip.setBackgroundResource(R.drawable.social_chip_selected_bg)
                    chip.setTextColor(android.graphics.Color.WHITE)
                } else {
                    chip.setBackgroundResource(R.drawable.social_chip_bg)
                    chip.setTextColor(android.graphics.Color.parseColor("#94a3b8"))
                }
            }
        }

        chipAll.setOnClickListener { selectChip(chipAll, "All", "Paste any YouTube, TikTok, Insta, or FB link...") }
        chipYt.setOnClickListener { selectChip(chipYt, "YouTube", "Paste YouTube video or Shorts link for 4K extract...") }
        chipTiktok.setOnClickListener { selectChip(chipTiktok, "TikTok", "Paste TikTok link for HD No-Watermark MP4...") }
        chipInsta.setOnClickListener { selectChip(chipInsta, "Instagram", "Paste Instagram Reel or Video post link...") }
        chipFb.setOnClickListener { selectChip(chipFb, "Facebook", "Paste Facebook watch or post link...") }
        chipThumb.setOnClickListener { selectChip(chipThumb, "Thumbnail", "Paste any video link to extract HD thumbnail...") }
        chipPfp.setOnClickListener { selectChip(chipPfp, "PFP", "Enter creator username (e.g. @MrBeast or username)...") }

        val pfpBtns = listOf(btnPfpTiktok, btnPfpYt, btnPfpInsta, btnPfpFb)
        fun selectPfpBtn(btn: Button, platform: String) {
            pfpPlatform = platform
            etUrl.hint = "Enter $platform username (e.g. @username)..."
            pfpBtns.forEach { b ->
                if (b == btn) {
                    b.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#8b5cf6"))
                    b.setTextColor(android.graphics.Color.WHITE)
                } else {
                    b.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#334155"))
                    b.setTextColor(android.graphics.Color.parseColor("#94a3b8"))
                }
            }
        }
        btnPfpTiktok.setOnClickListener { selectPfpBtn(btnPfpTiktok, "TikTok") }
        btnPfpYt.setOnClickListener { selectPfpBtn(btnPfpYt, "YouTube") }
        btnPfpInsta.setOnClickListener { selectPfpBtn(btnPfpInsta, "Instagram") }
        btnPfpFb.setOnClickListener { selectPfpBtn(btnPfpFb, "Facebook") }
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
            if (url.isEmpty()) { toast("Please paste a URL or enter username"); return@setOnClickListener }
            if (currentMode == "PFP") fetchProfile(url) else fetchMedia(url)
        }

        layoutTransformingBtn.setOnClickListener {
            if (isDownloading) return@setOnClickListener
            val info = currentMediaInfo ?: return@setOnClickListener
            val fmt  = selectedFormat ?: return@setOnClickListener
            startTransformingDownload(info, fmt)
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
        val platform = when (currentMode) {
            "YouTube"   -> "YouTube"
            "TikTok"    -> "TikTok"
            "Instagram" -> "Instagram"
            "Facebook"  -> "Facebook"
            "Thumbnail" -> "YouTube"
            else        -> extractor.detectPlatform(url)
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
                toast("Error extracting video. Check your connection or link!")
            }
        }
    }

    private fun fetchProfile(username: String) {
        val cleanUser = username.trimStart('@')
        val avatarUrl = extractor.getProfileAvatarUrl(cleanUser, pfpPlatform)

        currentMediaInfo = MediaInfo(
            title = "$pfpPlatform Profile",
            thumbnailUrl = avatarUrl,
            uploader = "@$cleanUser",
            avatarUrl = avatarUrl,
            platform = pfpPlatform,
            formats = emptyList(),
            originalUrl = avatarUrl
        )
        showPreview(currentMediaInfo!!, profileOnly = true)
    }

    private fun showPreview(info: MediaInfo, profileOnly: Boolean = false) {
        layoutPreview.visibility = View.VISIBLE

        if (info.thumbnailUrl.isNotEmpty()) {
            Glide.with(this).load(info.thumbnailUrl).into(imgThumb)
        }

        val avatarSrc = info.avatarUrl.ifEmpty {
            "https://ui-avatars.com/api/?name=${info.uploader}&background=8b5cf6&color=fff&size=128"
        }
        Glide.with(this).load(avatarSrc).transform(CircleCrop()).into(imgAvatar)

        tvUploader.text = info.uploader
        tvPlatform.text = info.platform
        tvTitle.text = if (profileOnly) "${info.platform} Profile" else info.title

        if (profileOnly || currentMode == "Thumbnail") {
            tvSelectQuality.visibility = View.GONE
            layoutFormats.visibility = View.GONE
            layoutTransformingBtn.visibility = View.GONE
        } else {
            tvSelectQuality.visibility = View.VISIBLE
            layoutFormats.visibility = View.VISIBLE
            layoutTransformingBtn.visibility = View.VISIBLE

            resetTransformingButton()
            renderFormats(info.formats)
        }
    }

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
                if (isDownloading) return@setOnClickListener
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

    // ─── Ultra Fast Download & Gallery Saving ──────────────────────────────────
    private fun startTransformingDownload(info: MediaInfo, fmt: FormatItem) {
        isDownloading = true
        layoutTransformingBtn.setBackgroundResource(R.drawable.btn_progress_bg)
        downloadProgressBar.progress = 0
        tvTransformingText.text = "⏳ Starting High Speed Download..."

        if (!fmt.directUrl.isNullOrEmpty()) {
            val filename = DownloadHelper.filename(info.title, fmt.qualityBadge, fmt.isAudio)
            val mime = DownloadHelper.mimeType(fmt.isAudio, fmt.directUrl)
            DownloadHelper.download(this, fmt.directUrl, filename, mime)
            
            scope.launch {
                delay(1500)
                setDownloadSuccessState("✔ Queued Directly to Gallery!")
            }
            return
        }

        val targetDir = DownloadHelper.getGalleryDirectory(fmt.isAudio)
        val filename = DownloadHelper.filename(info.title, fmt.qualityBadge, fmt.isAudio)
        val targetFile = File(targetDir, filename)
        if (targetFile.exists()) targetFile.delete()

        // High speed & resilience configuration (no warnings to block execution)
        val request = YoutubeDLRequest(info.originalUrl).apply {
            addOption("-o", targetFile.absolutePath)
            addOption("--no-mtime")
            addOption("--no-warnings")
            addOption("--ignore-errors")
            addOption("--no-check-certificate")
            addOption("--no-playlist")
            addOption("--concurrent-fragments", "8")
            addOption("--retries", "10")
            if (fmt.isAudio) {
                addOption("-x")
                addOption("--audio-format", "mp3")
                addOption("--audio-quality", "0")
            } else {
                when (fmt.cobaltQuality) {
                    "2160" -> addOption("-f", "best[height<=?2160]/bestvideo[height<=?2160]+bestaudio/best")
                    "1080" -> addOption("-f", "best[height<=?1080]/bestvideo[height<=?1080]+bestaudio/best")
                    "720"  -> addOption("-f", "best[height<=?720]/bestvideo[height<=?720]+bestaudio/best")
                    "480"  -> addOption("-f", "best[height<=?480]/bestvideo[height<=?480]/best")
                    else   -> addOption("-f", "best/bestvideo+bestaudio")
                }
            }
        }

        val processId = "DL_${System.currentTimeMillis()}"
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    YoutubeDL.getInstance().execute(request, processId, callback = { progress: Float, eta: Long, _: String ->
                        scope.launch {
                            val p = progress.toInt().coerceIn(0, 99)
                            downloadProgressBar.progress = p
                            tvTransformingText.text = if (eta > 0) "Downloading ($p%) — ETA: ${eta}s" else "Downloading ($p%)..."
                        }
                    })
                }
                
                val mime = DownloadHelper.mimeType(fmt.isAudio, targetFile.name)
                DownloadHelper.registerInGallery(this@MainActivity, targetFile, mime)
                setDownloadSuccessState("✔ Saved directly to Phone Gallery!")

            } catch (e: Exception) {
                android.util.Log.e("ARM", "Download error", e)
                toast("Download interrupted. Please check network or try alternative quality.")
                resetTransformingButton()
            }
        }
    }

    private fun setDownloadSuccessState(msg: String) {
        downloadProgressBar.progress = 100
        layoutTransformingBtn.setBackgroundResource(R.drawable.btn_download_success)
        tvTransformingText.text = msg
        isDownloading = false

        scope.launch {
            delay(4500)
            if (!isDownloading && layoutTransformingBtn.visibility == View.VISIBLE) {
                resetTransformingButton()
            }
        }
    }

    private fun resetTransformingButton() {
        isDownloading = false
        layoutTransformingBtn.setBackgroundResource(R.drawable.btn_download_normal)
        downloadProgressBar.progress = 0
        tvTransformingText.text = "⚡ Start Fast Download to Gallery"
    }

    private fun showInfoDialog(title: String, message: String) {
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Awesome!") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        tvLoadingMsg.visibility = if (show) View.VISIBLE else View.GONE
        btnFetch.isEnabled = !show
    }

    private fun resetPreview() {
        layoutPreview.visibility = View.GONE
        currentMediaInfo = null
        selectedFormat = null
        isDownloading = false
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
