package com.arm.downloader

import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.*

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
    private lateinit var rvFormats: RecyclerView
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
        bindViews()
        setupTabs()
        setupButtons()
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
        rvFormats       = findViewById(R.id.rvFormats)
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
                    "pfp_${info.uploader}_${System.currentTimeMillis()}.jpg", "image/jpeg")
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
            5 -> "YouTube" // Thumbnail - try YouTube first
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

        // Avatar
        val avatarSrc = info.avatarUrl.ifEmpty {
            "https://ui-avatars.com/api/?name=${info.uploader}&background=8b5cf6&color=fff&size=128"
        }
        Glide.with(this).load(avatarSrc).transform(CircleCrop()).into(imgAvatar)

        tvUploader.text = info.uploader
        tvPlatform.text = info.platform
        tvTitle.text = if (profileOnly) "${info.platform} Profile" else info.title

        if (profileOnly || currentTab == 5) {
            // Thumbnail/Profile mode — only show save buttons
            tvSelectQuality.visibility = View.GONE
            rvFormats.visibility = View.GONE
            btnDownload.visibility = View.GONE
        } else {
            // Video mode — show quality cards
            tvSelectQuality.visibility = View.VISIBLE
            rvFormats.visibility = View.VISIBLE
            btnDownload.visibility = View.VISIBLE

            val adapter = FormatAdapter(info.formats) { fmt ->
                selectedFormat = fmt
            }
            rvFormats.layoutManager = LinearLayoutManager(this)
            rvFormats.adapter = adapter
            selectedFormat = info.formats.firstOrNull()
        }
    }

    // ─── Download ─────────────────────────────────────────────────────────────
    private fun startDownload(info: MediaInfo, fmt: FormatItem) {
        layoutProgress.visibility = View.VISIBLE
        btnDownload.isEnabled = false
        tvDownloadStatus.text = "Resolving download link..."
        downloadProgressBar.isIndeterminate = true

        scope.launch {
            try {
                val downloadUrl = extractor.resolveDownloadUrl(
                    info.originalUrl,
                    fmt.cobaltQuality,
                    fmt.isAudio,
                    fmt.directUrl
                )

                if (downloadUrl.isEmpty()) {
                    toast("Could not get download link. Try another quality.")
                    layoutProgress.visibility = View.GONE
                    btnDownload.isEnabled = true
                    return@launch
                }

                val filename = DownloadHelper.filename(info.title, fmt.qualityBadge, fmt.isAudio)
                val mime = DownloadHelper.mimeType(fmt.isAudio)
                DownloadHelper.download(this@MainActivity, downloadUrl, filename, mime)

                tvDownloadStatus.text = "Download queued!"
                downloadProgressBar.isIndeterminate = false
                downloadProgressBar.progress = 100
                tvProgress.text = "100%"
                btnDownload.isEnabled = true
            } catch (e: Exception) {
                toast("Download error: ${e.message}")
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
