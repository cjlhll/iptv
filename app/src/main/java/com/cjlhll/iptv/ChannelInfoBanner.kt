package com.cjlhll.iptv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Format
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ChannelInfoBanner(
    visible: Boolean,
    channel: Channel?,
    programTitle: String?,
    channelNumber: Int = -1,
    programProgress: Float? = null,
    videoFormat: Format?,
    catchupConfiguration: CatchupConfiguration? = null,
    animate: Boolean = true,
    modifier: Modifier = Modifier
) {
    // UI风格和抽屉保持一致
    // 抽屉是 surface alpha 0.92f
    val containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
    // 底部浮窗，上面两个角圆角
    val shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)

    val content: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(containerColor, shape)
        ) {
            Column {
                if (catchupConfiguration != null) {
                    CatchupProgressRow(
                        startMillis = catchupConfiguration.startMillis,
                        endMillis = catchupConfiguration.endMillis,
                        positionMs = catchupConfiguration.positionMs
                    )
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 32.dp, end = 32.dp, top = if (catchupConfiguration != null) 4.dp else 24.dp, bottom = 24.dp)
                ) {
                    ChannelLogo(
                        logoUrl = channel?.logoUrl,
                        fallbackTitle = channel?.title ?: "",
                        modifier = Modifier.size(width = 80.dp, height = 50.dp),
                        width = 80.dp,
                        height = 50.dp
                    )

                    Spacer(modifier = Modifier.width(20.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        val titleText = if (channelNumber > 0) {
                            "$channelNumber. ${channel?.title ?: "未知频道"}"
                        } else {
                            channel?.title ?: "未知频道"
                        }
                        Text(
                            text = titleText,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (!programTitle.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = programTitle,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (programProgress != null && catchupConfiguration == null) {
                                BannerProgressBar(progress = programProgress)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(20.dp))

                    Column(horizontalAlignment = Alignment.End) {
                        val width = videoFormat?.width ?: 0
                        val height = videoFormat?.height ?: 0
                        val bitrate = videoFormat?.bitrate ?: -1
                        val channels = videoFormat?.channelCount ?: -1
                        val mime = videoFormat?.sampleMimeType ?: ""

                        val resolutionText = if (width > 0 && height > 0) "${width}x${height}" else ""
                        val bitrateText = if (bitrate > 0) "${bitrate / 1000} kbps" else ""
                        val audioText = if (channels > 0) "${channels}ch" else ""
                        val codecText = mime.substringAfter("/")

                        if (resolutionText.isNotEmpty()) TechSpecText(resolutionText)
                        if (bitrateText.isNotEmpty()) TechSpecText(bitrateText)

                        Row {
                            if (codecText.isNotEmpty()) {
                                TechSpecText(codecText)
                                if (audioText.isNotEmpty()) {
                                    Text(
                                        text = " | ",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }
                            }
                            if (audioText.isNotEmpty()) TechSpecText(audioText)
                        }
                    }
                }
            }
        }
    }

    if (animate) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = modifier.fillMaxWidth()
        ) {
            content()
        }
    } else {
        if (!visible) return
        Box(modifier = modifier.fillMaxWidth()) {
            content()
        }
    }
}

data class CatchupConfiguration(
    val startMillis: Long,
    val endMillis: Long,
    val positionMs: Long
)

private val catchupTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
private fun CatchupProgressRow(
    startMillis: Long,
    endMillis: Long,
    positionMs: Long
) {
    val total = (endMillis - startMillis).coerceAtLeast(1L)
    val progress = (positionMs.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    val zone = ZoneId.systemDefault()
    val startText = catchupTimeFormatter.format(Instant.ofEpochMilli(startMillis).atZone(zone))
    val endText = catchupTimeFormatter.format(Instant.ofEpochMilli(endMillis).atZone(zone))

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 32.dp, end = 32.dp, top = 20.dp, bottom = 4.dp)
    ) {
        Text(
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
                    .background(Color.White, CircleShape)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = endText,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
        )
    }
}

@Composable
fun TechSpecText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    )
}

@Composable
private fun BannerProgressBar(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val clamped = progress.coerceIn(0f, 1f)
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val fill = MaterialTheme.colorScheme.primary.copy(alpha = 0.80f)
    Box(
        modifier = modifier
            .padding(top = 4.dp)
            .fillMaxWidth()
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
