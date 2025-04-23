package com.example.flutter_exo_android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
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
import org.checkerframework.checker.nullness.qual.MonotonicNonNull
import java.io.File
import java.util.concurrent.Executor

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

    @OptIn(UnstableApi::class)
    private var databaseProvider: @MonotonicNonNull DatabaseProvider? = null

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
        
        // Create download cache
        val downloadDirectory = File(getExternalFilesDir(null), "downloads")
        if (!downloadDirectory.exists()) {
            downloadDirectory.mkdirs()
        }
        
        // Create the download cache
        val downloadCache: Cache = getDownloadCache()
        
        // Create data source factory
        val dataSourceFactory = DefaultDataSource.Factory(this)
        
        // Create the download manager
        val newDownloadManager = DownloadManager(
            this,
            getDatabaseProvider(this),
            getDownloadCache(),
            dataSourceFactory,
            Executor { it.run() }
        )
        
        // Set maximum parallel downloads
        newDownloadManager.maxParallelDownloads = 3
        
        // Enable download resumption
        newDownloadManager.resumeDownloads()
        
        // Return the download manager
        return newDownloadManager
    }

    private fun getDownloadCache(): Cache {
        // Create download directory
        val downloadDirectory = File(getExternalFilesDir(null), "downloads")
        if (!downloadDirectory.exists()) {
            downloadDirectory.mkdirs()
        }
        
        // Create download cache
        return SimpleCache(
            downloadDirectory,
            NoOpCacheEvictor(),
            ExoCacheDatabase.getInstance(this)
        )
    }

    override fun getScheduler(): Scheduler? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            PlatformScheduler(this, JOB_ID)
        } else {
            null
        }
    }

    override fun getForegroundNotification(downloads: List<Download>, notMetRequirements: Int): Notification {
        // Create notification for download progress
        return downloadNotificationHelper.buildProgressNotification(
            this,
            R.drawable.ic_download,
            null,
            null,
            downloads,
            notMetRequirements
        )
    }

    @OptIn(UnstableApi::class)
    @Synchronized
    private fun getDatabaseProvider(context: Context): DatabaseProvider {
        if (databaseProvider == null) {
            databaseProvider =
                StandaloneDatabaseProvider(context)
        }
        return databaseProvider!!
    }

    companion object {
        private const val CHANNEL_ID = "download_channel"
        private const val FOREGROUND_NOTIFICATION_ID = 1
    }
} 