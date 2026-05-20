package com.hdpornfull

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class HDPornFullProvider : MainAPI() {
    override var mainUrl = "https://www.hdpornfull.com"
    override var name = "HDPornFull"
    override val supportedTypes = setOf(TvType.NSFW)
    override var lang = "en"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasChromecastSupport = true

    override val mainPage = mainPageOf(
        "${mainUrl}/" to "Home",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val categoryName = request.name
        val document = app.get(request.data).document

        val home = document.select("article, .post, .video-block, .item, .entry").mapNotNull { element ->
            val title = element.selectFirst("a, h2, h3, .title")?.text()?.trim() ?: return@mapNotNull null
            val href = element.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val poster = element.selectFirst("img")?.attr("src")

            newMovieSearchResponse(title, href) {
                this.posterUrl = poster
            }
        }

        return newHomePageResponse(listOf(HomePageList(categoryName, home)), false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("${mainUrl}/?s=$query").document

        return document.select("article, .post, .video-block, .item, .entry").mapNotNull { element ->
            val title = element.selectFirst("a, h2, h3, .title")?.text()?.trim() ?: return@mapNotNull null
            val href = element.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val poster = element.selectFirst("img")?.attr("src")

            newMovieSearchResponse(title, href) {
                this.posterUrl = poster
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("h1, .entry-title, .title")?.text()?.trim() ?: "Unknown"
        val poster = document.selectFirst("meta[property=og:image]")?.attr("content")
            ?: document.selectFirst("img")?.attr("src")

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document

        // Try to find video source in iframe or video tags
        val iframeSrc = document.selectFirst("iframe")?.attr("src")
        if (!iframeSrc.isNullOrEmpty()) {
            loadExtractor(iframeSrc, data, subtitleCallback, callback)
        }

        // Try to find direct video sources
        document.select("video source, video src, .video-source").forEach { source ->
            val src = source.attr("src")
            if (src.isNotEmpty()) {
                callback(
                    ExtractorLink(
                        source = name,
                        name = name,
                        url = src,
                        referer = data,
                        quality = Qualities.Unknown.value,
                        type = if (src.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                    )
                )
            }
        }

        return true
    }
}
