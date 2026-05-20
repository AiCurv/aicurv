package com.hdpornfull

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class HDPornFullProvider : MainAPI() {
    override var mainUrl = "https://www.hdpornfull.com"
    override var name = "HDPornFull"
    override val supportedTypes = setOf(TvType.Movie)
    override var lang = "en"

    override suspend fun search(query: String): List<SearchResponse> {
        return listOf()
    }
}