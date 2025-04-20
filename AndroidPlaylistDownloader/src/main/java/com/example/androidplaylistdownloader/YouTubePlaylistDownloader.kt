package com.example.androidplaylistdownloader

import android.content.Context
import android.os.Environment
import android.util.Log
import com.github.haarigerharald.androidyoutubeextractor.YtFile
import com.github.haarigerharald.androidyoutubeextractor.YouTubeExtractor
import com.github.haarigerharald.androidyoutubeextractor.VideoMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.CompressionMethod
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.concurrent.CountDownLatch

class YouTubePlaylistDownloader(private val context: Context) {

    suspend fun downloadPlaylist(
        playlistUrl: String,
        onProgress: (progress: Int, total: Int) -> Unit,
        onComplete: (zipFilePath: String) -> Unit,
        onError: (errorMsg: String) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            try {
                // Step 1: Extract playlist ID and get playlist info (title, video URLs)
                val playlistId = extractPlaylistId(playlistUrl)
                if (playlistId == null) {
                    onError("Invalid playlist URL")
                    return@withContext
                }

                val playlistTitle = getPlaylistTitle(playlistId) ?: "YouTubePlaylist"
                val videoUrls = getPlaylistVideoUrls(playlistId)
                if (videoUrls.isEmpty()) {
                    onError("No videos found in playlist")
                    return@withContext
                }

                val totalVideos = videoUrls.size
                val downloadFolder = File(
                    context.getExternalFilesDir(Environment.DIRECTORY_MUSIC),
                    playlistTitle
                )
                if (!downloadFolder.exists()) {
                    downloadFolder.mkdirs()
                }

                var progress = 0

                // Step 2: Download each video audio as MP3 with cover art
                for (videoUrl in videoUrls) {
                    val videoId = extractVideoId(videoUrl)
                    if (videoId == null) {
                        Log.w("Downloader", "Skipping invalid video URL: $videoUrl")
                        continue
                    }

                    val videoMeta = getVideoMeta(videoUrl)
                    if (videoMeta == null) {
                        Log.w("Downloader", "Skipping video with no metadata: $videoUrl")
                        continue
                    }

                    val audioStreamUrl = getAudioStreamUrl(videoUrl)
                    if (audioStreamUrl == null) {
                        Log.w("Downloader", "Skipping video with no audio stream: $videoUrl")
                        continue
                    }

                    val mp3File = File(downloadFolder, "${videoMeta.title}.mp3")
                    if (mp3File.exists()) {
                        Log.i("Downloader", "File already exists: ${mp3File.absolutePath}")
                        progress++
                        onProgress(progress, totalVideos)
                        continue
                    }

                    // Download audio stream to temp file
                    val tempFile = File(context.cacheDir, "${videoMeta.title}.temp")
                    downloadFile(audioStreamUrl, tempFile)

                    // Convert to MP3 and embed cover art using FFmpeg
                    val coverUrl = videoMeta.hqThumbnailUrl
                    convertToMp3WithCover(tempFile, mp3File, coverUrl)

                    tempFile.delete()

                    progress++
                    onProgress(progress, totalVideos)
                }

                // Step 3: Compress folder into ZIP
                val zipFilePath = File(
                    context.getExternalFilesDir(Environment.DIRECTORY_MUSIC),
                    "$playlistTitle.zip"
                ).absolutePath

                val zipFile = ZipFile(zipFilePath)
                val parameters = ZipParameters()
                parameters.compressionMethod = CompressionMethod.DEFLATE
                parameters.compressionLevel = CompressionLevel.NORMAL

                zipFile.addFolder(downloadFolder, parameters)

                onComplete(zipFilePath)

            } catch (e: Exception) {
                onError(e.message ?: "Unknown error")
            }
        }
    }

    private fun extractPlaylistId(url: String): String? {
        // Extract playlist ID from URL
        val regex = Regex("[&?]list=([a-zA-Z0-9_-]+)")
        val match = regex.find(url)
        return match?.groups?.get(1)?.value
    }

    private fun extractVideoId(url: String): String? {
        // Extract video ID from URL
        val regex = Regex("[&?]v=([a-zA-Z0-9_-]+)")
        val match = regex.find(url)
        return match?.groups?.get(1)?.value
    }

    private fun getPlaylistTitle(playlistId: String): String? {
        // TODO: Implement YouTube Data API call to get playlist title
        // For now, return a placeholder
        return "YouTubePlaylist"
    }

    private fun getPlaylistVideoUrls(playlistId: String): List<String> {
        // TODO: Implement YouTube Data API call to get video URLs in playlist
        // For now, return empty list
        return emptyList()
    }

    private fun getVideoMeta(videoUrl: String): VideoMeta? {
        // Use YouTubeExtractor to get video metadata
        var videoMeta: VideoMeta? = null
        val latch = CountDownLatch(1)
        val extractor = object : YouTubeExtractor(context) {
            override fun onExtractionComplete(
                ytFiles: SparseArray<YtFile>?,
                videoMetaResult: VideoMeta?
            ) {
                videoMeta = videoMetaResult
                latch.countDown()
            }
        }
        extractor.extract(videoUrl, true, true)
        latch.await()
        return videoMeta
    }

    private fun getAudioStreamUrl(videoUrl: String): String? {
        // Use YouTubeExtractor to get audio stream URL
        var audioUrl: String? = null
        val latch = CountDownLatch(1)
        val extractor = object : YouTubeExtractor(context) {
            override fun onExtractionComplete(
                ytFiles: SparseArray<YtFile>?,
                videoMetaResult: VideoMeta?
            ) {
                if (ytFiles != null) {
                    for (i in 0 until ytFiles.size()) {
                        val ytFile = ytFiles.valueAt(i)
                        if (ytFile.format.height == -1 && ytFile.format.audioBitrate > 0) {
                            audioUrl = ytFile.url
                            break
                        }
                    }
                }
                latch.countDown()
            }
        }
        extractor.extract(videoUrl, true, true)
        latch.await()
        return audioUrl
    }

    private fun downloadFile(url: String, outputFile: File) {
        URL(url).openStream().use { input ->
            FileOutputStream(outputFile).use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun convertToMp3WithCover(tempFile: File, mp3File: File, coverUrl: String?) {
        // TODO: Use FFmpeg to convert temp file to MP3 and embed cover art
        // This is a placeholder implementation
        tempFile.copyTo(mp3File, overwrite = true)
    }
}
