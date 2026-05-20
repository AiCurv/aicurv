package com.hdpornfull

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.utils.*

class HDPornFullProvider : MainAPI() {
    override var mainUrl = "https://www.hdpornfull.com"
    override var name = "HDPornFull"
    override val supportedTypes = setOf(TvType.NSFW)
    override var lang = "en"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasChromecastSupport = true
    override val vpnStatus = VPNStatus.MightBeNeeded
    override val usesWebView = true

    override val mainPage = mainPageOf(
        "${mainUrl}/" to "Home",
    )

    private fun fetchDocument(url: String): org.jsoup.nodes.Document {
        // Use WebViewResolver to bypass Cloudflare protection
        val response = WebViewResolver(
            url = url,
            originalUrl = url,
            script = "document.querySelector('html')?.outerHTML",
            referer = url,
            jsInterceptor = """window.addEventListener('DOMContentLoaded', () => { 
                const check = setInterval(() => { 
                    if (!document.title.includes('Just a moment')) { 
                        clearInterval(check); 
                    } 
                }, 500); 
            });"""
        ).resolve()
        
        return app.get(url, referer = mainUrl).document
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data, referer = mainUrl).document
        val categoryName = request.name

        // WordPress-style selectors (hdpornfull uses WordPress)
        val home = document.select("article.post, .entry, .post-item, .type-post").mapNotNull { element ->
            val title = element.selectFirst("h2 a, h3 a, .entry-title a, .post-title a")?.text()?.trim()
                ?: return@mapNotNull null
            val href = element.selectFirst("h2 a, h3 a, .entry-title a, .post-title a")?.attr("abs:href")
                ?: return@mapNotNull null
            val poster = element.selectFirst("img")?.let {
                it.attr("data-src").ifEmpty { it.attr("data-lazy-src").ifEmpty { it.attr("src") } }
            }

            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = poster
            }
        }

        return newHomePageResponse(listOf(HomePageList(categoryName, home)), false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("${mainUrl}/?s=${query}", referer = mainUrl).document

        return document.select("article.post, .entry, .post-item, .type-post, .search-results article").mapNotNull { element ->
            val title = element.selectFirst("h2 a, h3 a, .entry-title a, .post-title a")?.text()?.trim()
                ?: return@mapNotNull null
            val href = element.selectFirst("h2 a, h3 a, .entry-title a, .post-title a")?.attr("abs:href")
                ?: return@mapNotNull null
            val poster = element.selectFirst("img")?.let {
                it.attr("data-src").ifEmpty { it.attr("data-lazy-src").ifEmpty { it.attr("src") } }
            }

            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = poster
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url, referer = mainUrl).document

        val title = document.selectFirst("h1.entry-title, h1.post-title, h1, .entry-title")?.text()?.trim()
            ?: document.selectFirst("meta[property=og:title]")?.attr("content")?.trim()
            ?: "Unknown"
        val poster = document.selectFirst("meta[property=og:image]")?.attr("content")
            ?: document.selectFirst(".entry-content img, .post-content img")?.attr("abs:src")
        val description = document.selectFirst(".entry-content p, .post-content p")?.text()?.trim()

        // Get iframe URL for video loading
        val iframeUrl = document.selectFirst("iframe")?.attr("abs:src") ?: ""

        return newMovieLoadResponse(title, url, TvType.NSFW, iframeUrl.ifEmpty { url }) {
            this.posterUrl = poster
            this.plot = description
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data.isNotEmpty()) {
            // Try loading as extractor link (handles many video hosts)
            loadExtractor(data, data, subtitleCallback, callback)
        }

        // Also try to get direct video links from the page
        val document = app.get(data, referer = mainUrl).document

        // Find video source elements
        document.select("video source, video src, .video-source, iframe").forEach { source ->
            val src = source.attr("abs:src").ifEmpty { source.attr("src") }
            if (src.isNotEmpty() && src.startsWith("http")) {
                if (src.contains("iframe") || src.contains("embed")) {
                    loadExtractor(src, data, subtitleCallback, callback)
                } else {
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
        }

        return true
    }
}
