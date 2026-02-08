package com.cjlhll.iptv

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class PlayerActivity : ComponentActivity() {
    private var onChannelStep: ((Int) -> Boolean)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val videoUrl = intent.getStringExtra("VIDEO_URL") ?: ""
        val videoTitle = intent.getStringExtra("VIDEO_TITLE") ?: "Live"
        val playlistFileName = intent.getStringExtra("PLAYLIST_FILE_NAME") ?: Prefs.getPlaylistFileName(this)

        setContent {
            VideoPlayerScreen(
                url = videoUrl,
                title = videoTitle,
                playlistFileName = playlistFileName,
                setOnChannelStep = { onChannelStep = it }
            ) {
                finish()
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (onChannelStep?.invoke(-1) == true) return true
                }

                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (onChannelStep?.invoke(1) == true) return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}

@Composable
fun VideoPlayerScreen(
    url: String,
    title: String,
    playlistFileName: String?,
    setOnChannelStep: (((Int) -> Boolean)?) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val player = remember {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            .setAllowCrossProtocolRedirects(true)

        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                playWhenReady = true
            }
    }

    var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var currentIndex by remember { mutableIntStateOf(0) }

    DisposableEffect(url) {
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.play()
        onDispose { }
    }

    LaunchedEffect(playlistFileName, url, title) {
        val liveSource = Prefs.getLiveSource(context)
        val resolvedFileName = when {
            !playlistFileName.isNullOrBlank() -> playlistFileName
            liveSource.isNotBlank() -> PlaylistCache.fileNameForSource(liveSource)
            else -> null
        }

        var content: String? = null

        if (!resolvedFileName.isNullOrBlank()) {
            content = withContext(Dispatchers.IO) {
                PlaylistCache.read(context, resolvedFileName)
            }
        }

        if (content == null && liveSource.isNotBlank()) {
            content = withContext(Dispatchers.IO) {
                val client = OkHttpClient()
                val request = Request.Builder().url(liveSource).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    response.body?.string()
                }
            }

            if (content != null) {
                val fileNameToSave = resolvedFileName ?: PlaylistCache.fileNameForSource(liveSource)
                withContext(Dispatchers.IO) {
                    PlaylistCache.write(context, fileNameToSave, content)
                }
                Prefs.setPlaylistFileName(context, fileNameToSave)
            }
        }

        if (content == null) return@LaunchedEffect

        val parsed = M3uParser.parse(content)
        if (parsed.isEmpty()) return@LaunchedEffect
        channels = parsed
        currentIndex = findBestChannelIndex(parsed, url, title) ?: 0
    }

    DisposableEffect(channels, currentIndex) {
        setOnChannelStep { direction ->
            if (channels.size < 2) return@setOnChannelStep false
            val size = channels.size
            val nextIndex = (currentIndex + direction).let { raw ->
                ((raw % size) + size) % size
            }
            val channel = channels[nextIndex]

            player.setMediaItem(MediaItem.fromUri(channel.url))
            player.prepare()
            player.play()

            currentIndex = nextIndex
            Prefs.setLastChannel(context, channel.url, channel.title)
            true
        }

        onDispose {
            setOnChannelStep(null)
        }
    }

    DisposableEffect(Unit) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(context, "播放失败：${error.errorCodeName}", Toast.LENGTH_SHORT).show()
            }
        }

        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                PlayerView(it).apply {
                    this.player = player
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    setKeepContentOnPlayerReset(true)
                    setShutterBackgroundColor(Color.TRANSPARENT)
                    keepScreenOn = true
                }
            },
            update = {
                it.player = player
            }
        )

        // Settings Button (Top-Right)
        IconButton(
            onClick = {
                val intent = Intent(context, MainActivity::class.java)
                context.startActivity(intent)
                (context as? android.app.Activity)?.finish()
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.size(32.dp)
            )
        }
    }
}
