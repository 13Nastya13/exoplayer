package com.example.flutter_exo_android

import android.app.Activity
import android.content.Intent
import androidx.annotation.NonNull
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity: FlutterActivity() {
    private val CHANNEL = "native_player"
    private val PLAYER_REQUEST_CODE = 1001
    private var pendingResult: MethodChannel.Result? = null

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            if (call.method == "open_player") {
                // Store the result to respond later when player returns
                pendingResult = result
                
                // Optional parameters from Flutter
                val videoUrl = call.argument<String>("video_url")
                val videoTitle = call.argument<String>("video_title")
                
                // Launch the PlayerActivity for result
                val intent = Intent(this, PlayerActivity::class.java).apply {
                    videoUrl?.let { putExtra("VIDEO_URL", it) }
                    videoTitle?.let { putExtra("VIDEO_TITLE", it) }
                }
                startActivityForResult(intent, PLAYER_REQUEST_CODE)
                
                // Don't return result now, we'll do it when player returns
            } else {
                result.notImplemented()
            }
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == PLAYER_REQUEST_CODE) {
            // Player activity has returned
            val result = when {
                resultCode == Activity.RESULT_OK && data != null -> {
                    val playbackPosition = data.getLongExtra("PLAYBACK_POSITION", 0)
                    val playbackCompleted = data.getBooleanExtra("PLAYBACK_COMPLETED", false)
                    
                    // Return result to Flutter with playback info
                    hashMapOf(
                        "success" to true,
                        "position" to playbackPosition,
                        "completed" to playbackCompleted
                    )
                }
                else -> hashMapOf("success" to false)
            }
            
            // Return result to Flutter
            pendingResult?.success(result)
            pendingResult = null
        }
    }
} 