package com.example.flutter_exo_android

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * Database provider for ExoPlayer downloads
 */
@OptIn(UnstableApi::class)
object ExoCacheDatabase {
    private var databaseProvider: DatabaseProvider? = null
    private var downloadCache: SimpleCache? = null
    
    @Synchronized
    fun getInstance(context: Context): DatabaseProvider {
        if (databaseProvider == null) {
            databaseProvider = StandaloneDatabaseProvider(context)
        }
        return databaseProvider!!
    }
    
    @Synchronized
    fun getDownloadCache(context: Context): SimpleCache {
        if (downloadCache == null) {
            val downloadDirectory = File(context.getExternalFilesDir(null), "downloads")
            if (!downloadDirectory.exists()) {
                downloadDirectory.mkdirs()
            }
            
            downloadCache = SimpleCache(
                downloadDirectory,
                NoOpCacheEvictor(),
                getInstance(context)
            )
        }
        return downloadCache!!
    }
    
    // Call this method in onDestroy() of your application class if needed
    fun release() {
        try {
            downloadCache?.release()
            downloadCache = null
        } catch (e: Exception) {
            // Ignore
        }
    }
} 