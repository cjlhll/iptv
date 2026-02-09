package com.cjlhll.iptv

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request

object LogoLoader {
    private const val MAX_ENTRIES = 128
    private var client: OkHttpClient? = null
    private val semaphore = kotlinx.coroutines.sync.Semaphore(3) // 限制并发数，防止UI卡顿

    private val cache = object : LinkedHashMap<String, androidx.compose.ui.graphics.ImageBitmap>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, androidx.compose.ui.graphics.ImageBitmap>): Boolean {
            return size > MAX_ENTRIES
        }
    }

    private fun getClient(context: android.content.Context): OkHttpClient {
        if (client == null) {
            synchronized(this) {
                if (client == null) {
                    val cacheDir = java.io.File(context.cacheDir, "http_logos")
                    if (!cacheDir.exists()) cacheDir.mkdirs()
                    val cacheSize = 100L * 1024 * 1024 // 100MB
                    val cache = okhttp3.Cache(cacheDir, cacheSize)
                    client = OkHttpClient.Builder()
                        .cache(cache)
                        .build()
                }
            }
        }
        return client!!
    }

    private fun getCached(url: String): androidx.compose.ui.graphics.ImageBitmap? = synchronized(cache) { cache[url] }

    private fun putCached(url: String, bitmap: androidx.compose.ui.graphics.ImageBitmap) {
        synchronized(cache) { cache[url] = bitmap }
    }

    suspend fun load(context: android.content.Context, url: String): androidx.compose.ui.graphics.ImageBitmap? {
        if (url.isBlank()) return null
        getCached(url)?.let { return it }

        return semaphore.withPermit {
            // Double check
            getCached(url)?.let { return@withPermit it }

            try {
                val bytes = withContext(Dispatchers.IO) {
                    val request = Request.Builder().url(url).build()
                    try {
                        getClient(context).newCall(request).execute().use { response ->
                            if (!response.isSuccessful) return@use null
                            response.body?.bytes()
                        }
                    } catch (e: Exception) {
                        null
                    }
                } ?: return@withPermit null

                val bitmap = withContext(Dispatchers.Default) {
                    try {
                        // 尝试先读取尺寸，避免加载过大图片
                        val options = BitmapFactory.Options()
                        options.inJustDecodeBounds = true
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                        
                        // 计算缩放比例，目标高度大概 50dp (approx 100-150px)
                        // 假设屏幕密度 2.0-3.0
                        val targetHeight = 100
                        var sampleSize = 1
                        if (options.outHeight > targetHeight) {
                            sampleSize = options.outHeight / targetHeight
                        }
                        
                        val decodeOptions = BitmapFactory.Options()
                        decodeOptions.inSampleSize = sampleSize
                        
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
                    } catch (e: Exception) {
                        null
                    }
                } ?: return@withPermit null

                val imageBitmap = bitmap.asImageBitmap()
                putCached(url, imageBitmap)
                imageBitmap
            } catch (e: Exception) {
                null
            }
        }
    }
}

@Composable
fun ChannelLogo(
    logoUrl: String?,
    fallbackTitle: String,
    modifier: Modifier = Modifier
) {
    var loaded by remember(logoUrl) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(logoUrl) {
        if (loaded != null && logoUrl != null && LogoLoader.load(context, logoUrl) == loaded) return@LaunchedEffect
        
        loaded = null
        val url = logoUrl?.trim().orEmpty()
        if (url.isBlank()) return@LaunchedEffect
        
        // 稍微延迟一点点，让UI先渲染出来
        kotlinx.coroutines.delay(50)
        
        loaded = try {
            LogoLoader.load(context, url)
        } catch (_: Exception) {
            null
        }
    }

    val shape = RoundedCornerShape(10.dp)
    val bg = MaterialTheme.colorScheme.surfaceVariant

    if (loaded != null) {
        Image(
            bitmap = loaded!!,
            contentDescription = fallbackTitle,
            modifier = modifier.clip(shape)
        )
    } else {
        Box(
            modifier = modifier
                .clip(shape)
                .background(bg),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = fallbackTitle.take(1),
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier
                    .padding(6.dp),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
