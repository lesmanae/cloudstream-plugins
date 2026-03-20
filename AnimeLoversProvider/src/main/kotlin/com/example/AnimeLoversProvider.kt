package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.fasterxml.jackson.annotation.JsonProperty

class AnimeLoversProvider : MainAPI() {
    override var mainUrl = "https://proxy-api.o69o.qzz.io"
    override var name = "AnimeLovers"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Anime)

    data class AAnime(
        @JsonProperty("url") val url: String? = null,
        @JsonProperty("judul") val judul: String? = null,
        @JsonProperty("cover") val cover: String? = null,
        @JsonProperty("sinopsis") val sinopsis: String? = null,
        @JsonProperty("status") val status: String? = null,
        @JsonProperty("genre") val genre: List<String>? = null,
    )
    data class AHomeResponse(
        @JsonProperty("data") val data: List<AAnime>? = null
    )
    data class ASearchData(
        @JsonProperty("jumlah") val jumlah: Int? = null,
        @JsonProperty("result") val result: List<AAnime>? = null
    )
    data class ASearchResponse(
        @JsonProperty("data") val data: List<ASearchData>? = null
    )
    data class AEpisode(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("ch") val ch: String? = null,
        @JsonProperty("url") val url: String? = null,
    )
    data class ADetail(
        @JsonProperty("series_id") val seriesId: String? = null,
        @JsonProperty("judul") val judul: String? = null,
        @JsonProperty("cover") val cover: String? = null,
        @JsonProperty("sinopsis") val sinopsis: String? = null,
        @JsonProperty("status") val status: String? = null,
        @JsonProperty("genre") val genre: List<String>? = null,
        @JsonProperty("chapter") val chapter: List<AEpisode>? = null,
    )
    data class ADetailResponse(
        @JsonProperty("data") val data: List<ADetail>? = null
    )
    data class AStreamLink(
        @JsonProperty("link") val link: String? = null,
        @JsonProperty("reso") val reso: String? = null,
    )
    data class AStreamData(
        @JsonProperty("streams") val streams: Map<String, List<AStreamLink>>? = null
    )
    data class AStreamResponse(
        @JsonProperty("data") val data: List<AStreamData>? = null
    )

    override val mainPage = mainPageOf(
        "$mainUrl/anime/home" to "Anime Terbaru",
        "$mainUrl/anime/ongoing" to "Anime Ongoing",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (request.data.contains("ongoing")) request.data
                  else "${request.data}?page=$page"
        val res = app.get(url).parsed<AHomeResponse>()
        val list = res.data?.mapNotNull { anime ->
            val slug = anime.url?.trimEnd('/') ?: return@mapNotNull null
            newAnimeSearchResponse(
                name = anime.judul ?: return@mapNotNull null,
                url = "$mainUrl/anime/detail?series=$slug",
                type = TvType.Anime
            ) {
                posterUrl = anime.cover
            }
        } ?: emptyList()
        return newHomePageResponse(request.name, list)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val res = app.get("$mainUrl/anime/search?query=$query&page=1").parsed<ASearchResponse>()
        val results = res.data?.firstOrNull()?.result ?: emptyList()
        return results.mapNotNull { anime ->
            val slug = anime.url?.trimEnd('/') ?: return@mapNotNull null
            newAnimeSearchResponse(
                name = anime.judul ?: return@mapNotNull null,
                url = "$mainUrl/anime/detail?series=$slug",
                type = TvType.Anime
            ) {
                posterUrl = anime.cover
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val res = app.get(url).parsed<ADetailResponse>()
        val data = res.data?.firstOrNull() ?: throw ErrorLoadingException("Data tidak ditemukan")
        val seriesId = data.seriesId ?: ""
        val episodes = data.chapter?.reversed()?.mapNotNull { ep ->
            val slug = ep.url ?: return@mapNotNull null
            val epNum = ep.ch?.filter { it.isDigit() }?.toIntOrNull()
            newEpisode("$mainUrl/anime/stream?slug=$slug&series=$seriesId&episode=${epNum ?: 1}") {
                name = "Episode ${ep.ch}"
                episode = epNum
            }
        } ?: emptyList()
        return newAnimeLoadResponse(
            data.judul ?: "Unknown",
            url,
            TvType.Anime,
            false
        ) {
            addEpisodes(DubStatus.Subbed, episodes)
            plot = data.sinopsis
            posterUrl = data.cover
            tags = data.genre
            showStatus = when (data.status) {
                "Ongoing" -> ShowStatus.Ongoing
                "Completed" -> ShowStatus.Completed
                else -> null
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val res = app.get(data).parsed<AStreamResponse>()
        val streams = res.data?.firstOrNull()?.streams ?: return false
        streams.forEach { (reso, links) ->
            links.firstOrNull { !it.link.isNullOrEmpty() }?.link?.let { link ->
                callback(
                    newExtractorLink(
                        source = name,
                        name = "$name $reso",
                        url = link,
                        type = ExtractorLinkType.VIDEO
                    ) {
                        this.referer = ""
                        this.quality = getQualityFromName(reso)
                    }
                )
            }
        }
        return true
    }
}
