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

    override val mainPage = mainPageOf(
        "$mainUrl/" to "Newest",
        "$mainUrl/most-popular" to "Most Popular",
        "$mainUrl/search/MILF" to "MILF",
        "$mainUrl/search/teen" to "Teen",
        "$mainUrl/search/anal" to "Anal",
        "$mainUrl/search/lesbian" to "Lesbian",
        "$mainUrl/search/big-tits" to "Big Tits",
        "$mainUrl/search/ebony" to "Ebony",
        "$mainUrl/search/creampie" to "Creampie",
        "$mainUrl/search/asian" to "Asian",
        "$mainUrl/search/interracial" to "Interracial",
        "$mainUrl/search/threesome" to "Threesome",
        "$mainUrl/search/POV" to "POV",
        "$mainUrl/search/step" to "Step",
        "$mainUrl/search/amateur" to "Amateur",
        "$mainUrl/search/massage" to "Massage",
        "$mainUrl/channels/LegalPorno.com" to "LegalPorno",
        "$mainUrl/channels/Brazzers" to "Brazzers",
        "$mainUrl/channels/Vixen.com" to "Vixen",
        "$mainUrl/channels/Tushy.com" to "Tushy",
        "$mainUrl/channels/EvilAngel.com" to "Evil Angel",
        "$mainUrl/channels/OnlyFans.com" to "OnlyFans",
        "$mainUrl/channels/Deeper.com" to "Deeper",
        "$mainUrl/channels/Mofos.com" to "Mofos",
    )

    private fun String.fixUrl(): String {
        return if (startsWith("//")) "https:$this"
        else if (startsWith("/")) "$mainUrl$this"
        else this
    }

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

        val hasNextPage = document.select("div.pagina a[href*=page=${page + 1}]").first() != null
            || document.select("div.pagina a:contains(Next)").first() != null

        return newHomePageResponse(
            listOf(HomePageList(categoryName, videos)),
            hasNextPage
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        // xxdbx uses dashes between words in search URLs
        // Spaces and + signs don't work, so we replace spaces with dashes
        val searchQuery = query.trim()
            .replace("\s+".toRegex(), "-")
        val searchUrl = "$mainUrl/search/$searchQuery"
        val document = app.get(searchUrl).document

        return document.select("div.v").mapNotNull { parseVideoCard(it) }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("article h1")?.text()?.trim()
            ?: document.selectFirst("h1")?.text()?.trim()
            ?: "Unknown"

        val poster = document.selectFirst("video[poster]")?.attr("poster")?.fixUrl()
            ?: document.selectFirst("img.v_pic")?.let {
                it.attr("data-src").ifEmpty { it.attr("src") }
            }?.fixUrl()

        val description = document.selectFirst("#desc")?.text()?.trim()

        // Tags from video page - these are clickable on the website
        // In Cloudstream they're display-only (app limitation)
        val tags = document.select("div.tags a[href*=/search/]").mapNotNull {
            it.text()?.trim()
        }.filter { it.isNotEmpty() }

        // Stars/Actors from the video page
        val actors = document.select("div.tags a[href*=/stars/]").mapNotNull {
            val starName = it.text()?.trim() ?: return@mapNotNull null
            ActorData(Actor(starName))
        }

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.plot = description
            this.tags = tags
            addActors(actors)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document

        document.select("video source").forEach { source ->
            val src = source.attr("src").fixUrl()
            val qualityLabel = source.attr("title")

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

        val iframeQuery = "iframe[src]"
        document.select(iframeQuery).forEach { iframe ->
            val iframeSrc = iframe.attr("abs:src").ifEmpty { iframe.attr("src") }
            if (iframeSrc.isNotEmpty()) {
                loadExtractor(iframeSrc.fixUrl(), data, subtitleCallback, callback)
            }
        }

        return true
    }
}
