package com.example.flutter_exo_android

import android.app.Application
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import io.flutter.app.FlutterApplication

@OptIn(UnstableApi::class)
class VideoPlayerApplication : FlutterApplication() {

    companion object {
        private const val TAG = "VideoPlayerApp"
    }

    override fun onCreate() {
        super.onCreate()
        
        try {
            // Initialize the download cache early to ensure singleton instance
            val cache = ExoCacheDatabase.getDownloadCache(this)
            Log.d(TAG, "Download cache initialized with ${cache.keys.size} keys")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing download cache: ${e.message}")
        }
    }

    override fun onTerminate() {
        try {
            // Release the download cache when the application is terminated
            ExoCacheDatabase.release()
            Log.d(TAG, "Download cache released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing download cache: ${e.message}")
        }
        
        super.onTerminate()
    }
} 