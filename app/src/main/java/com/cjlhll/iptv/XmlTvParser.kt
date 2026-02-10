package com.cjlhll.iptv

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

object XmlTvParser {
    private val XMLTV_DIGITS: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMddHHmmss", Locale.US)

    private val REGEX_OFFSET_4 = Regex("[+-]\\d{4}")
    private val REGEX_OFFSET_COLON = Regex("[+-]\\d{2}:\\d{2}")
    private val REGEX_OFFSET_2 = Regex("[+-]\\d{2}")

    private data class ParsedXmlTvTime(
        val instant: Instant,
        val offset: ZoneOffset?
    )

    fun parse(inputStream: InputStream): EpgData {
        val programsByChannelId = HashMap<String, MutableList<EpgProgram>>()
        val displayNameToId = HashMap<String, String>()

        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, true)
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(inputStream, null)

        var currentChannelId: String? = null

        var programmeChannelId: String? = null
        var programmeStart: ParsedXmlTvTime? = null
        var programmeStop: ParsedXmlTvTime? = null
        var programmeTitle: String? = null

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "channel" -> {
                            currentChannelId = parser.getAttributeValue(null, "id")?.trim()
                        }

                        "display-name" -> {
                            val id = currentChannelId
                            if (!id.isNullOrBlank()) {
                                val name = readText(parser)
                                for (key in EpgNormalize.keys(name)) {
                                    if (key.isNotBlank()) displayNameToId[key] = id
                                }
                            }
                        }

                        "programme" -> {
                            programmeChannelId = parser.getAttributeValue(null, "channel")?.trim()
                            programmeStart = parseXmlTvTime(parser.getAttributeValue(null, "start"))
                            programmeStop = parseXmlTvTime(parser.getAttributeValue(null, "stop"))
                            programmeTitle = null
                        }

                        "title" -> {
                            if (!programmeChannelId.isNullOrBlank()) {
                                programmeTitle = readText(parser)
                            }
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "channel" -> {
                            currentChannelId = null
                        }

                        "programme" -> {
                            val channelId = programmeChannelId
                            val start = programmeStart
                            val stop = programmeStop
                            val title = programmeTitle?.trim().orEmpty()

                            if (!channelId.isNullOrBlank() && start != null && stop != null && title.isNotBlank()) {
                                val sourceOffsetSeconds = (start.offset ?: stop.offset)?.totalSeconds

                                val startInstant = start.instant
                                var stopInstant = stop.instant
                                if (!stopInstant.isAfter(startInstant)) {
                                    stopInstant = stopInstant.plusSeconds(24 * 3600)
                                }

                                val list = programsByChannelId.getOrPut(channelId) { ArrayList() }
                                list.add(
                                    EpgProgram(
                                        channelId = channelId,
                                        startMillis = startInstant.toEpochMilli(),
                                        endMillis = stopInstant.toEpochMilli(),
                                        title = title,
                                        sourceOffsetSeconds = sourceOffsetSeconds
                                    )
                                )
                            }

                            programmeChannelId = null
                            programmeStart = null
                            programmeStop = null
                            programmeTitle = null
                        }
                    }
                }
            }

            eventType = parser.next()
        }

        val sorted = programsByChannelId.mapValues { (_, list) ->
            list.sortedBy { it.startMillis }
        }

        return EpgData(
            programsByChannelId = sorted,
            normalizedDisplayNameToChannelId = displayNameToId
        )
    }

    private fun readText(parser: XmlPullParser): String {
        var result = ""
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.text ?: ""
            parser.nextTag()
        }
        return result.trim()
    }

    private fun parseXmlTvTime(raw: String?): ParsedXmlTvTime? {
        if (raw.isNullOrBlank()) return null
        val s = raw.trim()

        // 1. Try standard XMLTV format first (yyyyMMddHHmmss +HHmm) - Most common
        // Fast check: starts with digit and length >= 14
        if (s.isNotEmpty() && s[0].isDigit()) {
            val digitsEnd = s.indexOfFirst { !it.isDigit() }
            val digitsLen = if (digitsEnd == -1) s.length else digitsEnd
            
            if (digitsLen >= 12) {
                val fullDigits = if (digitsLen >= 14) s.substring(0, 14) else s.substring(0, 12) + "00"
                
                try {
                    val ldt = LocalDateTime.parse(fullDigits, XMLTV_DIGITS)
                    
                    val rest = if (digitsEnd == -1) "" else s.substring(digitsEnd).trim()
                    val offset: ZoneOffset? = when {
                        rest.isEmpty() -> null
                        rest.startsWith("Z", ignoreCase = true) -> ZoneOffset.UTC
                        rest.startsWith("+") || rest.startsWith("-") -> {
                            val token = rest.takeWhile { !it.isWhitespace() }
                            when {
                                token.matches(REGEX_OFFSET_4) -> try { ZoneOffset.of(token) } catch (_: Exception) { null }
                                token.matches(REGEX_OFFSET_COLON) -> try { ZoneOffset.of(token) } catch (_: Exception) { null }
                                token.matches(REGEX_OFFSET_2) -> try { ZoneOffset.of("${token}:00") } catch (_: Exception) { null }
                                else -> null
                            }
                        }
                        else -> null
                    }

                    return if (offset != null) {
                        ParsedXmlTvTime(ldt.atOffset(offset).toInstant(), offset)
                    } else {
                        ParsedXmlTvTime(ldt.atZone(ZoneId.systemDefault()).toInstant(), null)
                    }
                } catch (_: Exception) {
                    // Fallthrough to other formats if parsing fails
                }
            }
        }

        // 2. Try ISO-8601 (Instant)
        try {
            return ParsedXmlTvTime(Instant.parse(s), ZoneOffset.UTC)
        } catch (_: Exception) {}

        // 3. Try ISO-8601 (OffsetDateTime)
        try {
            val odt = OffsetDateTime.parse(s)
            return ParsedXmlTvTime(odt.toInstant(), odt.offset)
        } catch (_: Exception) {}

        return null
    }
}

