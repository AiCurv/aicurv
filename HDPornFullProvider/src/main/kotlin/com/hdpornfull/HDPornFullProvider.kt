package com.hdpornfull

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.network.*

class HDPornFullProvider : MainAPI() {
    override var mainUrl = "https://www.hdpornfull.com"
    override var name = "HDPornFull"
    override val lang = "en"
    override val supportedTypes = setOf(TvType.Movie, TvType.NSFW)
    
    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search?q=$query"
        val doc = app.get(url).document
        val results = mutableListOf<SearchResponse>()
        
        doc.select(".movie-list .movie-item, .film-list .film-item").forEach { item ->
            val title = item.selectFirst(".movie-title, .film-title, h3 a")?.text() ?: return@forEach
            val href = item.selectFirst("a")?.attr("href") ?: return@forEach
            val poster = item.selectFirst("img")?.attr("data-src") ?: item.selectFirst("img")?.attr("src")
            
            results.add(
                newSearchResponse(name = title, url = href, posterUrl = poster)
            )
        }
        return results
    }
    
    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val title = doc.selectFirst(".movie-info h1, .film-info h1, h1")?.text() ?: "Unknown"
        val poster = doc.selectFirst(".movie-poster img, .film-poster img, .cover img")?.attr("src")
        val description = doc.selectFirst(".movie-description, .film-description, .description")?.text()
        
        val iframeUrl = doc.selectFirst("iframe")?.attr("src")
        
        return newMovieLoadResponse(
            name = title,
            url = url,
            type = TvType.Movie,
            posterUrl = poster,
            plot = description
        ) {
            if (iframeUrl != null) {
                extractor iframeUrl
            }
        }
    }
    
    override suspend fun loadLinks(data: String, isCasting: Boolean, callback: (ExtractorLink) -> Unit) {
        val doc = app.get(data).document
        val videoSrc = doc.selectFirst("video source")?.attr("src")
        if (videoSrc != null) {
            callback(
                ExtractorLink(
                    source = name,
                    quality = "HD",
                    url = videoSrc,
                    type = CINEMA_TYPE.HLS
                )
            )
        }
    }
}