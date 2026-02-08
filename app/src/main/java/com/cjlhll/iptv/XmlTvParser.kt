package com.cjlhll.iptv

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object XmlTvParser {
    private val timeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
    private val timeRegex = Regex("^(\\d{12,14})(?:\\s*([+-]\\d{2}:?\\d{2}|Z))?.*$")

    fun parse(inputStream: InputStream): EpgData {
        val programsByChannelId = HashMap<String, MutableList<EpgProgram>>()
        val displayNameToId = HashMap<String, String>()

        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, true)
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(inputStream, null)

        var currentChannelId: String? = null

        var programmeChannelId: String? = null
        var programmeStart: Long? = null
        var programmeStop: Long? = null
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
                            val startMillis = programmeStart
                            val endMillis = programmeStop
                            val title = programmeTitle?.trim().orEmpty()

                            if (!channelId.isNullOrBlank() && startMillis != null && endMillis != null && title.isNotBlank()) {
                                val list = programsByChannelId.getOrPut(channelId) { ArrayList() }
                                list.add(
                                    EpgProgram(
                                        channelId = channelId,
                                        startMillis = startMillis,
                                        endMillis = endMillis,
                                        title = title
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

    private fun parseXmlTvTime(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        val m = timeRegex.find(trimmed) ?: return null
        val timeDigits = m.groupValues.getOrNull(1).orEmpty()
        val tzPart = m.groupValues.getOrNull(2)?.trim()

        if (timeDigits.length < 12) return null
        val full = if (timeDigits.length >= 14) timeDigits.substring(0, 14) else timeDigits.substring(0, 12) + "00"

        val ldt = LocalDateTime.parse(full, timeFormatter)
        val offset = parseOffset(tzPart) ?: ZoneId.systemDefault().rules.getOffset(ldt)
        return ldt.toInstant(offset).toEpochMilli()
    }

    private fun parseOffset(raw: String?): ZoneOffset? {
        if (raw.isNullOrBlank()) return null
        val s = raw.trim()
        if (s.equals("Z", ignoreCase = true)) return ZoneOffset.UTC

        val sign = when {
            s.startsWith("+") -> 1
            s.startsWith("-") -> -1
            else -> return null
        }

        val digits = s.drop(1).filter { it.isDigit() }
        if (digits.length < 4) return null
        val hours = digits.substring(0, 2).toIntOrNull() ?: return null
        val minutes = digits.substring(2, 4).toIntOrNull() ?: return null
        val totalSeconds = sign * (hours * 3600 + minutes * 60)
        return ZoneOffset.ofTotalSeconds(totalSeconds)
    }
}

