package com.cjlhll.iptv

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream

object EpgRepository {
    private const val TAG = "EPG"

    @Volatile
    private var memoryCache: EpgData? = null

    suspend fun load(context: Context, epgSourceUrl: String, forceRefresh: Boolean = false): EpgData? {
        if (epgSourceUrl.isBlank()) return null

        // 1. Memory Cache
        if (!forceRefresh) {
            val mem = memoryCache
            if (mem != null) return mem
        }

        return withContext(Dispatchers.IO) {
            val cacheFileName = EpgCache.fileNameForSource(epgSourceUrl)
            val parsedCacheFileName = "$cacheFileName.ser"

            if (!forceRefresh) {
                // 2. Fast Parsed Disk Cache
                val fastCache = loadParsedCache(context, parsedCacheFileName)
                if (fastCache != null) {
                    Log.i(TAG, "Loaded EPG from fast parsed cache")
                    memoryCache = fastCache
                    return@withContext fastCache
                }

                // 3. Raw XML Disk Cache
                val cachedFile = EpgCache.getFile(context, cacheFileName)
                if (cachedFile != null && cachedFile.exists() && cachedFile.length() > 0) {
                    val parsed = parseFile(cachedFile)
                    if (parsed != null) {
                        saveParsedCache(context, parsedCacheFileName, parsed)
                        memoryCache = parsed
                        return@withContext parsed
                    }
                }
            }

            // 4. Network Download
            val client = OkHttpClient()
            val request = Request.Builder().url(epgSourceUrl).build()

            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "Download failed code=${response.code}")
                        null
                    } else {
                        val body = response.body
                        if (body != null) {
                            // Stream download to file directly to avoid large memory allocation
                            val tempFile = EpgCache.writeStream(context, cacheFileName, body.byteStream())
                            if (tempFile != null) {
                                val parsed = parseFile(tempFile)
                                if (parsed != null) {
                                    saveParsedCache(context, parsedCacheFileName, parsed)
                                    memoryCache = parsed
                                    parsed
                                } else null
                            } else null
                        } else null
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "download failed: ${e.message}")
                
                // If network failed and we were forcing refresh, fallback to caches
                if (forceRefresh) {
                    val fastCache = loadParsedCache(context, parsedCacheFileName)
                    if (fastCache != null) {
                        memoryCache = fastCache
                        return@withContext fastCache
                    }
                    
                    val cachedFile = EpgCache.getFile(context, cacheFileName)
                    if (cachedFile != null && cachedFile.exists()) {
                         val parsed = parseFile(cachedFile)
                         if (parsed != null) {
                             memoryCache = parsed
                             return@withContext parsed
                         }
                    }
                }
                null
            }
        }
    }


    private fun loadParsedCache(context: Context, fileName: String): EpgData? {
        return try {
            val file = java.io.File(context.filesDir, fileName)
            if (!file.exists()) return null
            java.io.ObjectInputStream(java.io.FileInputStream(file)).use {
                it.readObject() as? EpgData
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load parsed cache: ${e.message}")
            try { java.io.File(context.filesDir, fileName).delete() } catch (_: Exception) {}
            null
        }
    }

    private fun saveParsedCache(context: Context, fileName: String, data: EpgData) {
        try {
            val file = java.io.File(context.filesDir, fileName)
            java.io.ObjectOutputStream(java.io.FileOutputStream(file)).use {
                it.writeObject(data)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save parsed cache: ${e.message}")
        }
    }

    private fun parseFile(file: java.io.File): EpgData? {
        return try {
            val inputStream = java.io.FileInputStream(file)
            val stream = if (looksLikeGzip(file)) java.util.zip.GZIPInputStream(inputStream) else inputStream
            stream.use { XmlTvParser.parse(it) }
        } catch (e: Exception) {
            Log.w(TAG, "parse xmltv failed: ${e.message}")
            null
        }
    }

    private fun looksLikeGzip(file: java.io.File): Boolean {
        if (file.length() < 2) return false
        try {
            java.io.FileInputStream(file).use { fis ->
                val bytes = ByteArray(2)
                if (fis.read(bytes) != 2) return false
                return bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()
            }
        } catch (_: Exception) {
            return false
        }
    }
}

