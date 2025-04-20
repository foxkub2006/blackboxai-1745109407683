package com.example.androidplaylistdownloader

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private lateinit var playlistLinkEditText: EditText
    private lateinit var downloadButton: Button
    private lateinit var downloadProgressBar: ProgressBar
    private lateinit var statusTextView: TextView

    private val downloader = YouTubePlaylistDownloader(this)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startDownload()
        } else {
            statusTextView.text = "Permission denied. Cannot download files."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        playlistLinkEditText = findViewById(R.id.playlistLinkEditText)
        downloadButton = findViewById(R.id.downloadButton)
        downloadProgressBar = findViewById(R.id.downloadProgressBar)
        statusTextView = findViewById(R.id.statusTextView)

        downloadButton.setOnClickListener {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                startDownload()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    private fun startDownload() {
        val playlistLink = playlistLinkEditText.text.toString().trim()
        if (playlistLink.isEmpty()) {
            statusTextView.text = "Please enter a playlist link."
            return
        }

        downloadProgressBar.progress = 0
        downloadProgressBar.visibility = ProgressBar.VISIBLE
        statusTextView.text = "Starting download..."

        CoroutineScope(Dispatchers.IO).launch {
            try {
                downloader.downloadPlaylist(
                    playlistLink,
                    onProgress = { progress, total ->
                        withContext(Dispatchers.Main) {
                            downloadProgressBar.max = total
                            downloadProgressBar.progress = progress
                            statusTextView.text = "Downloading $progress of $total songs..."
                        }
                    },
                    onComplete = { zipFilePath ->
                        withContext(Dispatchers.Main) {
                            downloadProgressBar.visibility = ProgressBar.GONE
                            statusTextView.text = "Download complete: $zipFilePath"
                        }
                    },
                    onError = { errorMsg ->
                        withContext(Dispatchers.Main) {
                            downloadProgressBar.visibility = ProgressBar.GONE
                            statusTextView.text = "Error: $errorMsg"
                        }
                    }
                )
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    downloadProgressBar.visibility = ProgressBar.GONE
                    statusTextView.text = "Unexpected error: ${e.message}"
                }
            }
        }
    }
}
