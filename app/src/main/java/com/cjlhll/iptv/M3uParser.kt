package com.cjlhll.iptv

object M3uParser {
    private val groupRegex = Regex("group-title=\"([^\"]*)\"")

    fun parse(content: String): List<Channel> {
        val channels = ArrayList<Channel>()

        var pendingTitle: String? = null
        var pendingGroup: String? = null

        for (rawLine in content.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            if (line.startsWith("#EXTINF:", ignoreCase = true)) {
                pendingGroup = groupRegex.find(line)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
                pendingTitle = line.substringAfterLast(',', missingDelimiterValue = "").trim().takeIf { it.isNotBlank() }
                continue
            }

            if (line.startsWith('#')) continue

            val url = line
            val title = pendingTitle ?: url

            channels.add(
                Channel(
                    title = title,
                    url = url,
                    group = pendingGroup
                )
            )

            pendingTitle = null
            pendingGroup = null
        }

        return channels
    }
}

