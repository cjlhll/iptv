package com.cjlhll.iptv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Format
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

@Composable
fun ChannelInfoBanner(
    visible: Boolean,
    channel: Channel?,
    programTitle: String?,
    videoFormat: Format?,
    roundedTopCorners: Boolean = true,
    modifier: Modifier = Modifier
) {
    // UI风格和抽屉保持一致
    // 抽屉是 surface alpha 0.92f
    val containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
    // 底部浮窗，上面两个角圆角
    val shape = if (roundedTopCorners) RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp) else RectangleShape

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(containerColor, shape)
                .padding(horizontal = 32.dp, vertical = 24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Channel Logo
                ChannelLogo(
                    logoUrl = channel?.logoUrl,
                    fallbackTitle = channel?.title ?: "",
                    modifier = Modifier.size(width = 80.dp, height = 50.dp)
                )

                Spacer(modifier = Modifier.width(20.dp))

                // Info Column
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = channel?.title ?: "未知频道",
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
                    }
                }

                Spacer(modifier = Modifier.width(20.dp))

                // Tech Specs Column
                Column(horizontalAlignment = Alignment.End) {
                    val width = videoFormat?.width ?: 0
                    val height = videoFormat?.height ?: 0
                    val bitrate = videoFormat?.bitrate ?: -1
                    // Audio channels count
                    val channels = videoFormat?.channelCount ?: -1
                    val mime = videoFormat?.sampleMimeType ?: ""

                    val resolutionText = if (width > 0 && height > 0) "${width}x${height}" else ""
                    // Bitrate in Format is often NO_VALUE (-1) for adaptive streams until selected
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

@Composable
fun TechSpecText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    )
}
