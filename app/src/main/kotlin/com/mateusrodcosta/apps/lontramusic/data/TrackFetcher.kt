package com.mateusrodcosta.apps.lontramusic.data

import androidx.compose.runtime.Immutable
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.Options
import coil3.request.allowHardware
import coil3.size.pxOrElse
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Immutable
data class ArtworkModel(
    val type: ArtworkType,
    val source: String?,
    val hash: Long?,
    val id: Long,
    val path: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ArtworkModel) return false
        if (type != other.type) return false
        return when (type) {
            ArtworkType.EXTERNAL,
            ArtworkType.MEDIA_STORE -> source == other.source
            ArtworkType.EMBEDDED ->
                if (hash != null && other.hash != null) hash == other.hash else id == other.id
            ArtworkType.NONE -> id == other.id
        }
    }

    override fun hashCode(): Int {
        val result = type.hashCode()
        val extraHash =
            when (type) {
                ArtworkType.EXTERNAL,
                ArtworkType.MEDIA_STORE -> source?.hashCode() ?: 0
                ArtworkType.EMBEDDED -> hash?.hashCode() ?: id.hashCode()
                ArtworkType.NONE -> id.hashCode()
            }
        return 31 * result + extraHash
    }
}

class TrackFetcher(
    private val data: ArtworkModel,
    private val options: Options,
    private val imageLoader: ImageLoader,
) : Fetcher {

    override suspend fun fetch(): FetchResult? =
        withContext(Dispatchers.IO) {
            val context = options.context

            // 1. Direct Routing: If it's an external file, let Coil handle it natively
            if (data.type == ArtworkType.EXTERNAL && data.source != null) {
                val file = File(data.source)
                if (file.exists()) {
                    return@withContext imageLoader.components
                        .newFetcher(file, options, imageLoader)
                        ?.first
                        ?.fetch()
                }
            }

            // 2. Fallback/Embedded/MediaStore: Use our custom loader for Opus/Ogg or MediaStore
            // thumbnails
            val sizeLimit =
                options.size.width
                    .pxOrElse { 0 }
                    .coerceAtLeast(options.size.height.pxOrElse { 0 })
                    .takeIf { it > 0 }

            val bitmap =
                loadArtwork(
                    context = context,
                    id = data.id,
                    path = data.path,
                    highRes = true,
                    sizeLimit = sizeLimit,
                    crop = true,
                    allowHardware = options.allowHardware,
                ) ?: return@withContext null

            ImageFetchResult(
                image = bitmap.asImage(),
                isSampled = sizeLimit != null,
                dataSource = DataSource.DISK,
            )
        }

    class Factory : Fetcher.Factory<ArtworkModel> {
        override fun create(
            data: ArtworkModel,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher {
            return TrackFetcher(data, options, imageLoader)
        }
    }
}

class TrackKeyer : Keyer<ArtworkModel> {
    override fun key(data: ArtworkModel, options: Options): String {
        return when (data.type) {
            ArtworkType.EXTERNAL -> {
                val lastModified = data.source?.let { File(it).lastModified() } ?: 0L
                "folder_${data.source}_$lastModified"
            }
            ArtworkType.MEDIA_STORE -> "uri_${data.source}"
            ArtworkType.EMBEDDED -> {
                if (data.hash != null) "embedded_${data.hash}" else "track_${data.id}"
            }
            ArtworkType.NONE -> "none_${data.id}"
        }
    }
}
