package com.cjlhll.iptv

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

class PlayerActivity : ComponentActivity() {
    private var onChannelStep: ((Int) -> Boolean)? = null
    private var setDrawerOpen: ((Boolean) -> Unit)? = null
    private var isDrawerOpen: Boolean = false
    private var setSettingsDrawerOpen: ((Boolean) -> Unit)? = null
    private var isSettingsDrawerOpen: Boolean = false
    private var setInfoBannerOpen: ((Boolean) -> Unit)? = null
    private var isInfoBannerOpen: Boolean = false
    private var isCatchupPlayback: Boolean = false
    private var onCatchupSeek: ((Long) -> Boolean)? = null
    private var onReturnToLive: (() -> Boolean)? = null
    private var lastBackPressTime: Long = 0
    private var lastBackPressTimeCatchup: Long = 0

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
                        onSettingsDrawerOpenChanged = { isSettingsDrawerOpen = it },
                        setInfoBannerOpenController = { setInfoBannerOpen = it },
                        onInfoBannerOpenChanged = { isInfoBannerOpen = it },
                        setOnCatchupSeek = { onCatchupSeek = it },
                        setOnReturnToLive = { onReturnToLive = it },
                        onCatchupPlaybackChanged = { isCatchupPlayback = it }
                    ) {
                        finish()
                    }
                }
            }
        }
    }

    private fun calculateSeekStep(repeatCount: Int): Long {
        return when {
            repeatCount == 0 -> 10_000L
            repeatCount < 15 -> 30_000L
            repeatCount < 40 -> 60_000L
            else -> 300_000L
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    if (!isDrawerOpen && !isSettingsDrawerOpen) {
                        setInfoBannerOpen?.invoke(true)
                        return true
                    }
                }

                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (!isDrawerOpen && !isSettingsDrawerOpen) {
                        if (isCatchupPlayback) {
                            val step = calculateSeekStep(event.repeatCount)
                            if (onCatchupSeek?.invoke(-step) == true) return true
                        } else {
                            setDrawerOpen?.invoke(true)
                            return true
                        }
                    }
                }

                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (!isDrawerOpen && !isSettingsDrawerOpen) {
                        if (isCatchupPlayback) {
                            val step = calculateSeekStep(event.repeatCount)
                            if (onCatchupSeek?.invoke(step) == true) return true
                        } else {
                            setSettingsDrawerOpen?.invoke(true)
                            return true
                        }
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
                    if (isInfoBannerOpen) {
                        setInfoBannerOpen?.invoke(false)
                        return true
                    }

                    if (isCatchupPlayback) {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastBackPressTimeCatchup > 2000) {
                            lastBackPressTimeCatchup = currentTime
                            android.widget.Toast.makeText(this, "再按一次返回直播", android.widget.Toast.LENGTH_SHORT).show()
                            return true
                        }
                        if (onReturnToLive?.invoke() == true) {
                            lastBackPressTimeCatchup = 0
                            return true
                        }
                        return true
                    }

                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastBackPressTime > 2000) {
                        lastBackPressTime = currentTime
                        android.widget.Toast.makeText(this, "再按一次退出app", android.widget.Toast.LENGTH_SHORT).show()
                        return true
                    }
                }

                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (!isDrawerOpen && !isSettingsDrawerOpen) {
                        if (isCatchupPlayback) {
                            setInfoBannerOpen?.invoke(true)
                            return true
                        }
                        if (onChannelStep?.invoke(-1) == true) return true
                    }
                }

                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (!isDrawerOpen && !isSettingsDrawerOpen) {
                        if (isCatchupPlayback) {
                            setInfoBannerOpen?.invoke(true)
                            return true
                        }
                        if (onChannelStep?.invoke(1) == true) return true
                    }
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
    setInfoBannerOpenController: (((Boolean) -> Unit)?) -> Unit,
    onInfoBannerOpenChanged: (Boolean) -> Unit,
    setOnCatchupSeek: (((Long) -> Boolean)?) -> Unit,
    setOnReturnToLive: ((() -> Boolean)?) -> Unit,
    onCatchupPlaybackChanged: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    
    var drawerOpen by remember { mutableStateOf(false) }
    var settingsDrawerOpen by remember { mutableStateOf(false) }
    var infoBannerOpen by remember { mutableStateOf(false) }
    var videoFormat by remember { mutableStateOf<androidx.media3.common.Format?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var selectedGroup by remember { mutableStateOf("全部") }
    var epgData by remember { mutableStateOf<EpgData?>(null) }
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var nowProgramByUrl by remember { mutableStateOf<Map<String, NowProgramUi>>(emptyMap()) }
    var catchupRequest by remember { mutableStateOf<CatchupPlayRequest?>(null) }
    var catchupPositionMs by remember { mutableStateOf(0L) }
    var isSeekOverlayVisible by remember { mutableStateOf(false) }
    var catchupSeekPingAt by remember { mutableStateOf(0L) }

    val drawerLogoWidthPx = remember(density) { with(density) { 64.dp.roundToPx() } }
    val drawerLogoHeightPx = remember(density) { with(density) { 40.dp.roundToPx() } }
    val bannerLogoWidthPx = remember(density) { with(density) { 80.dp.roundToPx() } }
    val bannerLogoHeightPx = remember(density) { with(density) { 50.dp.roundToPx() } }

    val epgLoadTag = remember { "EPG" }
    val shouldRefreshEpg = remember { mutableStateOf(false) }

    var heavyWorkEnabled by remember { mutableStateOf(false) }
    var firstDrawerOpenSeen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(1500)
        heavyWorkEnabled = true
    }

    fun initializePlayer() {
        if (player != null) return
        
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

        val newPlayer = ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setRenderersFactory(renderersFactory)
            .build()
            .apply {
                playWhenReady = true
                setWakeMode(C.WAKE_MODE_NETWORK)
            }
        
        val req = catchupRequest
        if (req != null) {
             newPlayer.setMediaItem(MediaItem.fromUri(req.catchupUrl))
        } else {
             val currentChannelUrl = channels.getOrNull(currentIndex)?.url ?: url
             newPlayer.setMediaItem(MediaItem.fromUri(currentChannelUrl))
        }
        
        newPlayer.prepare()
        newPlayer.play()
        player = newPlayer
    }

    fun releasePlayer() {
        player?.release()
        player = null
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                initializePlayer()
            } else if (event == Lifecycle.Event.ON_STOP) {
                releasePlayer()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            releasePlayer()
        }
    }

    DisposableEffect(player) {
        val p = player
        if (p != null) {
            var retryCount = 0
            val activity = context as? Activity

            fun setScreenOn(keepOn: Boolean) {
                val window = activity?.window ?: return
                if (keepOn) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            val listener = object : androidx.media3.common.Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    setScreenOn(isPlaying)
                }

                override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                    videoFormat = p.videoFormat
                }

                override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                    videoFormat = p.videoFormat
                }

                override fun onPlayerError(error: PlaybackException) {
                    if (retryCount >= 3) return
                    retryCount += 1

                    val current = p.currentMediaItem ?: return
                    scope.launch {
                        delay(800)
                        p.setMediaItem(current)
                        p.prepare()
                        p.play()
                    }
                }
            }

            p.addListener(listener)
            setScreenOn(p.isPlaying)
            onDispose {
                setScreenOn(false)
                p.removeListener(listener)
            }
        } else {
            onDispose { }
        }
    }

    DisposableEffect(Unit) {
        setDrawerOpenController { drawerOpen = it }
        setSettingsDrawerOpenController { settingsDrawerOpen = it }
        setInfoBannerOpenController { infoBannerOpen = it }
        onDispose {
            setDrawerOpenController(null)
            setSettingsDrawerOpenController(null)
            setInfoBannerOpenController(null)
        }
    }

    LaunchedEffect(drawerOpen) {
        onDrawerOpenChanged(drawerOpen)
    }

    LaunchedEffect(settingsDrawerOpen) {
        onSettingsDrawerOpenChanged(settingsDrawerOpen)
    }

    LaunchedEffect(infoBannerOpen, currentIndex) {
        onInfoBannerOpenChanged(infoBannerOpen)
        if (infoBannerOpen) {
            delay(5000)
            infoBannerOpen = false
        }
    }

    LaunchedEffect(catchupRequest) {
        onCatchupPlaybackChanged(catchupRequest != null)
    }

    LaunchedEffect(catchupSeekPingAt) {
        if (catchupSeekPingAt > 0) {
            isSeekOverlayVisible = true
            delay(1500)
            isSeekOverlayVisible = false
        }
    }

    LaunchedEffect(catchupRequest, player) {
        val p = player
        if (catchupRequest == null || p == null) {
            catchupPositionMs = 0L
            return@LaunchedEffect
        }
        while (true) {
            val req = catchupRequest ?: break
            val total = (req.endMillis - req.startMillis).coerceAtLeast(1L)
            catchupPositionMs = p.currentPosition.coerceIn(0L, total)
            delay(500)
        }
    }

    LaunchedEffect(playlistFileName, url, title) {
        val liveSource = Prefs.getLiveSource(context)
        val isNetworkM3u = playlistFileName.isNullOrBlank() && liveSource.isNotBlank()

        val cacheFileName = when {
            !playlistFileName.isNullOrBlank() -> playlistFileName
            liveSource.isNotBlank() -> PlaylistCache.fileNameForSource(liveSource)
            else -> null
        }

        if (!cacheFileName.isNullOrBlank()) {
            val content = withContext(Dispatchers.IO) { PlaylistCache.read(context, cacheFileName) }
            if (content != null) {
                val (parsed, bestIndex) = withContext(Dispatchers.Default) {
                    val parsedChannels = M3uParser.parse(content)
                    val idx = if (parsedChannels.isEmpty()) 0 else (findBestChannelIndex(parsedChannels, url, title) ?: 0)
                    parsedChannels to idx
                }
                if (parsed.isNotEmpty()) {
                    channels = parsed
                    currentIndex = bestIndex

                    val urls = buildPrefetchLogoUrls(parsed, bestIndex)
                    scope.launch {
                        LogoLoader.prefetch(context, urls, drawerLogoWidthPx, drawerLogoHeightPx)
                    }
                    scope.launch {
                        LogoLoader.prefetch(context, urls, bannerLogoWidthPx, bannerLogoHeightPx)
                    }
                }
            }
        }

        if (isNetworkM3u) {
            var lastM3uAttemptTime = 0L
            while (true) {
                val now = System.currentTimeMillis()
                if (now - lastM3uAttemptTime > 30 * 60 * 1000L) {
                    val content = withContext(Dispatchers.IO) {
                        val client = OkHttpClient()
                        val request = Request.Builder().url(liveSource).build()
                        try {
                            client.newCall(request).execute().use { response ->
                                if (response.isSuccessful) response.body?.string() else null
                            }
                        } catch (e: Exception) { null }
                    }

                    if (content != null) {
                        val fileNameToSave = PlaylistCache.fileNameForSource(liveSource)
                        withContext(Dispatchers.IO) {
                            PlaylistCache.write(context, fileNameToSave, content)
                        }
                        Prefs.setPlaylistFileName(context, fileNameToSave)

                        val parsed = withContext(Dispatchers.Default) { M3uParser.parse(content) }

                        if (parsed.isNotEmpty()) {
                            val currentUrl = channels.getOrNull(currentIndex)?.url
                            val newIndex = if (currentUrl != null) {
                                parsed.indexOfFirst { it.url == currentUrl }.takeIf { it >= 0 }
                            } else null

                            channels = parsed
                            if (newIndex != null) {
                                currentIndex = newIndex
                            } else {
                                currentIndex = currentIndex.coerceIn(0, parsed.lastIndex)
                            }

                            val idx = currentIndex
                            val urls = buildPrefetchLogoUrls(parsed, idx)
                            scope.launch {
                                while (!heavyWorkEnabled) delay(200)
                                LogoLoader.prefetch(context, urls, drawerLogoWidthPx, drawerLogoHeightPx)
                                delay(800)
                                LogoLoader.prefetch(context, urls, bannerLogoWidthPx, bannerLogoHeightPx)
                            }
                        }
                    }
                    lastM3uAttemptTime = now
                }
                delay(10_000L)
            }
        }
    }

    LaunchedEffect(Unit) {
        val epgSource = Prefs.getEpgSource(context)
        if (epgSource.isNotBlank()) {
            val cachedEpg = EpgRepository.load(context, epgSource, forceRefresh = false)
            if (cachedEpg != null) {
                epgData = cachedEpg
                Log.i(epgLoadTag, "cache loaded. programs=${cachedEpg.programsByChannelId.size}")
            }

            var lastAttemptTime = 0L
            while (true) {
                val now = System.currentTimeMillis()
                
                if (!shouldRefreshEpg.value && (now - lastAttemptTime < 5 * 60 * 1000L)) {
                    delay(10_000L)
                    continue
                }

                val lastEpgUpdate = Prefs.getLastEpgUpdateTime(context)
                val zone = ZoneId.systemDefault()
                val lastDate = Instant.ofEpochMilli(lastEpgUpdate).atZone(zone).toLocalDate()
                val nowDate = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()

                val isDifferentDay = !lastDate.isEqual(nowDate)
                val isOver6Hours = (now - lastEpgUpdate) > 6 * 3600 * 1000L

                if (shouldRefreshEpg.value || isDifferentDay || isOver6Hours) {
                    lastAttemptTime = now
                    val newEpg = EpgRepository.load(context, epgSource, forceRefresh = true)
                    if (newEpg != null) {
                        epgData = newEpg
                        Prefs.setLastEpgUpdateTime(context, System.currentTimeMillis())
                        Log.i(epgLoadTag, "network refreshed. programs=${newEpg.programsByChannelId.size}")
                        if (shouldRefreshEpg.value) {
                            withContext(Dispatchers.Main) {
                                android.widget.Toast.makeText(context, "EPG更新成功", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        Log.w(epgLoadTag, "network refresh failed")
                    }
                    shouldRefreshEpg.value = false
                }
                delay(600_000L)
            }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(30_000)
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
            nowProgramByUrl = emptyMap()
            return@LaunchedEffect
        }

        val map = withContext(Dispatchers.Default) {
            buildMap {
                for (ch in filteredChannels) {
                    val t = data.nowProgramTitle(ch, nowMillis)
                    if (!t.isNullOrBlank()) {
                        val current = data.nowProgram(ch, nowMillis)
                        val progress = current?.let {
                            val duration = (it.endMillis - it.startMillis).toFloat()
                            if (duration <= 0f) null
                            else ((nowMillis - it.startMillis).toFloat() / duration).coerceIn(0f, 1f)
                        }
                        put(ch.url, NowProgramUi(title = t, progress = progress))
                    }
                }
            }
        }
        nowProgramByUrl = map
    }

    fun playChannel(channel: Channel) {
        val nextIndex = channels.indexOfFirst { it.url == channel.url }
        if (nextIndex < 0) return
        catchupRequest = null
        
        player?.let { p ->
            p.setMediaItem(MediaItem.fromUri(channel.url))
            p.prepare()
            p.play()
        }
        
        currentIndex = nextIndex
        Prefs.setLastChannel(context, channel.url, channel.title)
    }

    DisposableEffect(player, channels, currentIndex, catchupRequest) {
        setOnCatchupSeek { deltaMs ->
            val p = player ?: return@setOnCatchupSeek false
            val req = catchupRequest ?: return@setOnCatchupSeek false
            if (!p.isCurrentMediaItemSeekable) return@setOnCatchupSeek false
            val total = (req.endMillis - req.startMillis).coerceAtLeast(1L)
            val target = (p.currentPosition + deltaMs).coerceIn(0L, total)
            p.seekTo(target)
            catchupSeekPingAt = System.currentTimeMillis()
            catchupPositionMs = target
            true
        }
        setOnReturnToLive {
            val req = catchupRequest ?: return@setOnReturnToLive false
            val channel = channels.firstOrNull { it.url == req.liveUrl }
            if (channel != null) {
                playChannel(channel)
            } else {
                catchupRequest = null
                player?.let { p ->
                    p.setMediaItem(MediaItem.fromUri(req.liveUrl))
                    p.prepare()
                    p.play()
                }
            }
            true
        }
        onDispose {
            setOnCatchupSeek(null)
            setOnReturnToLive(null)
        }
    }

    DisposableEffect(channels, currentIndex, player) {
        setOnChannelStep { direction ->
            if (channels.size < 2) return@setOnChannelStep false
            if (drawerOpen || settingsDrawerOpen) return@setOnChannelStep false
            val size = channels.size
            val nextIndex = (currentIndex + direction).let { raw ->
                ((raw % size) + size) % size
            }
            val channel = channels[nextIndex]

            playChannel(channel)
            infoBannerOpen = true
            true
        }

        onDispose {
            setOnChannelStep(null)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (player != null) {
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
                        isFocusable = false
                        isFocusableInTouchMode = false
                        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    }
                },
                update = {
                    it.player = player
                }
            )
        }

        PlayerDrawer(
            visible = drawerOpen,
            groups = groups,
            selectedGroup = selectedGroup,
            channels = filteredChannels,
            initialChannelIndex = filteredChannels.indexOfFirst { it.url == channels.getOrNull(currentIndex)?.url }.let { if (it >= 0) it else 0 },
            selectedChannelUrl = channels.getOrNull(currentIndex)?.url,
            nowProgramByChannelUrl = nowProgramByUrl,
            epgData = epgData,
            nowMillis = nowMillis,
            onSelectGroup = { selectedGroup = it },
            onSelectChannel = { playChannel(it) },
            onPlayProgram = { req ->
                Log.d("PlayerActivity", "onPlayProgram called with url: ${req.catchupUrl}")
                catchupRequest = req
                player?.let { p ->
                    p.setMediaItem(MediaItem.fromUri(req.catchupUrl))
                    p.prepare()
                    p.play()
                }
                drawerOpen = false
            },
            onClose = { drawerOpen = false }
        )

        SettingsDrawer(
            visible = settingsDrawerOpen,
            onSourceConfigClick = {
                val intent = Intent(context, MainActivity::class.java)
                context.startActivity(intent)
            },
            onEpgSettingsClick = {
                shouldRefreshEpg.value = true
                settingsDrawerOpen = false
                android.widget.Toast.makeText(context, "开始更新EPG...", android.widget.Toast.LENGTH_SHORT).show()
            },
            onClose = { settingsDrawerOpen = false }
        )

        val req = catchupRequest

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.Bottom
            ) {
                val catchupConfig = if (req != null) {
                    CatchupConfiguration(
                        startMillis = req.startMillis,
                        endMillis = req.endMillis,
                        positionMs = catchupPositionMs
                    )
                } else null

                val displayChannel = if (req != null) {
                    channels.firstOrNull { it.url == req.liveUrl }
                } else {
                    channels.getOrNull(currentIndex)
                }

                val displayTitle = if (req != null) {
                    "${req.programTitle} (回看中)"
                } else {
                    displayChannel?.let { nowProgramByUrl[it.url]?.title }
                }

                val displayProgress = if (req != null) {
                    null
                } else {
                    displayChannel?.let { nowProgramByUrl[it.url]?.progress }
                }

                val displayNumber = if (req != null) {
                    if (displayChannel != null) channels.indexOf(displayChannel) + 1 else 0
                } else {
                    currentIndex + 1
                }

                ChannelInfoBanner(
                    visible = infoBannerOpen || isSeekOverlayVisible,
                    channel = displayChannel,
                    programTitle = displayTitle,
                    programProgress = displayProgress,
                    channelNumber = displayNumber,
                    videoFormat = videoFormat,
                    catchupConfiguration = catchupConfig,
                    animate = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

private fun buildPrefetchLogoUrls(channels: List<Channel>, currentIndex: Int): List<String> {
    if (channels.isEmpty()) return emptyList()

    val maxUrls = 220
    val out = ArrayList<String>(minOf(maxUrls, channels.size))
    val seen = HashSet<String>(minOf(maxUrls, channels.size))

    fun addAt(index: Int) {
        if (out.size >= maxUrls) return
        val url = channels.getOrNull(index)?.logoUrl?.trim().orEmpty()
        if (url.isNotBlank() && seen.add(url)) out.add(url)
    }

    addAt(currentIndex)
    for (delta in 1..8) {
        addAt(currentIndex - delta)
        addAt(currentIndex + delta)
    }

    val head = minOf(120, channels.size)
    for (i in 0 until head) addAt(i)

    return out
}

private val catchupTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
private fun CatchupProgressBar(
    startMillis: Long,
    endMillis: Long,
    positionMs: Long,
    modifier: Modifier = Modifier,
    isBottomPaddingNeeded: Boolean = false
) {
    val total = (endMillis - startMillis).coerceAtLeast(1L)
    val progress = (positionMs.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    val zone = ZoneId.systemDefault()
    val startText = catchupTimeFormatter.format(Instant.ofEpochMilli(startMillis).atZone(zone))
    val endText = catchupTimeFormatter.format(Instant.ofEpochMilli(endMillis).atZone(zone))

    val containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
    val shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
    val bottomPadding = if (isBottomPaddingNeeded) 20.dp else 4.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(containerColor, shape)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 32.dp, end = 32.dp, top = 20.dp, bottom = bottomPadding)
        ) {
            androidx.tv.material3.Text(
                text = startText,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(12.dp)
            ) {
                // Track
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .align(Alignment.Center)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f), RoundedCornerShape(2.dp))
                )
                
                // Progress Fill
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(4.dp)
                        .align(Alignment.CenterStart)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                )

                // Dot
                val bias = if (progress.isNaN()) -1f else (progress * 2) - 1
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .align(BiasAlignment(bias.coerceIn(-1f, 1f), 0f))
                        .background(ComposeColor.White, CircleShape)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            androidx.tv.material3.Text(
                text = endText,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
            )
        }
    }
}
