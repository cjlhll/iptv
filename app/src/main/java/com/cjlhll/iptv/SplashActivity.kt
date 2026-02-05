package com.cjlhll.iptv

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val liveSource = Prefs.getLiveSource(this)
        val (lastUrl, lastTitle) = Prefs.getLastChannel(this)

        if (liveSource.isNotBlank() && !lastUrl.isNullOrBlank()) {
            // Configured and has history -> Go to Player directly
            val intent = Intent(this, PlayerActivity::class.java).apply {
                putExtra("VIDEO_URL", lastUrl)
                putExtra("VIDEO_TITLE", lastTitle ?: "Live")
            }
            startActivity(intent)
        } else {
            // Not configured or no history -> Go to Config
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }
        
        finish()
    }
}
