package com.mateusrodcosta.apps.lontramusic.data

import androidx.compose.runtime.Immutable
import androidx.core.net.toUri
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.Options
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
    val path: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ArtworkModel) return false
        if (type != other.type) return false
        if (type == ArtworkType.EXTERNAL || type == ArtworkType.MEDIA_STORE) {
            return source == other.source
        }
        if (type == ArtworkType.EMBEDDED && hash != null && other.hash != null) {
            return hash == other.hash
        }
        return id == other.id
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        if (type == ArtworkType.EXTERNAL || type == ArtworkType.MEDIA_STORE) {
            result = 31 * result + (source?.hashCode() ?: 0)
        } else if (type == ArtworkType.EMBEDDED && hash != null) {
            result = 31 * result + hash.hashCode()
        } else {
            result = 31 * result + id.hashCode()
        }
        return result
    }
}

class TrackFetcher(
    private val data: ArtworkModel,
    private val options: Options,
    private val imageLoader: ImageLoader,
) : Fetcher {

    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        val context = options.context

        // 1. Direct Routing: If it's an external file, let Coil handle it natively
        if (data.type == ArtworkType.EXTERNAL && data.source != null) {
            val file = File(data.source)
            if (file.exists()) {
                return@withContext imageLoader.components.newFetcher(file, options, imageLoader)?.first?.fetch()
            }
        }

        // 2. Direct Routing: If it's MediaStore, let Coil handle the Uri natively
        if (data.type == ArtworkType.MEDIA_STORE && data.source != null) {
            val uri = data.source.toUri()
            return@withContext imageLoader.components.newFetcher(uri, options, imageLoader)?.first?.fetch()
        }

        // 3. Fallback/Embedded: Use our custom loader for Opus/Ogg or if direct routing failed
        val sizeLimit = options.size.width.pxOrElse { 0 }.coerceAtLeast(options.size.height.pxOrElse { 0 })
            .takeIf { it > 0 }

        val bitmap = loadArtwork(
            context = context,
            id = data.id,
            path = data.path,
            highRes = true,
            sizeLimit = sizeLimit,
            crop = true
        ) ?: return@withContext null

        ImageFetchResult(
            image = bitmap.asImage(),
            isSampled = sizeLimit != null,
            dataSource = DataSource.DISK
        )
    }

    class Factory : Fetcher.Factory<ArtworkModel> {
        override fun create(data: ArtworkModel, options: Options, imageLoader: ImageLoader): Fetcher {
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
                if (data.hash != null) "embedded_${data.hash}"
                else "track_${data.id}"
            }
            ArtworkType.NONE -> "none_${data.id}"
        }
    }
}
