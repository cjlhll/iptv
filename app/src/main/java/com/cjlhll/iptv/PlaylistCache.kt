package com.cjlhll.iptv

import android.content.Context
import java.io.File
import java.security.MessageDigest

object PlaylistCache {
    fun fileNameForSource(sourceUrl: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(sourceUrl.toByteArray(Charsets.UTF_8))
        val hex = buildString(digest.size * 2) {
            for (b in digest) {
                append(((b.toInt() shr 4) and 0xF).toString(16))
                append((b.toInt() and 0xF).toString(16))
            }
        }
        return "playlist_$hex.m3u"
    }

    fun write(context: Context, fileName: String, content: String): File {
        val file = File(context.filesDir, fileName)
        file.writeText(content, Charsets.UTF_8)
        return file
    }

    fun read(context: Context, fileName: String): String? {
        val file = File(context.filesDir, fileName)
        if (!file.exists()) return null
        return file.readText(Charsets.UTF_8)
    }
}

