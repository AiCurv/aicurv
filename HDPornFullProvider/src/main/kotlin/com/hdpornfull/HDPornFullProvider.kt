package com.hdpornfull

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.ExtractorLink
import com.lagradost.cloudstream3.LoadResponse

class HDPornFullProvider : MainAPI() {
    override var mainUrl = "https://www.hdpornfull.com"
    override var name = "HDPornFull"
    override val supportedTypes = setOf(TvType.Movie)
    override var lang = "en"

    // User agent to bypass simple bot detection
    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
        "Accept-Language" to "en-US,en;q=0.5",
        "Connection" to "keep-alive"
    )

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query"
        val doc = try {
            app.get(url, headers = headers).document
        } catch (e: Exception) {
            return emptyList()
        }

        val results = mutableListOf<SearchResponse>()
        
        // Try different selectors common on movie sites
        val items = doc.select(".film-item, .movie-item, .item-film, article, .result-item")
        
        for (item in items) {
            val titleElement = item.selectFirst("h2, h3, .title, a")
            val linkElement = item.selectFirst("a[href]")
            val posterElement = item.selectFirst("img")
            
            val title = titleElement?.text()?.trim() ?: continue
            val href = linkElement?.attr("href") ?: continue
            val poster = posterElement?.attr("src") ?: posterElement?.attr("data-src")
            
            if (href.contains(mainUrl) || href.startsWith("/")) {
                results.add(
                    newMovieSearchResponse(
                        name = title,
                        url = fixUrl(href),
                        type = TvType.Movie,
                        fixUrl(poster)
                    )
                )
            }
        }
        
        return results.distinctBy { it.name }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = try {
            app.get(url, headers = headers).document
        } catch (e: Exception) {
            throw ErrorLoadingException("Failed to load page: ${e.message}")
        }

        val title = doc.selectFirst("h1, .title, .film-title")?.text()?.trim()
            ?: doc.title().substringBefore("|").substringBefore("-").trim()
        
        val poster = doc.selectFirst("img[poster], .poster img, .film-poster img")?.attr("src")
            ?: doc.selectFirst("meta[property='og:image']")?.attr("content")
        
        val description = doc.selectFirst(".description, .synopsis, .plot")?.text()?.trim()
            ?: doc.selectFirst("meta[property='og:description']")?.attr("content")
        
        val year = Regex("\\d{4}").find(doc.text())?.value?.toIntOrNull()
        
        // Look for iframe or video source
        val iframe = doc.selectFirst("iframe[src*='player'], iframe[src*='embed'], iframe[src*='video']")
        val videoSrc = doc.selectFirst("video source, video[type='video/mp4']")?.attr("src")
        
        val streamUrl = iframe?.attr("src") ?: videoSrc
        
        return newMovieLoadResponse(
            name = title ?: "Unknown",
            url = url,
            type = TvType.Movie,
            dataUrl = streamUrl
        ) {
            this.posterUrl = fixUrl(poster)
            this.plot = description
            this.year = year
        }
    }

    override suspend fun loadLinks(data: String, callback: (ExtractorLink) -> Unit): Boolean {
        if (data.isBlank()) return false
        
        // If it's an external iframe/embed, use loadExtractor which can handle Cloudflare verification
        if (data.startsWith("http")) {
            // Check if it's an external player/embed URL
            if (data.contains("player") || data.contains("embed") || data.contains("video")) {
                // Use loadExtractor to handle the external source - this can open in webview for Cloudflare
                return loadExtractor(data, mainUrl, callback)
            }
            
            // Direct video file
            callback(
                ExtractorLink(
                    source = name,
                    name = name,
                    url = data,
                    referer = mainUrl
                )
            )
            return true
        }
        
        return false
    }

    private fun fixUrl(url: String?): String? {
        if (url == null) return null
        return when {
            url.startsWith("//") -> "https:$url"
            url.startsWith("/") -> "$mainUrl$url"
            !url.startsWith("http") -> "$mainUrl/$url"
            else -> url
        }
    }
}