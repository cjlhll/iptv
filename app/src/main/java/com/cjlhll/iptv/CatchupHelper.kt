package com.cjlhll.iptv

import android.util.Log
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

enum class CatchupMode { APPEND, DEFAULT, NONE }

data class CatchupPlayRequest(
    val catchupUrl: String,
    val liveUrl: String,
    val programTitle: String,
    val startMillis: Long,
    val endMillis: Long
)

object CatchupHelper {
    private const val TAG = "CatchupHelper"
    private val YMDHMS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

    fun formatUtcYmdHms(i: Instant): String =
        i.atOffset(ZoneOffset.UTC).format(YMDHMS)

    fun formatCstYmdHms(i: Instant): String =
        i.atOffset(ZoneOffset.ofHours(8)).format(YMDHMS)

    fun buildCatchupUrl(
        liveUrl: String,
        modeRaw: String?,
        sourceTemplate: String?,
        start: Instant,
        stop: Instant
    ): String? {
        Log.d(TAG, "buildCatchupUrl: modeRaw=$modeRaw, template=$sourceTemplate, start=$start, stop=$stop")
        
        val mode = when (modeRaw?.lowercase()) {
            "append" -> CatchupMode.APPEND
            "default" -> CatchupMode.DEFAULT
            else -> CatchupMode.NONE
        }

        if (mode == CatchupMode.NONE) {
            Log.d(TAG, "Mode is NONE or unknown: $modeRaw")
            return null
        }
        val template = sourceTemplate?.takeIf { it.isNotBlank() } ?: run {
            Log.d(TAG, "Source template is empty")
            return null
        }

        // 格式化时间
        val beginUtc = formatUtcYmdHms(start)
        val endUtc = formatUtcYmdHms(stop)
        val beginCst = formatCstYmdHms(start)
        val endCst = formatCstYmdHms(stop)
        val beginTs = start.epochSecond.toString()
        val endTs = stop.epochSecond.toString()
        
        Log.d(TAG, "Time params: beginUtc=$beginUtc, endUtc=$endUtc, beginCst=$beginCst, endCst=$endCst, beginTs=$beginTs, endTs=$endTs")

        // 替换标准占位符
        var filled = template
            .replace("{begin_utc}", beginUtc)
            .replace("{end_utc}", endUtc)
            .replace("{begin_cst}", beginCst)
            .replace("{end_cst}", endCst)
            .replace("{begin_ts}", beginTs)
            .replace("{end_ts}", endTs)

        // 替换 APTV 风格占位符 (兼容)
        // ${ (b) yyyyMMddHHmmss : utc }
        // 默认不带 :utc 的视为 CST (UTC+8)
        filled = filled
            .replace("\${(b)yyyyMMddHHmmss:utc}", beginUtc)
            .replace("\${(e)yyyyMMddHHmmss:utc}", endUtc)
            .replace("\${(b)yyyyMMddHHmmss}", beginCst)
            .replace("\${(e)yyyyMMddHHmmss}", endCst)
            .replace("\${start}", beginTs)
            .replace("\${end}", endTs)
            .replace("\${timestamp}", beginTs) // 有些源可能用 timestamp
            
        Log.d(TAG, "Filled template: $filled")

        return when (mode) {
            CatchupMode.APPEND -> liveUrl + filled
            CatchupMode.DEFAULT -> filled
            else -> null
        }
    }
}
