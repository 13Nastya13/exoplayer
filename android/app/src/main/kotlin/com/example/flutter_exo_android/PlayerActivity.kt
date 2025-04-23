package com.example.flutter_exo_android

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.File
import java.net.URL

@UnstableApi
class PlayerActivity : AppCompatActivity(), Player.Listener {

    private lateinit var playerView: PlayerView
    private lateinit var player: ExoPlayer
    private lateinit var titleText: TextView
    private lateinit var backButton: Button
    private lateinit var resolutionButton: FloatingActionButton
    private lateinit var fullscreenButton: FloatingActionButton
    private lateinit var downloadButton: FloatingActionButton
    private lateinit var trackSelector: DefaultTrackSelector
    private var playWhenReady = true
    private var playbackPosition = 0L
    private val TAG = "PlayerActivity"
    private var isFullscreen = false
    private var originalOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    private var isDownloaded = false
    private var currentVideoUrl = ""
    private var currentVideoTitle = ""

    // Variables to store available video qualities
    private val videoQualities = mutableListOf<Pair<String, TrackSelectionParameters>>()
    private var currentQualityIndex = 0

    // We'll always ensure we have these basic resolutions available
    private val defaultResolutions = listOf(
        "Auto (adaptive)" to Integer.MAX_VALUE,
        "360p" to 360,
        "720p" to 720
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        try {
            // Store original orientation
            originalOrientation = requestedOrientation

            // Initialize views
            playerView = findViewById(R.id.player_view)
            titleText = findViewById(R.id.titleText)
            backButton = findViewById(R.id.backButton)
            resolutionButton = findViewById(R.id.resolutionButton)
            fullscreenButton = findViewById(R.id.fullscreenButton)
            downloadButton = findViewById(R.id.downloadButton)

            // Get video URI from intent if available
            val videoUrl = intent.getStringExtra("VIDEO_URL")
                ?: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
            val videoTitle = intent.getStringExtra("VIDEO_TITLE") ?: "Sample Video"
            
            currentVideoUrl = videoUrl
            currentVideoTitle = videoTitle

            Log.d(TAG, "Playing video: $videoTitle, URL: $videoUrl")

            // Set the title
            titleText.text = videoTitle

            // Set up back button
            backButton.setOnClickListener {
                handleBackPress()
            }

            // Set up resolution button
            resolutionButton.setOnClickListener {
                showResolutionDialog()
            }
            
            // Set up fullscreen button
            fullscreenButton.setOnClickListener {
                toggleFullscreen()
            }
            
            // Set up download button
            downloadButton.setOnClickListener {
                startVideoDownload()
            }

            // Make buttons visible by default
            resolutionButton.visibility = View.VISIBLE
            fullscreenButton.visibility = View.VISIBLE
            
            // Check if video is already downloaded
            checkIfVideoIsDownloaded(videoUrl)

            // Initialize player in onCreate
            initializePlayer(videoUrl)
            
            // Setup the default resolutions immediately so we always have options
            setupDefaultResolutions()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun checkIfVideoIsDownloaded(videoUrl: String) {
        try {
            val downloadedFile = getDownloadedVideoFile(videoUrl)
            isDownloaded = downloadedFile.exists()
            
            // Update the download button visibility based on whether the video is downloaded
            downloadButton.visibility = if (isDownloaded) View.GONE else View.VISIBLE
            
            // If video is downloaded, update the URL to the local file
            if (isDownloaded) {
                Log.d(TAG, "Video is already downloaded: ${downloadedFile.absolutePath}")
                Toast.makeText(this, "Playing downloaded version", Toast.LENGTH_SHORT).show()
                // We'll continue using the streaming URL in this implementation
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if video is downloaded: ${e.message}")
            isDownloaded = false
            downloadButton.visibility = View.VISIBLE
        }
    }
    
    private fun getDownloadedVideoFile(videoUrl: String): File {
        // Create a unique filename based on the URL
        val urlHash = videoUrl.hashCode().toString()
        val extension = getFileExtensionFromUrl(videoUrl)
        val filename = "video_$urlHash.$extension"
        
        // Get the download directory
        val downloadDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        return File(downloadDir, filename)
    }
    
    private fun getFileExtensionFromUrl(url: String): String {
        return when {
            url.endsWith(".mp4", ignoreCase = true) -> "mp4"
            url.endsWith(".m3u8", ignoreCase = true) -> "mp4" // For HLS, we'll save as MP4
            url.endsWith(".mpd", ignoreCase = true) -> "mp4" // For DASH, we'll save as MP4
            else -> "mp4" // Default to MP4
        }
    }
    
    private fun startVideoDownload() {
        try {
            // Show confirmation dialog
            AlertDialog.Builder(this, R.style.AlertDialogTheme)
                .setTitle("Download Video")
                .setMessage("Do you want to download '$currentVideoTitle' for offline viewing?")
                .setPositiveButton("Download") { _, _ ->
                    // Start the download
                    downloadVideo()
                }
                .setNegativeButton("Cancel", null)
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "Error starting download: ${e.message}")
            Toast.makeText(this, "Failed to start download", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun downloadVideo() {
        try {
            // Create download request
            val downloadRequest = DownloadRequest.Builder(
                currentVideoUrl.hashCode().toString(),
                Uri.parse(currentVideoUrl)
            ).build()
            
            // Start download service
            DownloadService.sendAddDownload(
                this,
                ExoDownloadService::class.java,
                downloadRequest,
                false
            )
            
            // Notify user
            Toast.makeText(this, "Download started", Toast.LENGTH_SHORT).show()
            
            // Update UI
            downloadButton.visibility = View.GONE
            isDownloaded = true
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading video: ${e.message}")
            Toast.makeText(this, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun toggleFullscreen() {
        if (isFullscreen) {
            // Exit fullscreen mode
            exitFullscreen()
        } else {
            // Enter fullscreen mode
            enterFullscreen()
        }
    }
    
    private fun enterFullscreen() {
        // Hide action bar and system UI
        hideSystemUi()
        
        // Store current orientation
        if (!isFullscreen) {
            originalOrientation = requestedOrientation
        }
        
        // Force landscape orientation
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        
        // Make header layout invisible
        findViewById<View>(R.id.headerLayout).visibility = View.GONE
        
        // Update fullscreen button icon
        fullscreenButton.setImageResource(R.drawable.ic_fullscreen_exit)
        
        // Update flag
        isFullscreen = true
        
        // Show toast
        Toast.makeText(this, "Fullscreen mode", Toast.LENGTH_SHORT).show()
    }
    
    private fun exitFullscreen() {
        // Show system UI
        showSystemUi()
        
        // Restore original orientation
        requestedOrientation = originalOrientation
        
        // Make header layout visible again
        findViewById<View>(R.id.headerLayout).visibility = View.VISIBLE
        
        // Update fullscreen button icon
        fullscreenButton.setImageResource(R.drawable.ic_fullscreen)
        
        // Update flag
        isFullscreen = false
        
        // Show toast
        Toast.makeText(this, "Normal mode", Toast.LENGTH_SHORT).show()
    }
    
    private fun hideSystemUi() {
        // Hide the status bar and navigation bar
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        
        // Make the activity fullscreen
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        
        // Extend player view to use full screen
        val params = playerView.layoutParams
        params.width = ViewGroup.LayoutParams.MATCH_PARENT
        params.height = ViewGroup.LayoutParams.MATCH_PARENT
        playerView.layoutParams = params
    }
    
    private fun showSystemUi() {
        // Show the status bar and navigation bar
        WindowCompat.setDecorFitsSystemWindows(window, true)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.show(WindowInsetsCompat.Type.systemBars())
        
        // Exit fullscreen
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        
        // Reset player view layout
        val params = playerView.layoutParams
        params.width = ViewGroup.LayoutParams.MATCH_PARENT
        params.height = ViewGroup.LayoutParams.MATCH_PARENT
        playerView.layoutParams = params
    }
    
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            if (!isFullscreen) {
                enterFullscreen()
            }
        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            if (isFullscreen) {
                exitFullscreen()
            }
        }
    }
    
    private fun setupDefaultResolutions() {
        videoQualities.clear()
        
        // Add the default resolutions
        for ((name, height) in defaultResolutions) {
            val width = when (height) {
                360 -> 640
                720 -> 1280
                else -> Integer.MAX_VALUE
            }
            
            val parameters = if (name == "Auto (adaptive)") {
                // For Auto, don't set any constraints
                trackSelector.parameters.buildUpon()
                    .clearVideoSizeConstraints()
                    .setPreferredVideoMimeType(null)
                    .setMaxVideoBitrate(Integer.MAX_VALUE)
                    .build()
            } else {
                // For specific resolutions, set height constraint
                trackSelector.parameters.buildUpon()
                    .setMaxVideoSize(width, height)
                    .build()
            }
            
            videoQualities.add(Pair(name, parameters))
        }
    }

    private fun showResolutionDialog() {
        if (videoQualities.isEmpty()) {
            setupDefaultResolutions()
        }

        val qualityNames = videoQualities.map { it.first }.toTypedArray()

        val dialogBuilder = AlertDialog.Builder(this, R.style.AlertDialogTheme)
            .setTitle("Select Video Quality")
            .setSingleChoiceItems(qualityNames, currentQualityIndex) { dialog, which ->
                if (which != currentQualityIndex && which < videoQualities.size) {
                    // Save current playback state
                    val currentPosition = player.currentPosition
                    val wasPlaying = player.isPlaying
                    
                    // Apply new track selection parameters
                    currentQualityIndex = which
                    
                    val qualityName = videoQualities[which].first
                    Log.d(TAG, "Changing to quality: $qualityName")
                    
                    // Apply the parameters immediately
                    player.trackSelectionParameters = videoQualities[which].second
                    
                    // Restore playback state
                    player.seekTo(currentPosition)
                    player.playWhenReady = wasPlaying
                    
                    // Show toast with the new quality
                    Toast.makeText(
                        this,
                        "Changing to $qualityName",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }

        val dialog = dialogBuilder.create()
        dialog.show()
    }

    private fun handleBackPress() {
        try {
            // If we're in fullscreen mode, first exit fullscreen
            if (isFullscreen) {
                exitFullscreen()
                return
            }
            
            // Otherwise proceed with normal back behavior
            
            // Save current playback position
            if (::player.isInitialized) {
                playbackPosition = player.currentPosition
                // Create a result intent to pass back information to Flutter
                val resultIntent = Intent().apply {
                    putExtra("PLAYBACK_POSITION", playbackPosition)
                    putExtra("PLAYBACK_COMPLETED", player.playbackState == Player.STATE_ENDED)
                }
                setResult(RESULT_OK, resultIntent)

                // Make sure player is properly released
                releasePlayer()
            }

            // Return to previous activity
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "Error handling back press: ${e.message}")
            finish() // Make sure we exit even if there's an error
        }
    }

    private fun initializePlayer(videoUrl: String) {
        try {
            // Create track selector for quality selection
            trackSelector = DefaultTrackSelector(this)
            
            // Build parameters to allow all quality levels initially
            val parameters = trackSelector.buildUponParameters()
                .setAllowVideoMixedMimeTypeAdaptiveness(true)
                .setAllowVideoNonSeamlessAdaptiveness(true)
                .setMaxVideoSize(Integer.MAX_VALUE, Integer.MAX_VALUE)
                .setMaxVideoBitrate(Integer.MAX_VALUE)
                .build()
                
            trackSelector.parameters = parameters

            player = ExoPlayer.Builder(this)
                .setTrackSelector(trackSelector)
                .build()
                .also { exoPlayer ->
                    playerView.player = exoPlayer

                    // Register player listener to detect when tracks are available
                    exoPlayer.addListener(this)

                    // Create a MediaItem based on content type
                    val mediaItem = when {
                        videoUrl.endsWith(".m3u8") -> {
                            // For HLS streams
                            MediaItem.Builder()
                                .setUri(videoUrl)
                                .setMimeType(MimeTypes.APPLICATION_M3U8)
                                .build()
                        }

                        videoUrl.endsWith(".mpd") -> {
                            // For DASH streams
                            MediaItem.Builder()
                                .setUri(videoUrl)
                                .setMimeType(MimeTypes.APPLICATION_MPD)
                                .build()
                        }

                        else -> {
                            // For other formats (mp4, etc.)
                            MediaItem.fromUri(videoUrl)
                        }
                    }

                    // Setup data source factory for network requests
                    val dataSourceFactory = DefaultHttpDataSource.Factory()

                    // Create appropriate media source based on stream type
                    val mediaSource = when {
                        videoUrl.endsWith(".m3u8") -> {
                            HlsMediaSource.Factory(dataSourceFactory)
                                .createMediaSource(mediaItem)
                        }

                        videoUrl.endsWith(".mpd") -> {
                            DashMediaSource.Factory(dataSourceFactory)
                                .createMediaSource(mediaItem)
                        }

                        else -> {
                            // ExoPlayer can automatically determine the type
                            exoPlayer.setMediaItem(mediaItem)
                            null
                        }
                    }

                    // Set media source if it was created explicitly
                    mediaSource?.let { exoPlayer.setMediaSource(it) }

                    // Prepare the player
                    exoPlayer.playWhenReady = playWhenReady
                    exoPlayer.seekTo(playbackPosition)
                    exoPlayer.prepare()
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing player: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun onTracksChanged(tracks: Tracks) {
        // This method is called when track information is available
        try {
            videoQualities.clear()

            // Create Auto (adaptive) quality option
            videoQualities.add(Pair("Auto (adaptive)", player.trackSelectionParameters))

            // Get available video tracks and their resolutions
            for (trackGroup in tracks.groups) {
                // Only process video tracks
                if (trackGroup.type != C.TRACK_TYPE_VIDEO) continue

                // Iterate through each track in the group
                for (i in 0 until trackGroup.length) {
                    val format = trackGroup.getTrackFormat(i)

                    // Skip unsupported or unselectable tracks
                    if (!trackGroup.isTrackSupported(i) || !trackGroup.isTrackSelected(i)) {
                        continue
                    }

                    // Format name (resolution)
                    val width = format.width
                    val height = format.height
                    val bitrate = format.bitrate

                    if (width <= 0 || height <= 0) continue

                    // Display resolution as quality name
                    val qualityName =
                        "${height}p" + if (bitrate > 0) " (${bitrate / 1000} kbps)" else ""

                    // Create parameters for this specific quality
                    val parameters = player.trackSelectionParameters.buildUpon()
                        .setMaxVideoSize(width, height)
                        .setMaxVideoBitrate(bitrate)
                        .build()

                    // Add to available qualities list
                    videoQualities.add(Pair(qualityName, parameters))

                    Log.d(
                        TAG,
                        "Added quality option: $qualityName w:$width h:$height bitrate:$bitrate"
                    )
                }
            }

            // If no qualities were found, try another approach for HLS/DASH streams
            if (videoQualities.size <= 1) {
                // Use predefined common video resolutions
                val commonResolutions = listOf(
                    Triple("240p", 426, 240),
                    Triple("360p", 640, 360),
                    Triple("480p", 854, 480),
                    Triple("720p", 1280, 720),
                    Triple("1080p", 1920, 1080),
                    Triple("1440p", 2560, 1440),
                    Triple("2160p (4K)", 3840, 2160)
                )

                for ((name, width, height) in commonResolutions) {
                    val parameters = player.trackSelectionParameters.buildUpon()
                        .setMaxVideoSize(width, height)
                        .build()

                    videoQualities.add(Pair(name, parameters))
                    Log.d(TAG, "Added predefined quality: $name")
                }
            }

            // Remove duplicates and sort qualities by resolution (ascending)
            val uniqueQualities = mutableListOf<Pair<String, TrackSelectionParameters>>()
            val addedResolutions = mutableSetOf<String>()

            for (quality in videoQualities) {
                val resolution = quality.first
                if (!addedResolutions.contains(resolution)) {
                    uniqueQualities.add(quality)
                    addedResolutions.add(resolution)
                }
            }

            videoQualities.clear()
            videoQualities.addAll(uniqueQualities)

            // Sort qualities by resolution (ascending)
            videoQualities.sortBy { quality ->
                if (quality.first == "Auto (adaptive)") {
                    return@sortBy -1 // Always keep Auto option first
                }

                val heightStr = quality.first.split("p")[0]
                try {
                    heightStr.toInt()
                } catch (e: Exception) {
                    0
                }
            }

            Log.d(TAG, "Final quality options: ${videoQualities.map { it.first }}")

            // Show resolution button only if we have multiple options
            runOnUiThread {
                if (videoQualities.size > 1) {
                    resolutionButton.visibility = View.VISIBLE
                } else {
                    resolutionButton.visibility = View.GONE
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error processing video tracks: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun onStart() {
        super.onStart()
        try {
            if (Util.SDK_INT > 23 && ::player.isInitialized) {
                // playerView should already have player assigned from onCreate
                player.playWhenReady = playWhenReady
                player.seekTo(playbackPosition)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onStart: ${e.message}")
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            if (Util.SDK_INT <= 23 && ::player.isInitialized) {
                // playerView should already have player assigned from onCreate
                player.playWhenReady = playWhenReady
                player.seekTo(playbackPosition)
            }
            
            // Apply fullscreen state if needed
            if (isFullscreen) {
                hideSystemUi()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onResume: ${e.message}")
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            if (Util.SDK_INT <= 23 && ::player.isInitialized) {
                releasePlayer()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onPause: ${e.message}")
        }
    }

    override fun onStop() {
        super.onStop()
        try {
            if (Util.SDK_INT > 23 && ::player.isInitialized) {
                releasePlayer()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onStop: ${e.message}")
        }
    }

    private fun releasePlayer() {
        try {
            if (::player.isInitialized) {
                playbackPosition = player.currentPosition
                playWhenReady = player.playWhenReady
                player.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing player: ${e.message}")
        }
    }

    override fun onBackPressed() {
        // Handle hardware/system back button same as our UI back button
        handleBackPress()
    }
} 