package com.example.flutter_exo_android

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider

/**
 * Database provider for ExoPlayer downloads
 */
@OptIn(UnstableApi::class)
object ExoCacheDatabase {
    private var databaseProvider: DatabaseProvider? = null

    @Synchronized
    fun getInstance(context: Context): DatabaseProvider {
        if (databaseProvider == null) {
            databaseProvider = StandaloneDatabaseProvider(context)
        }
        return databaseProvider!!
    }
} 