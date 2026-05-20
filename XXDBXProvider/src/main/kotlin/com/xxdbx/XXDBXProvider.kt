package com.xxdbx

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class XXDBXProvider : MainAPI() {
    override var mainUrl = "https://xxdbx.com"
    override var name = "XXDBX"
    override val supportedTypes = setOf(TvType.NSFW)
    override var lang = "en"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasChromecastSupport = true

    // Categories on the homepage - each with a URL pattern
    override val mainPage = mainPageOf(
        "$mainUrl/" to "Newest Videos",
        "$mainUrl/most-popular" to "Most Popular",
    )

    private fun String.fixUrl(): String {
        return if (startsWith("//")) "https:$this"
        else if (startsWith("/")) "$mainUrl$this"
        else this
    }

    /**
     * Parse a video card div.v from the listing pages.
     * Each card contains: link to /view/{id}, thumbnail, title, duration, tags.
     */
    private fun parseVideoCard(element: org.jsoup.nodes.Element): SearchResponse? {
        val anchor = element.selectFirst("a[href*=/view/]") ?: return null
        val href = anchor.attr("abs:href").ifEmpty { anchor.attr("href") }.fixUrl()
        if (href.isEmpty()) return null

        val title = element.selectFirst("div.v_title")?.text()?.trim() ?: return null
        val posterImg = element.selectFirst("img.v_pic")
        val poster = posterImg?.let {
            it.attr("data-src").ifEmpty { it.attr("src") }
        }?.fixUrl()

        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = poster
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val pageUrl = if (page > 1) {
            "${request.data}?page=$page"
        } else {
            request.data
        }

        val document = app.get(pageUrl).document
        val categoryName = request.name

        val videos = document.select("div.v").mapNotNull { parseVideoCard(it) }

        // Check if there is a next page
        val hasNextPage = document.select("div.pagina a[href*=page=${page + 1}]").first() != null
            || document.select("div.pagina a:contains(Next)").first() != null

        return newHomePageResponse(
            listOf(HomePageList(categoryName, videos)),
            hasNextPage
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        // xxdbx search URL pattern: /search/{query}
        val searchUrl = "$mainUrl/search/${java.net.URLEncoder.encode(query, "UTF-8")}"
        val document = app.get(searchUrl).document

        return document.select("div.v").mapNotNull { parseVideoCard(it) }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        // Title from h1
        val title = document.selectFirst("article h1")?.text()?.trim()
            ?: document.selectFirst("h1")?.text()?.trim()
            ?: "Unknown"

        // Poster from video element poster attribute
        val poster = document.selectFirst("video[poster]")?.attr("poster")?.fixUrl()
            ?: document.selectFirst("img.v_pic")?.let {
                it.attr("data-src").ifEmpty { it.attr("src") }
            }?.fixUrl()

        // Description from #desc div
        val description = document.selectFirst("#desc")?.text()?.trim()

        // Tags/keywords from links in .tags section
        val tags = document.select("div.tags a[href*=/search/]").mapNotNull {
            it.text()?.trim()
        }.filter { it.isNotEmpty() }

        // Channel
        val channel = document.selectFirst("div.tags a[href*=/channels/]")?.text()?.trim()

        // Stars
        val stars = document.select("div.tags a[href*=/stars/]").mapNotNull {
            it.text()?.trim()
        }.filter { it.isNotEmpty() }

        // Video sources are directly in <video> <source> elements
        // e.g. <source src="//d.v1d30.com/.../360.mp4" title="360p">
        // We pass the URL itself as data so loadLinks can fetch the page and extract sources
        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.plot = description
            this.tags = tags
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document

        // Extract MP4 sources directly from the video element
        // Pattern: <source src="//d.v1d30.com/.../360.mp4" title="360p">
        document.select("video source").forEach { source ->
            val src = source.attr("src").fixUrl()
            val qualityLabel = source.attr("title") // e.g. "360p", "720p", "1080p"

            if (src.isNotEmpty() && src.contains(".mp4")) {
                val quality = when (qualityLabel) {
                    "1080p" -> Qualities.P1080.value
                    "720p" -> Qualities.P720.value
                    "480p" -> Qualities.P480.value
                    "360p" -> Qualities.P360.value
                    else -> Qualities.Unknown.value
                }

                callback(
                    ExtractorLink(
                        source = name,
                        name = "$name $qualityLabel",
                        url = src,
                        referer = data,
                        quality = quality,
                        type = ExtractorLinkType.VIDEO
                    )
                )
            }
        }

        // Also try to find any other video sources in iframes or embeds
        document.select("iframe[src]").forEach { iframe ->
            val iframeSrc = iframe.attr("abs:src").ifEmpty { iframe.attr("src") }
            if (iframeSrc.isNotEmpty()) {
                loadExtractor(iframeSrc.fixUrl(), data, subtitleCallback, callback)
            }
        }

        return true
    }
}
