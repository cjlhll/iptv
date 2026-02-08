package com.cjlhll.iptv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState

enum class DrawerColumn {
    Groups,
    Channels
}

@Composable
fun PlayerDrawer(
    visible: Boolean,
    groups: List<String>,
    selectedGroup: String,
    channels: List<Channel>,
    selectedChannelUrl: String?,
    nowProgramTitleByChannelUrl: Map<String, String> = emptyMap(),
    onSelectGroup: (String) -> Unit,
    onSelectChannel: (Channel) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val container = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
    val scrim = MaterialTheme.colorScheme.scrim.copy(alpha = 0.42f)
    val shape = RoundedCornerShape(topEnd = 18.dp, bottomEnd = 18.dp)

    val selectedChannelRequester = remember { FocusRequester() }
    var activeColumn by remember { mutableStateOf(DrawerColumn.Channels) }
    var pendingFocusToChannels by remember { mutableStateOf(false) }

    val channelListState = rememberLazyListState()

    val focusTargetIndex = remember(channels, selectedChannelUrl) {
        if (!selectedChannelUrl.isNullOrBlank()) {
            val i = channels.indexOfFirst { it.url == selectedChannelUrl }
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
            pendingFocusToChannels = true
        }
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
                    } else if (it.key == Key.DirectionRight && activeColumn == DrawerColumn.Groups) {
                        pendingFocusToChannels = true
                        true
                    } else {
                        false
                    }
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.68f)
                    .background(container, shape)
                    .padding(0.dp)
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    DrawerColumnPanel(
                        title = "分组",
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(260.dp)
                            .padding(16.dp),
                    ) {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(groups, key = { it }) { g ->
                                DrawerGroupItem(
                                    name = g,
                                    selected = g == selectedGroup,
                                    onFocused = { activeColumn = DrawerColumn.Groups },
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

                    DrawerColumnPanel(
                        title = "频道",
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f)
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
                                    onFocused = { activeColumn = DrawerColumn.Channels },
                                    onClick = { onSelectChannel(ch) }
                                )
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
            .padding(vertical = 6.dp)
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
            .padding(vertical = 6.dp)
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
                Text(
                    text = nowProgramTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
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
