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
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.C
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.darkColorScheme
import androidx.compose.ui.graphics.Color as ComposeColor
import android.util.Log

class PlayerActivity : ComponentActivity() {
    private var onChannelStep: ((Int) -> Boolean)? = null
    private var setDrawerOpen: ((Boolean) -> Unit)? = null
    private var isDrawerOpen: Boolean = false
    private var setSettingsDrawerOpen: ((Boolean) -> Unit)? = null
    private var isSettingsDrawerOpen: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val videoUrl = intent.getStringExtra("VIDEO_URL") ?: ""
        val videoTitle = intent.getStringExtra("VIDEO_TITLE") ?: "Live"
        val playlistFileName = intent.getStringExtra("PLAYLIST_FILE_NAME") ?: Prefs.getPlaylistFileName(this)

        setContent {
            val colorScheme = darkColorScheme(
                primary = ComposeColor(0xFFBDBDBD),
                onPrimary = ComposeColor.Black,
                secondary = ComposeColor(0xFF757575),
                onSecondary = ComposeColor.Black,
                tertiary = ComposeColor(0xFF616161),
                onTertiary = ComposeColor.White,
                background = ComposeColor(0xFF121212),
                onBackground = ComposeColor.White,
                surface = ComposeColor(0xFF1E1E1E),
                onSurface = ComposeColor.White
            )

            MaterialTheme(colorScheme = colorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = androidx.compose.ui.graphics.RectangleShape
                ) {
                    VideoPlayerScreen(
                        url = videoUrl,
                        title = videoTitle,
                        playlistFileName = playlistFileName,
                        setOnChannelStep = { onChannelStep = it },
                        setDrawerOpenController = { setDrawerOpen = it },
                        onDrawerOpenChanged = { isDrawerOpen = it },
                        setSettingsDrawerOpenController = { setSettingsDrawerOpen = it },
                        onSettingsDrawerOpenChanged = { isSettingsDrawerOpen = it }
                    ) {
                        finish()
                    }
                }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (!isDrawerOpen && !isSettingsDrawerOpen) {
                        setDrawerOpen?.invoke(true)
                        return true
                    }
                }

                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (!isDrawerOpen && !isSettingsDrawerOpen) {
                        setSettingsDrawerOpen?.invoke(true)
                        return true
                    }
                }

                KeyEvent.KEYCODE_MENU -> {
                    if (!isDrawerOpen && !isSettingsDrawerOpen) {
                        setSettingsDrawerOpen?.invoke(true)
                        return true
                    }
                }

                KeyEvent.KEYCODE_BACK -> {
                    if (isDrawerOpen) {
                        setDrawerOpen?.invoke(false)
                        return true
                    }
                    if (isSettingsDrawerOpen) {
                        setSettingsDrawerOpen?.invoke(false)
                        return true
                    }
                }

                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (!isDrawerOpen && !isSettingsDrawerOpen && onChannelStep?.invoke(-1) == true) return true
                }

                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (!isDrawerOpen && !isSettingsDrawerOpen && onChannelStep?.invoke(1) == true) return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun VideoPlayerScreen(
    url: String,
    title: String,
    playlistFileName: String?,
    setOnChannelStep: (((Int) -> Boolean)?) -> Unit,
    setDrawerOpenController: (((Boolean) -> Unit)?) -> Unit,
    onDrawerOpenChanged: (Boolean) -> Unit,
    setSettingsDrawerOpenController: (((Boolean) -> Unit)?) -> Unit,
    onSettingsDrawerOpenChanged: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var drawerOpen by remember { mutableStateOf(false) }
    var settingsDrawerOpen by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val player = remember {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            .setAllowCrossProtocolRedirects(true)

        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                15_000,
                120_000,
                1_000,
                2_000
            )
            .setBackBuffer(30_000, true)
            .build()

        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setRenderersFactory(renderersFactory)
            .build()
            .apply {
                playWhenReady = true
                setWakeMode(C.WAKE_MODE_NETWORK)
            }
    }

    var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var selectedGroup by remember { mutableStateOf("全部") }
    var epgData by remember { mutableStateOf<EpgData?>(null) }
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var nowProgramTitleByUrl by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    val epgLoadTag = remember { "EPG" }

    DisposableEffect(Unit) {
        setDrawerOpenController { drawerOpen = it }
        setSettingsDrawerOpenController { settingsDrawerOpen = it }
        onDispose {
            setDrawerOpenController(null)
            setSettingsDrawerOpenController(null)
        }
    }

    LaunchedEffect(drawerOpen) {
        onDrawerOpenChanged(drawerOpen)
    }

    LaunchedEffect(settingsDrawerOpen) {
        onSettingsDrawerOpenChanged(settingsDrawerOpen)
    }

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

        val (parsed, bestIndex) = withContext(Dispatchers.Default) {
            val parsedChannels = M3uParser.parse(content)
            val idx = if (parsedChannels.isEmpty()) 0 else (findBestChannelIndex(parsedChannels, url, title) ?: 0)
            parsedChannels to idx
        }

        if (parsed.isEmpty()) return@LaunchedEffect
        channels = parsed
        currentIndex = bestIndex
    }

    // LaunchedEffect(channels, currentIndex) {
    //     val ch = channels.getOrNull(currentIndex)
    //     if (ch != null) {
    //         selectedGroup = ch.group?.takeIf { it.isNotBlank() } ?: "未分组"
    //     }
    // }

    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(30_000)
        }
    }

    LaunchedEffect(channels) {
        val epgSource = Prefs.getEpgSource(context)
        if (channels.isEmpty() || epgSource.isBlank()) {
            epgData = null
            return@LaunchedEffect
        }
        val loaded = EpgRepository.load(context, epgSource)
        epgData = loaded
        if (loaded != null) {
            val matched = withContext(Dispatchers.Default) {
                channels.count { loaded.resolveChannelId(it) != null }
            }
            Log.i(epgLoadTag, "loaded. programs=${loaded.programsByChannelId.size}, matched=$matched/${channels.size}")
        } else {
            Log.w(epgLoadTag, "load failed or parsed empty")
        }
    }

    val groups = remember(channels) {
        val set = LinkedHashSet<String>()
        set.add("全部")
        for (c in channels) {
            set.add(c.group?.takeIf { it.isNotBlank() } ?: "未分组")
        }
        set.toList()
    }

    val filteredChannels = remember(channels, selectedGroup) {
        if (selectedGroup == "全部") channels
        else channels.filter { (it.group?.takeIf { g -> g.isNotBlank() } ?: "未分组") == selectedGroup }
    }

    LaunchedEffect(epgData, nowMillis, filteredChannels) {
        val data = epgData
        if (data == null || filteredChannels.isEmpty()) {
            nowProgramTitleByUrl = emptyMap()
            return@LaunchedEffect
        }

        val map = withContext(Dispatchers.Default) {
            buildMap {
                for (ch in filteredChannels) {
                    val t = data.nowProgramTitle(ch, nowMillis)
                    if (!t.isNullOrBlank()) put(ch.url, t)
                }
            }
        }
        nowProgramTitleByUrl = map
    }

    fun playChannel(channel: Channel) {
        val nextIndex = channels.indexOfFirst { it.url == channel.url }
        if (nextIndex < 0) return
        player.setMediaItem(MediaItem.fromUri(channel.url))
        player.prepare()
        player.play()
        currentIndex = nextIndex
        Prefs.setLastChannel(context, channel.url, channel.title)
    }

    DisposableEffect(channels, currentIndex) {
        setOnChannelStep { direction ->
            if (channels.size < 2) return@setOnChannelStep false
            if (drawerOpen || settingsDrawerOpen) return@setOnChannelStep false
            val size = channels.size
            val nextIndex = (currentIndex + direction).let { raw ->
                ((raw % size) + size) % size
            }
            val channel = channels[nextIndex]

            playChannel(channel)
            true
        }

        onDispose {
            setOnChannelStep(null)
        }
    }

    DisposableEffect(Unit) {
        var retryCount = 0
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                if (retryCount >= 3) return
                retryCount += 1

                val current = player.currentMediaItem ?: return
                scope.launch {
                    delay(800)
                    player.stop()
                    player.clearMediaItems()
                    player.setMediaItem(current)
                    player.prepare()
                    player.play()
                }
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

        PlayerDrawer(
            visible = drawerOpen,
            groups = groups,
            selectedGroup = selectedGroup,
            channels = filteredChannels,
            selectedChannelUrl = channels.getOrNull(currentIndex)?.url,
            nowProgramTitleByChannelUrl = nowProgramTitleByUrl,
            epgData = epgData,
            nowMillis = nowMillis,
            onSelectGroup = { selectedGroup = it },
            onSelectChannel = { playChannel(it) },
            onClose = { drawerOpen = false }
        )

        SettingsDrawer(
            visible = settingsDrawerOpen,
            onSourceConfigClick = {
                val intent = Intent(context, MainActivity::class.java)
                context.startActivity(intent)
                // (context as? android.app.Activity)?.finish() // Keep PlayerActivity in back stack
            },
            onEpgSettingsClick = {
                // Placeholder
            },
            onClose = { settingsDrawerOpen = false }
        )
    }
}
