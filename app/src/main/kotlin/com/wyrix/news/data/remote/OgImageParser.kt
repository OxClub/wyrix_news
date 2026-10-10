package com.wyrix.news.data.remote

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Reads the preview image a publisher declares in its page (Open Graph / Twitter card). */
object OgImageParser {
    private val metaTag = Regex("<meta\\s[^>]*>", RegexOption.IGNORE_CASE)
    private val attribute = Regex("([a-zA-Z:_-]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')")
    private val preferred = listOf("og:image:secure_url", "og:image", "twitter:image", "twitter:image:src")

    fun parse(html: String, baseUrl: String): String? {
        val found = HashMap<String, String>()
        for (tag in metaTag.findAll(html)) {
            val attrs = HashMap<String, String>()
            for (m in attribute.findAll(tag.value)) {
                attrs[m.groupValues[1].lowercase()] = m.groupValues[2].ifEmpty { m.groupValues[3] }
            }
            val key = (attrs["property"] ?: attrs["name"])?.lowercase() ?: continue
            val content = attrs["content"]?.trim().orEmpty()
            if (content.isNotEmpty() && key !in found) found[key] = content
        }
        val raw = preferred.firstNotNullOfOrNull { found[it] } ?: return null
        val resolved = baseUrl.toHttpUrlOrNull()?.resolve(raw.replace("&amp;", "&")) ?: return null
        val secure = if (resolved.scheme == "http") resolved.newBuilder().scheme("https").build() else resolved
        return secure.toString().takeIf { it.length <= 2000 }
    }
}
