package com.arm.downloader

import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import coil.load
import com.arm.downloader.databinding.ActivityMainBinding
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val extractor = MediaExtractor()
    private lateinit var downloadHelper: DownloadHelper
    
    private var currentMediaInfo: MediaInfo? = null
    private var selectedFormat: FormatItem? = null
    private var activePlatform = "all"
    private var activeProfilePlatform = "tiktok"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        downloadHelper = DownloadHelper(this)

        setupTabs()
        setupListeners()
    }

    private fun setupTabs() {
        binding.tabLayoutMain.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> setPlatform("all", "Paste YouTube, TikTok, Instagram or Facebook link...")
                    1 -> setPlatform("youtube", "Paste YouTube video or Shorts link here...")
                    2 -> setPlatform("tiktok", "Paste TikTok video link here...")
                    3 -> setPlatform("instagram", "Paste Instagram Reel or Post link here...")
                    4 -> setPlatform("facebook", "Paste Facebook video link here...")
                    5 -> setPlatform("thumbnail", "Paste link to extract HD Thumbnail image...")
                    6 -> setPlatform("profile", "Paste profile link or username...")
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        binding.tabLayoutProfile.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                activeProfilePlatform = when (tab?.position) {
                    0 -> "tiktok"
                    1 -> "youtube"
                    2 -> "instagram"
                    else -> "facebook"
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setPlatform(platform: String, placeholder: String) {
        activePlatform = platform
        binding.etUrl.hint = placeholder
        if (platform == "profile") {
            binding.tabLayoutProfile.visibility = View.VISIBLE
        } else {
            binding.tabLayoutProfile.visibility = View.GONE
        }
    }

    private fun setupListeners() {
        binding.btnPaste.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = clipboard.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                val text = clipData.getItemAt(0).text.toString()
                binding.etUrl.setText(text)
            }
        }

        binding.btnFetch.setOnClickListener {
            val url = binding.etUrl.text.toString().trim()
            if (url.isEmpty()) {
                Toast.makeText(this, "Please enter a valid link or username", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            fetchMedia(url)
        }

        binding.btnDownloadAvatar.setOnClickListener {
            currentMediaInfo?.let { info ->
                val name = "${info.uploaderId}_Profile_Pic.jpg"
                downloadHelper.downloadFile(info.avatarUrl, name, isImage = true)
            }
        }

        binding.btnDownloadThumb.setOnClickListener {
            currentMediaInfo?.let { info ->
                val name = "${info.uploaderId}_Thumbnail.jpg"
                downloadHelper.downloadFile(info.thumbnail, name, isImage = true)
            }
        }

        binding.btnStartDownload.setOnClickListener {
            val info = currentMediaInfo ?: return@setOnClickListener
            val fmt = selectedFormat ?: info.formats.firstOrNull() ?: info.audioOption ?: return@setOnClickListener

            val ext = if (fmt.isAudio) "mp3" else "mp4"
            val name = "ARM_${info.uploaderId}_${fmt.height}p.$ext"
            downloadHelper.downloadFile(fmt.directUrl, name, isAudio = fmt.isAudio)
        }
    }

    private fun fetchMedia(url: String) {
        binding.progressBarLoading.visibility = View.VISIBLE
        binding.cardPreview.visibility = View.GONE

        lifecycleScope.launch {
            val isProfileMode = (activePlatform == "profile")
            val platformHint = if (isProfileMode) activeProfilePlatform else activePlatform
            
            val result = extractor.extractMediaInfo(url, platformHint, isProfileMode)
            binding.progressBarLoading.visibility = View.GONE

            result.onSuccess { info ->
                currentMediaInfo = info
                renderPreview(info)
            }.onFailure { err ->
                Toast.makeText(this@MainActivity, "Error: ${err.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun renderPreview(info: MediaInfo) {
        binding.cardPreview.visibility = View.VISIBLE
        binding.tvTitle.text = info.title
        binding.tvUploader.text = info.uploader
        binding.tvPlatform.text = info.platform
        
        binding.imgAvatar.load(info.avatarUrl) {
            crossfade(true)
            placeholder(R.drawable.ic_profile_placeholder)
        }
        binding.imgThumb.load(info.thumbnail) {
            crossfade(true)
            placeholder(R.drawable.ic_thumb_placeholder)
        }

        if (info.isProfileOnly) {
            binding.layoutFormats.visibility = View.GONE
        } else {
            binding.layoutFormats.visibility = View.VISIBLE
            selectedFormat = info.formats.firstOrNull() ?: info.audioOption
            binding.btnStartDownload.text = "Start ${selectedFormat?.label ?: "Download"}"
        }
    }
}
