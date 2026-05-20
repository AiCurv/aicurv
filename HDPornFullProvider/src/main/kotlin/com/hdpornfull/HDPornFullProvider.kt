package com.hdpornfull

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.network.*

class HDPornFullProvider : MainAPI() {
    override var mainUrl = "https://www.hdpornfull.com"
    override var name = "HDPornFull"
    override val lang = "en"
    override val tvTypes = listOf(TvType.Movie, TvType.NSFW)
    
    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search?q=$query"
        val doc = app.get(url).document
        val results = mutableListOf<SearchResponse>()
        
        doc.select(".movie-list .movie-item").forEach { item ->
            val title = item.selectFirst(".movie-title")?.text() ?: return@forEach
            val href = item.selectFirst("a")?.attr("href") ?: return@forEach
            val poster = item.selectFirst("img")?.attr("data-src") ?: item.selectFirst("img")?.attr("src")
            
            results.add(SearchResponse(
                name = title,
                url = href,
                posterUrl = poster,
                type = TvType.Movie
            ))
        }
        return results
    }
    
    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val title = doc.selectFirst(".movie-info h1")?.text() ?: "Unknown"
        val poster = doc.selectFirst(".movie-poster img")?.attr("src")
        val description = doc.selectFirst(".movie-description")?.text()
        
        // Get iframe or video source
        val iframeUrl = doc.selectFirst("iframe")?.attr("src")
        
        return newMovieLoadResponse(
            name = title,
            url = url,
            type = TvType.Movie,
            posterUrl = poster,
            plot = description
        ) {
            // This is where we add video links
            if (iframeUrl != null) {
                extractor iframeUrl
            }
        }
    }
    
    override suspend fun loadLinks(data: String, isCasting: Boolean, callback: (ExtractorLink) -> Unit) {
        // For direct video sources on the page
        val doc = app.get(data).document
        val videoSrc = doc.selectFirst("video source")?.attr("src")
        if (videoSrc != null) {
            callback(ExtractorLink(
                source = name,
                quality = "HD",
                url = videoSrc,
                type = INEMA_TYPE.HLS
            ))
        }
    }
}