package com.cjlhll.iptv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.widget.Toast
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val colorScheme = darkColorScheme(
                primary = Color(0xFFBDBDBD),
                onPrimary = Color.Black,
                secondary = Color(0xFF757575),
                onSecondary = Color.Black,
                tertiary = Color(0xFF616161),
                onTertiary = Color.White,
                background = Color(0xFF121212),
                onBackground = Color.White,
                surface = Color(0xFF1E1E1E),
                onSurface = Color.White
            )
            
            MaterialTheme(colorScheme = colorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = androidx.compose.ui.graphics.RectangleShape
                ) {
                    MainScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // Load initial values from Prefs
    val savedLiveSource = remember { Prefs.getLiveSource(context) }
    val savedEpgSource = remember { Prefs.getEpgSource(context) }
    
    var liveSource by remember { mutableStateOf(savedLiveSource) }
    var epgSource by remember { mutableStateOf(savedEpgSource) }
    var isLoading by remember { mutableStateOf(false) }

    fun loadAndPlay(url: String, save: Boolean) {
        if (url.isBlank()) {
            Toast.makeText(context, "请输入直播源地址", Toast.LENGTH_SHORT).show()
            return
        }
        
        isLoading = true
        if (save) {
            Prefs.setLiveSource(context, liveSource)
            Prefs.setEpgSource(context, epgSource)
        }

        coroutineScope.launch {
            try {
                if (save) Toast.makeText(context, "正在加载直播源...", Toast.LENGTH_SHORT).show()
                val client = OkHttpClient()
                val request = Request.Builder().url(url).build()
                
                val response = withContext(Dispatchers.IO) {
                    client.newCall(request).execute()
                }
                
                if (response.isSuccessful) {
                    val content = withContext(Dispatchers.IO) {
                        response.body?.string()
                    }
                    
                    if (content != null) {
                        val (lastUrl, lastTitle) = Prefs.getLastChannel(context)
                        val channel = findChannel(content, lastUrl, lastTitle)
                        
                        if (channel != null) {
                            val (playUrl, playTitle) = channel
                            // Save this channel as last played immediately
                            Prefs.setLastChannel(context, playUrl, playTitle)
                            
                            val intent = Intent(context, PlayerActivity::class.java).apply {
                                putExtra("VIDEO_URL", playUrl)
                                putExtra("VIDEO_TITLE", playTitle)
                            }
                            context.startActivity(intent)
                            
                            // Finish config activity so back button doesn't return here
                            (context as? android.app.Activity)?.finish()
                        } else {
                            Toast.makeText(context, "未找到有效的直播频道", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(context, "加载失败: ${response.code}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: IOException) {
                e.printStackTrace()
                Toast.makeText(context, "网络错误: ${e.message}", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "发生错误: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
            }
        }
    }

    // Auto-play removed as SplashActivity handles routing
    // LaunchedEffect(Unit) { ... }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left side: QR Code Placeholder
        Box(
            modifier = Modifier
                .fillMaxHeight(0.6f)
                .aspectRatio(1f)
                .border(2.dp, Color.Gray, RoundedCornerShape(16.dp))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "二维码",
                style = MaterialTheme.typography.displayMedium,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.width(48.dp))

        // Right side: Inputs and Button
        Column(
            modifier = Modifier
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val textFieldColors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF303030),
                unfocusedContainerColor = Color(0xFF303030),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedLabelColor = Color.LightGray,
                unfocusedLabelColor = Color.Gray,
                cursorColor = Color.White
            )

            TextField(
                value = liveSource,
                onValueChange = { liveSource = it },
                label = { Text("直播源地址") },
                modifier = Modifier.fillMaxWidth(),
                colors = textFieldColors,
                enabled = !isLoading
            )

            TextField(
                value = epgSource,
                onValueChange = { epgSource = it },
                label = { Text("EPG地址") },
                modifier = Modifier.fillMaxWidth(),
                colors = textFieldColors,
                enabled = !isLoading
            )

            Button(
                onClick = { loadAndPlay(liveSource, true) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !isLoading
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (isLoading) "加载中..." else "保存并播放")
                }
            }
        }
    }
}

fun findChannel(m3uContent: String, lastUrl: String?, lastTitle: String?): Pair<String, String>? {
    val lines = m3uContent.lines()
    var currentTitle: String? = null
    
    var firstChannel: Pair<String, String>? = null
    var titleMatch: Pair<String, String>? = null
    
    for (line in lines) {
        val trimmedLine = line.trim()
        if (trimmedLine.startsWith("#EXTINF:")) {
            // Extract title
            val parts = trimmedLine.split(",")
            if (parts.size > 1) {
                currentTitle = parts.last().trim()
            }
        } else if (trimmedLine.isNotEmpty() && !trimmedLine.startsWith("#")) {
            val url = trimmedLine
            val title = currentTitle ?: "Unknown"
            val channel = Pair(url, title)
            
            // First valid channel
            if (firstChannel == null) firstChannel = channel
            
            // Check for exact URL match
            if (lastUrl != null && url == lastUrl) {
                return channel
            }
            
            // Check for Title match (fallback if URL changed)
            if (lastTitle != null && title == lastTitle) {
                titleMatch = channel
            }
            
            currentTitle = null
        }
    }
    
    // Return priority: Title Match -> First Channel
    return titleMatch ?: firstChannel
}
