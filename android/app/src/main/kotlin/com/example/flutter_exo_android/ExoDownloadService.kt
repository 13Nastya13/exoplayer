package com.example.flutter_exo_android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import androidx.media3.exoplayer.scheduler.Scheduler
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class)
class ExoDownloadService : DownloadService(
    FOREGROUND_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    CHANNEL_ID,
    R.string.download_channel_name,
    0
) {
    private lateinit var downloadManager: DownloadManager
    private lateinit var downloadNotificationHelper: DownloadNotificationHelper
    private val JOB_ID = 1

    override fun onCreate() {
        super.onCreate()
        
        // Create notification channel
        createNotificationChannel()
        
        // Initialize download notification helper
        downloadNotificationHelper = DownloadNotificationHelper(this, CHANNEL_ID)
        
        // Initialize download manager
        downloadManager = getDownloadManager()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.download_channel_name)
            val description = getString(R.string.download_channel_description)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                this.description = description
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun getDownloadManager(): DownloadManager {
        // Create download manager if it doesn't exist
        if (::downloadManager.isInitialized) {
            return downloadManager
        }
        
        // Create data source factory
        val dataSourceFactory = DefaultDataSource.Factory(this)
        
        // Get the download cache
        val downloadCache = ExoCacheDatabase.getDownloadCache(this)
        
        // Get database provider
        val databaseProvider = ExoCacheDatabase.getInstance(this)
        
        // Create the download manager - constructor has changed in different versions
        try {
            // Try to create download manager using available constructor
            val newDownloadManager = DownloadManager(
                this,
                databaseProvider,
                downloadCache,
                dataSourceFactory, 
                Executor { it.run() }
            )
            
            // Set download properties
            newDownloadManager.maxParallelDownloads = 3
            newDownloadManager.minRetryCount = 3
            newDownloadManager.resumeDownloads()
            
            return newDownloadManager
        } catch (e: Exception) {
            Log.e("ExoDownloadService", "Error creating download manager: ${e.message}")
            e.printStackTrace()
            
            // Fallback method if the above fails
            throw e  // Rethrow to make error visible
        }
    }

    override fun getScheduler(): Scheduler? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            PlatformScheduler(this, JOB_ID)
        } else {
            null
        }
    }

    override fun getForegroundNotification(downloads: List<Download>, notMetRequirements: Int): Notification {
        // If there are no downloads, use a simple notification
        if (downloads.isEmpty()) {
            return createProgressNotification("Download Starting", 0)
        }
        
        // If there's a single download, create a dedicated notification
        if (downloads.size == 1) {
            val download = downloads[0]
            val titleText = "Downloading Video"
            val percentComplete = getDownloadPercentage(download)
            
            return when (download.state) {
                Download.STATE_DOWNLOADING -> {
                    createProgressNotification(
                        "$titleText - $percentComplete%", 
                        percentComplete
                    )
                }
                Download.STATE_COMPLETED -> {
                    createCompletedNotification(titleText)
                }
                Download.STATE_FAILED -> {
                    createFailedNotification(titleText)
                }
                else -> {
                    createProgressNotification(
                        titleText, 
                        percentComplete
                    )
                }
            }
        }
        
        // For multiple downloads, use the helper's default notification
        return downloadNotificationHelper.buildProgressNotification(
            this,
            R.drawable.ic_download,
            null,
            null,
            downloads,
            notMetRequirements
        )
    }
    
    private fun getDownloadPercentage(download: Download): Int {
        return if (download.contentLength > 0) {
            (download.bytesDownloaded * 100 / download.contentLength).toInt()
        } else {
            0
        }
    }
    
    private fun createProgressNotification(title: String, progress: Int): Notification {
        // Create a notification builder
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(title)
            .setOngoing(true)
            .setProgress(100, progress, false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            
        // Add cancel action if supported
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create a cancel intent
            val cancelIntent = Intent(this, ExoDownloadService::class.java).apply {
                action = ACTION_CANCEL_DOWNLOADS
            }
            
            val pendingIntent = PendingIntent.getService(
                this, 0, cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            builder.addAction(
                R.drawable.ic_download, 
                "Cancel", 
                pendingIntent
            )
        }
            
        return builder.build()
    }
    
    private fun createCompletedNotification(title: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle("$title - Completed")
            .setContentText("Download completed successfully")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    private fun createFailedNotification(title: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle("$title - Failed")
            .setContentText("Download failed. Please try again.")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            val action = intent.action
            if (action == ACTION_CANCEL_DOWNLOADS) {
                // Cancel all downloads
                downloadManager.removeAllDownloads()
                
                // Show a toast about cancellation
                val handler = Handler(Looper.getMainLooper())
                handler.post {
                    Toast.makeText(this, "Download canceled", Toast.LENGTH_SHORT).show()
                }
                
                // Stop the service
                stopSelf(startId)
                return START_NOT_STICKY
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    companion object {
        private const val CHANNEL_ID = "download_channel"
        private const val FOREGROUND_NOTIFICATION_ID = 1
        const val ACTION_CANCEL_DOWNLOADS = "action_cancel_downloads"
    }
} 