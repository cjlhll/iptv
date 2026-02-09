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

        runCatching { Instant.parse(s) }.getOrNull()?.let { return ParsedXmlTvTime(it, ZoneOffset.UTC) }
        runCatching {
            val odt = OffsetDateTime.parse(s)
            ParsedXmlTvTime(odt.toInstant(), odt.offset)
        }.getOrNull()?.let { return it }

        val digits = s.takeWhile { it.isDigit() }
        if (digits.length < 12) return null
        val fullDigits = if (digits.length >= 14) digits.substring(0, 14) else digits.substring(0, 12) + "00"
        val ldt = runCatching { LocalDateTime.parse(fullDigits, XMLTV_DIGITS) }.getOrNull() ?: return null

        val rest = s.substring(digits.length).trim()
        val offset: ZoneOffset? = when {
            rest.isEmpty() -> null
            rest.startsWith("Z", ignoreCase = true) -> ZoneOffset.UTC
            rest.startsWith("+") || rest.startsWith("-") -> {
                val token = rest.takeWhile { !it.isWhitespace() }
                when {
                    token.matches(Regex("[+-]\\d{4}")) -> runCatching { ZoneOffset.of(token) }.getOrNull()
                    token.matches(Regex("[+-]\\d{2}:\\d{2}")) -> runCatching { ZoneOffset.of(token) }.getOrNull()
                    token.matches(Regex("[+-]\\d{2}")) -> runCatching { ZoneOffset.of("${token}:00") }.getOrNull()
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
    }
}

