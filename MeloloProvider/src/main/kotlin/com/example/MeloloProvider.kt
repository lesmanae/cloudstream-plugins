package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.fasterxml.jackson.annotation.JsonProperty

class MeloloProvider : MainAPI() {
    override var mainUrl = "https://proxy-api.o69o.qzz.io"
    override var name = "Melolo"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.TvSeries)

    data class MBook(
        @JsonProperty("drama_name") val dramaName: String? = null,
        @JsonProperty("drama_id") val dramaId: String? = null,
        @JsonProperty("description") val description: String? = null,
        @JsonProperty("thumb_url") val thumbUrl: String? = null,
    )
    data class MHomeData(
        @JsonProperty("books") val books: List<MBook>? = null
    )
    data class MHomeResponse(
        @JsonProperty("data") val data: List<MHomeData>? = null
    )
    data class MSearchResponse(
        @JsonProperty("data") val data: List<MHomeData>? = null
    )
    data class MVideoItem(
        @JsonProperty("episode") val episode: Int? = null,
        @JsonProperty("video_id") val videoId: String? = null,
        @JsonProperty("cover") val cover: String? = null
    )
    data class MDetailData(
        @JsonProperty("drama_name") val dramaName: String? = null,
        @JsonProperty("description") val description: String? = null,
        @JsonProperty("video_list") val videoList: List<MVideoItem>? = null
    )
    data class MDetailResponse(
        @JsonProperty("data") val data: MDetailData? = null
    )
    data class MQuality(
        @JsonProperty("label") val label: String? = null,
        @JsonProperty("url") val url: String? = null
    )
    data class MStreamData(
        @JsonProperty("qualities") val qualities: List<MQuality>? = null
    )
    data class MStreamResponse(
        @JsonProperty("data") val data: MStreamData? = null
    )

    override val mainPage = mainPageOf(
        "$mainUrl/melolo/home" to "Drama Terbaru",
        "$mainUrl/melolo/populer" to "Drama Populer",
        "$mainUrl/melolo/new" to "Drama Baru"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val res = app.get("${request.data}?page=$page").parsed<MHomeResponse>()
        val books = res.data?.flatMap { it.books ?: emptyList() } ?: emptyList()
        val list = books.mapNotNull { book ->
            val id = book.dramaId ?: return@mapNotNull null
            val poster = book.thumbUrl ?: ""
            newTvSeriesSearchResponse(
                name = book.dramaName ?: return@mapNotNull null,
                url = "$mainUrl/melolo/detail/$id|$poster",
                type = TvType.TvSeries
            ) {
                posterUrl = poster
            }
        }
        return newHomePageResponse(request.name, list)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val res = app.get("$mainUrl/melolo/search?q=$query&result=20&page=1").parsed<MSearchResponse>()
        val books = res.data?.flatMap { it.books ?: emptyList() } ?: emptyList()
        return books.mapNotNull { book ->
            val id = book.dramaId ?: return@mapNotNull null
            val poster = book.thumbUrl ?: ""
            newTvSeriesSearchResponse(
                name = book.dramaName ?: return@mapNotNull null,
                url = "$mainUrl/melolo/detail/$id|$poster",
                type = TvType.TvSeries
            ) {
                posterUrl = poster
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val parts = url.split("|")
        val apiUrl = parts[0]
        val poster = if (parts.size > 1) parts[1] else null
        val res = app.get(apiUrl).parsed<MDetailResponse>()
        val data = res.data ?: throw ErrorLoadingException("Data tidak ditemukan")
        val episodes = data.videoList?.mapNotNull { video ->
            val videoId = video.videoId ?: return@mapNotNull null
            val cover = video.cover?.replace(".heic", ".jpeg")
            newEpisode("$mainUrl/melolo/stream/$videoId") {
                episode = video.episode
                posterUrl = cover
            }
        } ?: emptyList()
        return newTvSeriesLoadResponse(
            name = data.dramaName ?: "Unknown",
            url = url,
            type = TvType.TvSeries,
            episodes = episodes
        ) {
            plot = data.description
            posterUrl = poster
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val res = app.get(data).parsed<MStreamResponse>()
        res.data?.qualities?.forEach { quality ->
            val url = quality.url ?: return@forEach
            val label = quality.label ?: "Unknown"
            callback(
                newExtractorLink(
                    source = name,
                    name = "$name $label",
                    url = url,
                    type = ExtractorLinkType.VIDEO
                ) {
                    this.referer = ""
                    this.quality = getQualityFromName(label)
                }
            )
        }
        return true
    }
}
