package com.cjlhll.iptv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
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

private val epgTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val epgDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd")

@Composable
fun PlayerDrawer(
    visible: Boolean,
    groups: List<String>,
    selectedGroup: String,
    channels: List<Channel>,
    selectedChannelUrl: String?,
    nowProgramTitleByChannelUrl: Map<String, String> = emptyMap(),
    epgData: EpgData? = null,
    nowMillis: Long = 0L,
    onSelectGroup: (String) -> Unit,
    onSelectChannel: (Channel) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
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

    val groupListState = rememberLazyListState()
    val channelListState = rememberLazyListState()
    val programListState = rememberLazyListState()
    val dateListState = rememberLazyListState()

    val groupFocusTargetIndex = remember(groups, selectedGroup) {
        val i = groups.indexOf(selectedGroup)
        if (i >= 0) i else 0
    }

    val groupFocusTargetName = remember(groups, groupFocusTargetIndex) {
        groups.getOrNull(groupFocusTargetIndex)
    }

    val focusTargetIndex = remember(channels, selectedChannelUrl, focusedChannelUrl) {
        val url = focusedChannelUrl ?: selectedChannelUrl
        if (!url.isNullOrBlank()) {
            val i = channels.indexOfFirst { it.url == url }
            if (i >= 0) i else 0
        } else {
            0
        }
    }

    val focusTargetUrl = remember(channels, focusTargetIndex) {
        channels.getOrNull(focusTargetIndex)?.url
    }

    LaunchedEffect(visible) {
        if (visible) {
            showGroups = false
            showDates = false
            pendingFocusToChannels = true
            focusedChannelUrl = selectedChannelUrl ?: channels.firstOrNull()?.url
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
            channelListState.scrollToItem(focusTargetIndex)
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

    val zone = remember { ZoneId.systemDefault() }
    val todayDate = remember(nowMillis, zone) {
        Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    }

    val fullPrograms = remember(epgData, focusedChannel) {
        val data = epgData
        val ch = focusedChannel
        if (data == null || ch == null) return@remember emptyList()
        val channelId = data.resolveChannelId(ch) ?: return@remember emptyList()
        data.programsByChannelId[channelId].orEmpty()
    }

    val epgDates = remember(fullPrograms, zone) {
        if (fullPrograms.isEmpty()) return@remember emptyList()
        fullPrograms
            .asSequence()
            .map { millisToLocalDate(it.startMillis, zone) }
            .distinct()
            .sorted()
            .toList()
    }

    var selectedEpgDate by remember { mutableStateOf<LocalDate?>(null) }
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
        fullPrograms.filter { millisToLocalDate(it.startMillis, zone) == d }
    }

    val programWindow = remember(programsBySelectedDate, nowMillis, selectedEpgDate, todayDate) {
        if (programsBySelectedDate.isEmpty()) {
            ProgramWindow(emptyList(), 0)
        } else {
            val baseIndex = if (selectedEpgDate == todayDate) {
                indexOfProgramAt(programsBySelectedDate, nowMillis)
            } else {
                0
            }
            val start = (baseIndex - 12).coerceAtLeast(0)
            val end = (start + 60).coerceAtMost(programsBySelectedDate.size)
            val slice = programsBySelectedDate.subList(start, end)
            val focusIndex = (baseIndex - start).coerceIn(0, slice.lastIndex)
            ProgramWindow(slice, focusIndex)
        }
    }

    LaunchedEffect(pendingFocusToPrograms, visible, programWindow.focusIndex, programWindow.programs.size) {
        if (!pendingFocusToPrograms) return@LaunchedEffect
        if (!visible) return@LaunchedEffect
        if (programWindow.programs.isEmpty()) {
            pendingFocusToPrograms = false
            return@LaunchedEffect
        }

        withFrameNanos { }

        centerLazyListItem(programListState, programWindow.focusIndex)

        repeat(3) {
            withFrameNanos { }
            if (runCatching { selectedProgramRequester.requestFocus() }.isSuccess) {
                pendingFocusToPrograms = false
                return@LaunchedEffect
            }
        }

        pendingFocusToPrograms = false
    }

    LaunchedEffect(visible, focusedChannelUrl, selectedEpgDate) {
        if (!visible) return@LaunchedEffect
        if (programWindow.programs.isEmpty()) return@LaunchedEffect
        withFrameNanos { }
        centerLazyListItem(programListState, programWindow.focusIndex)
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

    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(animationSpec = tween(160), initialOffsetX = { -it }),
        exit = slideOutHorizontally(animationSpec = tween(140), targetOffsetX = { -it }),
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(scrim)
                .onPreviewKeyEvent {
                    if (it.key == Key.Back) {
                        onClose()
                        true
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
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(
                        animateDpAsState(
                            targetValue =
                                (if (showGroups) (150.dp + 32.dp + 1.dp) else 0.dp) +
                                (200.dp + 32.dp + 1.dp) +
                                ((if (showDates && epgDates.isNotEmpty()) 440.dp else 300.dp) + 32.dp),
                            animationSpec = tween(180),
                            label = "drawerWidth"
                        ).value
                    )
                    .background(container, shape)
                    .padding(0.dp)
            ) {
                Row(modifier = Modifier.fillMaxHeight()) {
                    AnimatedVisibility(
                        visible = showGroups,
                        enter = slideInHorizontally(animationSpec = tween(140), initialOffsetX = { -it }) + fadeIn(tween(120)),
                        exit = slideOutHorizontally(animationSpec = tween(120), targetOffsetX = { -it }) + fadeOut(tween(100)),
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
                            items(channels, key = { it.url }) { ch ->
                                val selected = ch.url == selectedChannelUrl
                                DrawerChannelItem(
                                    channel = ch,
                                    nowProgramTitle = nowProgramTitleByChannelUrl[ch.url],
                                    selected = selected,
                                    focusRequester = if (ch.url == focusTargetUrl) selectedChannelRequester else null,
                                    onFocused = {
                                        activeColumn = DrawerColumn.Channels
                                        focusedChannelUrl = ch.url
                                        showDates = false
                                        showGroups = false
                                    },
                                    onClick = { onSelectChannel(ch) }
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
                            .width(if (showDates && epgDates.isNotEmpty()) 440.dp else 300.dp)
                            .padding(16.dp),
                    ) {
                        val programs = programWindow.programs
                        val dates = epgDates
                        val selectedDate = selectedEpgDate

                        Row(modifier = Modifier.fillMaxSize()) {
                            if (programs.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f, fill = true)
                                        .fillMaxHeight(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "暂无节目单",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .weight(1f, fill = true)
                                        .fillMaxHeight(),
                                    state = programListState
                                ) {
                                    itemsIndexed(
                                        items = programs,
                                        key = { _, p -> "${p.channelId}:${p.startMillis}:${p.endMillis}:${p.title}" }
                                    ) { index, p ->
                                        val timeText = formatEpgTimeRange(p.startMillis, p.endMillis)
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
                                            }
                                        )
                                    }
                                }
                            }

                            AnimatedVisibility(
                                visible = showDates && dates.isNotEmpty(),
                                enter = slideInHorizontally(animationSpec = tween(140), initialOffsetX = { it }) + fadeIn(tween(120)),
                                exit = slideOutHorizontally(animationSpec = tween(120), targetOffsetX = { it }) + fadeOut(tween(100)),
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
    nowProgramTitle: String?,
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
            modifier = Modifier.size(width = 64.dp, height = 40.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f, fill = true)) {
            Text(
                text = channel.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!nowProgramTitle.isNullOrBlank()) {
                FocusMarqueeText(
                    text = nowProgramTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    focused = focused
                )
            } else {
                Text(
                    text = "暂无节目",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                )
            }
        }
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
    onFocused: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.01f)
    }

    val textColor = when (timeState) {
        ProgramTimeState.Past -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
        ProgramTimeState.Now -> Color(0xFF3B82F6)
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

private fun formatEpgTimeRange(startMillis: Long, endMillis: Long): String {
    val zone = ZoneId.systemDefault()
    val start = Instant.ofEpochMilli(startMillis).atZone(zone).toLocalTime()
    val end = Instant.ofEpochMilli(endMillis).atZone(zone).toLocalTime()
    return "${epgTimeFormatter.format(start)}-${epgTimeFormatter.format(end)}"
}

private fun millisToLocalDate(millis: Long, zone: ZoneId): LocalDate {
    return Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
}

private suspend fun centerLazyListItem(state: LazyListState, index: Int) {
    runCatching {
        state.scrollToItem(index)
    }

    withFrameNanos { }

    val layoutInfo = state.layoutInfo
    val item = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return
    val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
    val itemCenter = item.offset + (item.size / 2)
    val delta = itemCenter - viewportCenter
    if (delta != 0) {
        runCatching {
            state.animateScrollBy(delta.toFloat())
        }
    }
}
