package com.cjlhll.iptv

import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

enum class DrawerColumn {
    Groups,
    Channels,
    Programs,
    Dates
}

private data class ProgramWindow(
    val programs: List<EpgProgram>,
    val focusIndex: Int
)

private data class EpgChannelUiData(
    val zone: ZoneId,
    val programs: List<EpgProgram>,
    val dates: List<LocalDate>
)

private val epgTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val epgDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd")

private fun zoneForPrograms(programs: List<EpgProgram>): ZoneId {
    val seconds = programs.firstOrNull { it.sourceOffsetSeconds != null }?.sourceOffsetSeconds
    return if (seconds != null) java.time.ZoneOffset.ofTotalSeconds(seconds) else ZoneId.systemDefault()
}

private fun zoneForProgram(program: EpgProgram, fallback: ZoneId): ZoneId {
    val seconds = program.sourceOffsetSeconds
    return if (seconds != null) java.time.ZoneOffset.ofTotalSeconds(seconds) else fallback
}

@OptIn(FlowPreview::class)
@Composable
fun PlayerDrawer(
    visible: Boolean,
    groups: List<String>,
    selectedGroup: String,
    channels: List<Channel>,
    selectedChannelUrl: String?,
    nowProgramByChannelUrl: Map<String, NowProgramUi> = emptyMap(),
    epgData: EpgData? = null,
    nowMillis: Long = 0L,
    onSelectGroup: (String) -> Unit,
    onSelectChannel: (Channel) -> Unit,
    onPlayProgram: (CatchupPlayRequest) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val container = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
    val scrim = Color.Transparent
    val shape = RoundedCornerShape(topEnd = 18.dp, bottomEnd = 18.dp)

    var showGroups by remember { mutableStateOf(false) }
    var showDates by remember { mutableStateOf(false) }

    val selectedGroupRequester = remember { FocusRequester() }
    val selectedChannelRequester = remember { FocusRequester() }
    val selectedProgramRequester = remember { FocusRequester() }
    val selectedDateRequester = remember { FocusRequester() }
    var activeColumn by remember { mutableStateOf(DrawerColumn.Channels) }
    var pendingFocusToGroups by remember { mutableStateOf(false) }
    var pendingFocusToChannels by remember { mutableStateOf(false) }
    var pendingFocusToPrograms by remember { mutableStateOf(false) }
    var pendingFocusToDates by remember { mutableStateOf(false) }

    var focusedChannelUrl by remember { mutableStateOf<String?>(null) }
    var stableFocusedChannelUrl by remember { mutableStateOf<String?>(null) }

    val visibleValue by rememberUpdatedState(visible)

    var autoProgramScrollKey by remember { mutableStateOf<String?>(null) }

    val epgChannelUiCache = remember(epgData) {
        object : LinkedHashMap<String, EpgChannelUiData>(32, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, EpgChannelUiData>?): Boolean {
                return size > 32
            }
        }
    }

    // val groupListState = rememberLazyListState()
    // val channelListState = rememberLazyListState()
    // val programListState = rememberLazyListState()
    // val dateListState = rememberLazyListState()

    LaunchedEffect(Unit) {
        snapshotFlow { focusedChannelUrl }
            .filterNotNull()
            .distinctUntilChanged()
            .debounce(350)
            .collectLatest { url ->
                if (!visibleValue) return@collectLatest
                if (stableFocusedChannelUrl != url) stableFocusedChannelUrl = url
            }
    }

    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        autoProgramScrollKey = null
    }

    val groupFocusTargetIndex = remember(groups, selectedGroup) {
        val i = groups.indexOf(selectedGroup)
        if (i >= 0) i else 0
    }

    val groupFocusTargetName = remember(groups, groupFocusTargetIndex) {
        groups.getOrNull(groupFocusTargetIndex)
    }

    val channelIndexByUrl = remember(channels) {
        HashMap<String, Int>(minOf(channels.size * 2, 4096)).apply {
            for (i in channels.indices) {
                put(channels[i].url, i)
            }
        }
    }

    val focusTargetIndex = remember(channelIndexByUrl, selectedChannelUrl, focusedChannelUrl) {
        val url = focusedChannelUrl ?: selectedChannelUrl
        if (!url.isNullOrBlank()) channelIndexByUrl[url] ?: 0 else 0
    }

    val focusTargetUrl = remember(channels, focusTargetIndex) {
        channels.getOrNull(focusTargetIndex)?.url
    }

    val groupListState = rememberLazyListState()

    val initialScrollIndex = remember(channelIndexByUrl, selectedChannelUrl) {
        val url = selectedChannelUrl
        if (!url.isNullOrBlank()) channelIndexByUrl[url] ?: 0 else 0
    }

    val channelListState = rememberLazyListState()

    val programListState = rememberLazyListState()
    val dateListState = rememberLazyListState()

    LaunchedEffect(visible) {
        if (visible) {
            showGroups = false
            showDates = false
            activeColumn = DrawerColumn.Channels
            // pendingFocusToChannels = true
            val targetUrl = selectedChannelUrl ?: channels.firstOrNull()?.url
            focusedChannelUrl = targetUrl
            stableFocusedChannelUrl = targetUrl

            if (channels.isNotEmpty()) {
                val targetIndex = targetUrl?.let { channelIndexByUrl[it] } ?: 0

                runCatching {
                    snapshotFlow { channelListState.layoutInfo.visibleItemsInfo.isNotEmpty() }
                        .filter { it }
                        .first()
                }

                val visibleItems = channelListState.layoutInfo.visibleItemsInfo
                val selectedVisible = visibleItems.any { it.index == targetIndex }

                if (selectedVisible) {
                    focusedChannelUrl = targetUrl
                    stableFocusedChannelUrl = targetUrl

                    withFrameNanos { }

                    repeat(2) {
                        if (runCatching { selectedChannelRequester.requestFocus() }.isSuccess) return@LaunchedEffect
                        withFrameNanos { }
                    }
                } else {
                    val fallbackIndex = visibleItems.firstOrNull()?.index ?: channelListState.firstVisibleItemIndex
                    val fallbackUrl = channels.getOrNull(fallbackIndex)?.url
                    focusedChannelUrl = fallbackUrl
                    stableFocusedChannelUrl = fallbackUrl

                    withFrameNanos { }

                    repeat(2) {
                        if (runCatching { selectedChannelRequester.requestFocus() }.isSuccess) return@LaunchedEffect
                        withFrameNanos { }
                    }
                }
            }
        }
    }

    LaunchedEffect(selectedGroup) {
        if (!visible) return@LaunchedEffect
        if (!showGroups) return@LaunchedEffect
        if (channels.isEmpty()) return@LaunchedEffect
        runCatching {
            channelListState.scrollToItem(0)
        }
    }

    LaunchedEffect(pendingFocusToGroups, visible, groupFocusTargetName, groupFocusTargetIndex) {
        if (!pendingFocusToGroups) return@LaunchedEffect
        if (!visible) return@LaunchedEffect
        if (!showGroups) {
            pendingFocusToGroups = false
            return@LaunchedEffect
        }
        if (groupFocusTargetName == null) {
            pendingFocusToGroups = false
            return@LaunchedEffect
        }

        withFrameNanos { }

        runCatching {
            groupListState.scrollToItem(groupFocusTargetIndex)
        }

        repeat(3) {
            withFrameNanos { }
            if (runCatching { selectedGroupRequester.requestFocus() }.isSuccess) {
                pendingFocusToGroups = false
                return@LaunchedEffect
            }
        }

        pendingFocusToGroups = false
    }

    LaunchedEffect(pendingFocusToChannels, visible, focusTargetUrl, focusTargetIndex) {
        if (!pendingFocusToChannels) return@LaunchedEffect
        if (!visible) return@LaunchedEffect
        if (focusTargetUrl == null) {
            pendingFocusToChannels = false
            return@LaunchedEffect
        }

        withFrameNanos { }

        runCatching {
            if (channelListState.layoutInfo.visibleItemsInfo.none { it.index == focusTargetIndex }) {
                channelListState.scrollToItem(focusTargetIndex)
            }
        }

        repeat(3) {
            withFrameNanos { }
            if (runCatching { selectedChannelRequester.requestFocus() }.isSuccess) {
                pendingFocusToChannels = false
                return@LaunchedEffect
            }
        }

        pendingFocusToChannels = false
    }

    val focusedChannel = remember(channels, focusedChannelUrl, selectedChannelUrl) {
        val url = focusedChannelUrl ?: selectedChannelUrl
        channels.firstOrNull { it.url == url } ?: channels.firstOrNull()
    }

    val epgDataChannel = remember(channels, stableFocusedChannelUrl, selectedChannelUrl) {
        val url = stableFocusedChannelUrl ?: selectedChannelUrl
        channels.firstOrNull { it.url == url } ?: channels.firstOrNull()
    }

    val epgChannelData by produceState<EpgChannelUiData?>(
        initialValue = epgDataChannel?.url?.let { epgChannelUiCache[it] },
        key1 = epgData,
        key2 = epgDataChannel
    ) {
        val data = epgData
        val ch = epgDataChannel
        if (data == null || ch == null) {
            value = null
            return@produceState
        }
        val cacheKey = ch.url
        val cached = epgChannelUiCache[cacheKey]
        if (cached != null) {
            value = cached
            return@produceState
        }
        value = withContext(Dispatchers.Default) {
            val channelId = data.resolveChannelId(ch) ?: return@withContext EpgChannelUiData(
                zone = ZoneId.systemDefault(),
                programs = emptyList(),
                dates = emptyList()
            )
            val programs = data.programsByChannelId[channelId].orEmpty()
            if (programs.isEmpty()) return@withContext EpgChannelUiData(
                zone = ZoneId.systemDefault(),
                programs = emptyList(),
                dates = emptyList()
            )
            val zone = zoneForPrograms(programs)
            val dates = programs
                .asSequence()
                .map { millisToLocalDate(it.startMillis, zone) }
                .distinct()
                .sorted()
                .toList()
            EpgChannelUiData(zone = zone, programs = programs, dates = dates)
        }

        val computed = value
        if (computed != null) {
            epgChannelUiCache[cacheKey] = computed
        }
    }

    val fullPrograms = epgChannelData?.programs.orEmpty()
    val zone = epgChannelData?.zone ?: ZoneId.systemDefault()
    val todayDate = remember(nowMillis, zone) {
        Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    }

    val epgDates = epgChannelData?.dates.orEmpty()

    var selectedEpgDate by remember { mutableStateOf<LocalDate?>(null) }

    LaunchedEffect(visible) {
        if (!visible) selectedEpgDate = null
    }

    LaunchedEffect(focusedChannelUrl) {
        selectedEpgDate = null
    }

    LaunchedEffect(epgDates, todayDate, focusedChannelUrl, visible) {
        if (!visible) return@LaunchedEffect
        val selected = selectedEpgDate
        if (selected != null && epgDates.contains(selected)) return@LaunchedEffect
        selectedEpgDate = when {
            epgDates.contains(todayDate) -> todayDate
            else -> epgDates.firstOrNull()
        }
    }

    val dateFocusTargetIndex = remember(epgDates, selectedEpgDate) {
        val d = selectedEpgDate ?: return@remember 0
        val i = epgDates.indexOf(d)
        if (i >= 0) i else 0
    }

    val programsBySelectedDate = remember(fullPrograms, selectedEpgDate, zone) {
        val d = selectedEpgDate ?: return@remember emptyList()
        val dayStart = d.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = d.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        sliceProgramsOverlapping(fullPrograms, dayStart, dayEnd)
    }

    val programWindow = remember(programsBySelectedDate, nowMillis, selectedEpgDate, todayDate) {
        if (programsBySelectedDate.isEmpty()) {
            ProgramWindow(emptyList(), 0)
        } else {
            val focusIndex = if (selectedEpgDate == todayDate) {
                indexOfProgramAt(programsBySelectedDate, nowMillis)
            } else {
                0
            }
            ProgramWindow(programsBySelectedDate, focusIndex)
        }
    }

    val programScrollKey = remember(epgDataChannel?.url, selectedEpgDate) {
        val url = epgDataChannel?.url ?: return@remember null
        val date = selectedEpgDate?.toString() ?: ""
        "$url|$date"
    }

    LaunchedEffect(visible, programScrollKey, programWindow.focusIndex, programWindow.programs.size) {
        if (!visible) return@LaunchedEffect
        val key = programScrollKey ?: return@LaunchedEffect
        if (programWindow.programs.isEmpty()) return@LaunchedEffect
        if (autoProgramScrollKey == key) return@LaunchedEffect
        delay(80)
        runCatching { programListState.scrollToItem((programWindow.focusIndex - 3).coerceAtLeast(0)) }
        autoProgramScrollKey = key
    }

    LaunchedEffect(pendingFocusToPrograms, visible, programWindow.focusIndex, programWindow.programs.size) {
        if (!pendingFocusToPrograms) return@LaunchedEffect
        if (!visible) return@LaunchedEffect
        if (programWindow.programs.isEmpty()) {
            withTimeoutOrNull(1500) {
                snapshotFlow { programWindow.programs.isNotEmpty() }
                    .filter { it }
                    .first()
            }
            if (programWindow.programs.isEmpty()) {
                pendingFocusToPrograms = false
                return@LaunchedEffect
            }
        }

        withFrameNanos { }

        runCatching { programListState.scrollToItem((programWindow.focusIndex - 3).coerceAtLeast(0)) }
        withFrameNanos { }

        if (runCatching { selectedProgramRequester.requestFocus() }.isSuccess) {
            pendingFocusToPrograms = false
            return@LaunchedEffect
        }

        runCatching { programListState.scrollToItem((programWindow.focusIndex - 3).coerceAtLeast(0)) }

        repeat(3) {
            withFrameNanos { }
            if (runCatching { selectedProgramRequester.requestFocus() }.isSuccess) {
                pendingFocusToPrograms = false
                return@LaunchedEffect
            }
        }

        pendingFocusToPrograms = false
    }

    LaunchedEffect(visible, focusedChannelUrl, selectedEpgDate, programWindow.focusIndex, programWindow.programs.size) {
        if (!visible) return@LaunchedEffect
        if (programWindow.programs.isEmpty()) return@LaunchedEffect
        if (activeColumn != DrawerColumn.Programs && activeColumn != DrawerColumn.Dates) return@LaunchedEffect
        programListState.scrollToItem((programWindow.focusIndex - 3).coerceAtLeast(0))
    }

    LaunchedEffect(pendingFocusToDates, visible, epgDates.size, dateFocusTargetIndex) {
        if (!pendingFocusToDates) return@LaunchedEffect
        if (!visible) return@LaunchedEffect
        if (!showDates) {
            pendingFocusToDates = false
            return@LaunchedEffect
        }
        if (epgDates.isEmpty()) {
            pendingFocusToDates = false
            return@LaunchedEffect
        }

        withFrameNanos { }

        runCatching {
            dateListState.scrollToItem(dateFocusTargetIndex)
        }

        repeat(3) {
            withFrameNanos { }
            if (runCatching { selectedDateRequester.requestFocus() }.isSuccess) {
                pendingFocusToDates = false
                return@LaunchedEffect
            }
        }

        pendingFocusToDates = false
    }

    val datesVisible = showDates && epgDates.isNotEmpty()
    val targetDrawerWidth = remember(showGroups, datesVisible) {
        val groupsSection = if (showGroups) (150.dp + 1.dp) else 0.dp
        val channelsSection = 300.dp
        val divider = 1.dp
        val programsSection = 300.dp + 32.dp + (if (datesVisible) (110.dp + 10.dp) else 0.dp)
        groupsSection + channelsSection + divider + programsSection
    }
    val drawerWidth by animateDpAsState(
        targetValue = targetDrawerWidth,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "drawerWidth"
    )
    val drawerOffsetX by animateDpAsState(
        targetValue = if (visible) 0.dp else -drawerWidth,
        animationSpec = tween(if (visible) 160 else 140),
        label = "drawerOffsetX"
    )

    val channelCount = channels.size
    val focusedIndex = remember(channelIndexByUrl, focusedChannelUrl) {
        val url = focusedChannelUrl
        if (url.isNullOrBlank()) null else channelIndexByUrl[url]
    }

    Box(modifier = modifier.fillMaxSize()) {
        val overlayModifier = if (visible) {
            Modifier
                .fillMaxSize()
                .background(scrim)
                .onPreviewKeyEvent {
                    if (it.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                    if (it.key == Key.Back) {
                        onClose()
                        true
                    } else if (it.key == Key.DirectionUp && activeColumn == DrawerColumn.Channels && channelCount > 0) {
                        val index = focusedIndex
                        if (index == 0) {
                            focusedChannelUrl = channels.last().url
                            pendingFocusToChannels = true
                            true
                        } else {
                            false
                        }
                    } else if (it.key == Key.DirectionDown && activeColumn == DrawerColumn.Channels && channelCount > 0) {
                        val index = focusedIndex
                        if (index == channelCount - 1) {
                            focusedChannelUrl = channels.first().url
                            pendingFocusToChannels = true
                            true
                        } else {
                            false
                        }
                    } else if (it.key == Key.DirectionLeft && activeColumn == DrawerColumn.Channels) {
                        if (!showGroups) showGroups = true
                        pendingFocusToGroups = true
                        true
                    } else if (it.key == Key.DirectionRight && activeColumn == DrawerColumn.Groups) {
                        pendingFocusToChannels = true
                        true
                    } else if (it.key == Key.DirectionRight && activeColumn == DrawerColumn.Channels) {
                        pendingFocusToPrograms = true
                        true
                    } else if (it.key == Key.DirectionRight && activeColumn == DrawerColumn.Programs) {
                        if (!showDates) showDates = true
                        pendingFocusToDates = true
                        true
                    } else if (it.key == Key.DirectionLeft && activeColumn == DrawerColumn.Programs) {
                        pendingFocusToChannels = true
                        true
                    } else if (it.key == Key.DirectionLeft && activeColumn == DrawerColumn.Dates) {
                        pendingFocusToPrograms = true
                        true
                    } else {
                        false
                    }
                }
        } else {
            Modifier.fillMaxSize()
        }

        Box(modifier = overlayModifier) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(drawerWidth)
                    .offset(x = drawerOffsetX)
                    .background(container, shape)
                    .focusProperties { canFocus = visible }
                    .padding(0.dp)
            ) {
                Row(modifier = Modifier.fillMaxHeight()) {
                    AnimatedVisibility(
                        visible = showGroups,
                        enter = slideInHorizontally(animationSpec = tween(140), initialOffsetX = { -it }) + expandHorizontally(expandFrom = Alignment.Start) + fadeIn(tween(120)),
                        exit = slideOutHorizontally(animationSpec = tween(120), targetOffsetX = { -it }) + shrinkHorizontally(shrinkTowards = Alignment.Start) + fadeOut(tween(100)),
                    ) {
                        Row(modifier = Modifier.fillMaxHeight()) {
                            DrawerColumnPanel(
                                title = "分组",
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(150.dp)
                                    .padding(16.dp),
                            ) {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    state = groupListState
                                ) {
                                    items(groups, key = { it }) { g ->
                                        DrawerGroupItem(
                                            name = g,
                                            selected = g == selectedGroup,
                                            focusRequester = if (g == groupFocusTargetName) selectedGroupRequester else null,
                                            onFocused = {
                                                activeColumn = DrawerColumn.Groups
                                                showDates = false
                                            },
                                            onClick = { onSelectGroup(g) }
                                        )
                                    }
                                }
                            }

                            Spacer(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(1.dp)
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f))
                            )
                        }
                    }

                    DrawerColumnPanel(
                        title = "频道",
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(300.dp)
                            .padding(16.dp),
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            state = channelListState
                        ) {
                            itemsIndexed(channels, key = { _, ch -> ch.url }) { index, ch ->
                                DrawerChannelItem(
                                    channel = ch,
                                    index = index + 1,
                                    selected = (ch.url == selectedChannelUrl),
                                    nowProgram = nowProgramByChannelUrl[ch.url],
                                    focusRequester = if (ch.url == focusTargetUrl) selectedChannelRequester else null,
                                    onFocused = {
                                        activeColumn = DrawerColumn.Channels
                                        focusedChannelUrl = ch.url
                                        showDates = false
                                        showGroups = false
                                    },
                                    onClick = {
                                        focusedChannelUrl = ch.url
                                        // pendingFocusToChannels = true
                                        onSelectChannel(ch)
                                    }
                                )
                            }
                        }
                    }

                    Spacer(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(1.dp)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f))
                    )

                    DrawerColumnPanel(
                        title = "节目单",
                        modifier = Modifier
                            .fillMaxHeight()
                            .wrapContentWidth()
                            .padding(16.dp),
                    ) {
                        val programs = programWindow.programs
                        val dates = epgDates
                        val selectedDate = selectedEpgDate
                        val isEpgLoading = visible && epgData != null && epgDataChannel != null && epgChannelData == null

                        Row(modifier = Modifier.fillMaxSize()) {
                            if (programs.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .width(300.dp)
                                        .fillMaxHeight(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (isEpgLoading) "加载中" else "暂无节目单",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .width(300.dp)
                                        .fillMaxHeight(),
                                    state = programListState
                                ) {
                                    itemsIndexed(
                                        items = programs,
                                        key = { _, p -> "${p.channelId}:${p.startMillis}:${p.endMillis}:${p.title}" }
                                    ) { index, p ->
                                        val timeText = formatEpgTimeRange(
                                            startMillis = p.startMillis,
                                            endMillis = p.endMillis,
                                            zone = zoneForProgram(p, zone)
                                        )
                                        val state = when {
                                            p.endMillis <= nowMillis -> ProgramTimeState.Past
                                            p.startMillis <= nowMillis && nowMillis < p.endMillis -> ProgramTimeState.Now
                                            else -> ProgramTimeState.Future
                                        }
                                        DrawerProgramItem(
                                            time = timeText,
                                            title = p.title,
                                            timeState = state,
                                            focusRequester = if (index == programWindow.focusIndex) selectedProgramRequester else null,
                                            onFocused = {
                                                activeColumn = DrawerColumn.Programs
                                                showDates = true
                                            },
                                            onClick = {
                                                Log.d("PlayerDrawer", "Program clicked: ${p.title}, state=$state")
                                                if (state == ProgramTimeState.Past) {
                                                    val ch = epgDataChannel
                                                    Log.d("PlayerDrawer", "Checking channel for catchup: ${ch?.title}, mode=${ch?.catchupMode}, source=${ch?.catchupSource}")
                                                    
                                                    if (ch != null && ch.catchupMode != null) {
                                                        val url = CatchupHelper.buildCatchupUrl(
                                                            liveUrl = ch.url,
                                                            modeRaw = ch.catchupMode,
                                                            sourceTemplate = ch.catchupSource,
                                                            start = Instant.ofEpochMilli(p.startMillis),
                                                            stop = Instant.ofEpochMilli(p.endMillis)
                                                        )
                                                        Log.d("PlayerDrawer", "Generated catchup URL: $url")
                                                        if (url != null) {
                                                            onPlayProgram(
                                                                CatchupPlayRequest(
                                                                    catchupUrl = url,
                                                                    liveUrl = ch.url,
                                                                    programTitle = p.title,
                                                                    startMillis = p.startMillis,
                                                                    endMillis = p.endMillis
                                                                )
                                                            )
                                                        } else {
                                                            Toast.makeText(context, "回看地址生成失败", Toast.LENGTH_SHORT).show()
                                                        }
                                                    } else {
                                                        Log.d("PlayerDrawer", "Catchup not supported for this channel or mode is null")
                                                        Toast.makeText(context, "该频道不支持回看", Toast.LENGTH_SHORT).show()
                                                    }
                                                } else {
                                                    Log.d("PlayerDrawer", "Program is not in the past, ignoring click")
                                                    Toast.makeText(context, "只能回看历史节目", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        )
                                    }
                                }
                            }

                            AnimatedVisibility(
                                visible = showDates && dates.isNotEmpty(),
                                enter = slideInHorizontally(animationSpec = tween(140), initialOffsetX = { it }) + expandHorizontally(expandFrom = Alignment.Start) + fadeIn(tween(120)),
                                exit = slideOutHorizontally(animationSpec = tween(120), targetOffsetX = { it }) + shrinkHorizontally(shrinkTowards = Alignment.Start) + fadeOut(tween(100)),
                            ) {
                                Row(modifier = Modifier.fillMaxHeight()) {
                                    Spacer(modifier = Modifier.width(10.dp))

                                    LazyColumn(
                                        modifier = Modifier
                                            .width(110.dp)
                                            .fillMaxHeight(),
                                        state = dateListState
                                    ) {
                                        itemsIndexed(
                                            items = dates,
                                            key = { _, d -> d.toString() }
                                        ) { index, d ->
                                            DrawerDateItem(
                                                text = epgDateFormatter.format(d),
                                                selected = (d == selectedDate),
                                                focusRequester = if (index == dateFocusTargetIndex) selectedDateRequester else null,
                                                onFocused = {
                                                    activeColumn = DrawerColumn.Dates
                                                    showDates = true
                                                },
                                                onClick = { selectedEpgDate = d }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerColumnPanel(
    title: String,
    modifier: Modifier,
    content: @Composable () -> Unit
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun DrawerGroupItem(
    name: String,
    selected: Boolean,
    focusRequester: FocusRequester?,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused -> MaterialTheme.colorScheme.surfaceVariant
        selected -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.01f)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .let { m -> if (focusRequester != null) m.focusRequester(focusRequester) else m }
            .background(bg, RoundedCornerShape(12.dp))
            .onFocusChanged {
                if (it.isFocused) onFocused()
                focused = it.isFocused
            }
            .focusable()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(
            text = name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun DrawerChannelItem(
    channel: Channel,
    index: Int,
    selected: Boolean,
    nowProgram: NowProgramUi?,
    focusRequester: FocusRequester?,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused -> MaterialTheme.colorScheme.surfaceVariant
        selected -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.01f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .let { m -> if (focusRequester != null) m.focusRequester(focusRequester) else m }
            .background(bg, RoundedCornerShape(12.dp))
            .onFocusChanged {
                if (it.isFocused) onFocused()
                focused = it.isFocused
            }
            .focusable()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChannelLogo(
            logoUrl = channel.logoUrl,
            fallbackTitle = channel.title,
            modifier = Modifier.size(width = 64.dp, height = 40.dp),
            width = 64.dp,
            height = 40.dp
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f, fill = true)) {
            Text(
                text = "$index. ${channel.title}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!nowProgram?.title.isNullOrBlank()) {
                FocusMarqueeText(
                    text = nowProgram?.title.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    focused = focused
                )
                val progress = nowProgram?.progress
                if (progress != null) {
                    ThinProgressBar(
                        progress = progress,
                        focused = focused,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .fillMaxWidth()
                    )
                }
            } else {
                Text(
                    text = "无信息",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                )
            }
        }
    }
}

@Composable
private fun ThinProgressBar(
    progress: Float,
    focused: Boolean,
    modifier: Modifier = Modifier
) {
    val clamped = progress.coerceIn(0f, 1f)
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = if (focused) 0.16f else 0.12f)
    val fill = MaterialTheme.colorScheme.primary.copy(alpha = if (focused) 0.95f else 0.80f)
    Box(
        modifier = modifier
            .height(3.dp)
            .background(track, RoundedCornerShape(999.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(clamped)
                .background(fill, RoundedCornerShape(999.dp))
        )
    }
}

private enum class ProgramTimeState {
    Past,
    Now,
    Future
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FocusMarqueeText(
    text: String,
    style: TextStyle,
    color: Color,
    focused: Boolean,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        maxLines = 1,
        overflow = if (focused) TextOverflow.Clip else TextOverflow.Ellipsis,
        style = style,
        color = color,
        modifier = modifier.then(if (focused) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier)
    )
}

@Composable
private fun DrawerDateItem(
    text: String,
    selected: Boolean,
    focusRequester: FocusRequester?,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused -> MaterialTheme.colorScheme.surfaceVariant
        selected -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.01f)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .let { m -> if (focusRequester != null) m.focusRequester(focusRequester) else m }
            .background(bg, RoundedCornerShape(12.dp))
            .onFocusChanged {
                if (it.isFocused) onFocused()
                focused = it.isFocused
            }
            .focusable()
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        Text(
            text = text,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun DrawerProgramItem(
    time: String,
    title: String,
    timeState: ProgramTimeState,
    focusRequester: FocusRequester?,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.01f)
    }

    val textColor = when (timeState) {
        ProgramTimeState.Past -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
        ProgramTimeState.Now -> Color(0xFF40C4FF) // Light Blue A200
        ProgramTimeState.Future -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.86f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .let { m -> if (focusRequester != null) m.focusRequester(focusRequester) else m }
            .background(bg, RoundedCornerShape(12.dp))
            .onFocusChanged {
                if (it.isFocused) onFocused()
                focused = it.isFocused
            }
            .focusable()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = time,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            style = MaterialTheme.typography.bodySmall,
            color = textColor,
            modifier = Modifier.width(96.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        FocusMarqueeText(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
            focused = focused,
            modifier = Modifier.weight(1f, fill = true)
        )
    }
}

private fun indexOfProgramAt(programs: List<EpgProgram>, nowMillis: Long): Int {
    if (programs.isEmpty()) return 0
    var lo = 0
    var hi = programs.lastIndex
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        val p = programs[mid]
        if (nowMillis < p.startMillis) {
            hi = mid - 1
        } else if (nowMillis >= p.endMillis) {
            lo = mid + 1
        } else {
            return mid
        }
    }
    return lo.coerceIn(0, programs.lastIndex)
}

private fun lowerBoundStartMillis(programs: List<EpgProgram>, value: Long): Int {
    var lo = 0
    var hi = programs.size
    while (lo < hi) {
        val mid = (lo + hi) ushr 1
        if (programs[mid].startMillis < value) {
            lo = mid + 1
        } else {
            hi = mid
        }
    }
    return lo
}

private fun sliceProgramsOverlapping(programs: List<EpgProgram>, rangeStart: Long, rangeEnd: Long): List<EpgProgram> {
    if (programs.isEmpty()) return emptyList()
    val endIndex = lowerBoundStartMillis(programs, rangeEnd)
    var startIndex = (lowerBoundStartMillis(programs, rangeStart) - 1).coerceAtLeast(0)
    if (startIndex > endIndex) startIndex = endIndex
    if (startIndex == endIndex) return emptyList()
    val slice = programs.subList(startIndex, endIndex)
    return slice.filter { it.endMillis > rangeStart }
}

private fun formatEpgTimeRange(startMillis: Long, endMillis: Long, zone: ZoneId): String {
    val start = Instant.ofEpochMilli(startMillis).atZone(zone).toLocalTime()
    val end = Instant.ofEpochMilli(endMillis).atZone(zone).toLocalTime()
    return "${epgTimeFormatter.format(start)}-${epgTimeFormatter.format(end)}"
}

private fun millisToLocalDate(millis: Long, zone: ZoneId): LocalDate {
    return Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
}


