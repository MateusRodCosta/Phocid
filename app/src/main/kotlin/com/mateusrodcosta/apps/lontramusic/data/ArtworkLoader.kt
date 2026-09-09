package com.mateusrodcosta.apps.lontramusic.data

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.WindowManager
import com.mateusrodcosta.apps.lontramusic.utils.coerceInOrMin
import com.mateusrodcosta.apps.lontramusic.utils.roundToIntOrZero
import com.mateusrodcosta.apps.lontramusic.utils.trimAndNormalize
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.min
import org.apache.commons.io.FilenameUtils
import org.jaudiotagger.audio.AudioFileIO
import org.sunsetware.omio.VORBIS_COMMENT_METADATA_BLOCK_PICTURE
import org.sunsetware.omio.decodeMetadataBlockPicture
import org.sunsetware.omio.readOpusMetadata

/** https://developer.android.com/media/platform/supported-formats#image-formats */
private val imageFileExtensionScores =
    listOf("png", "bmp", "jpg", "jpeg", "webp", "heif", "heic", "gif")
        .reversed()
        .mapIndexed { index, extension -> extension to index }
        .toMap()

/** "Folder" seems to be off-limits and can't be accessed, listed for completeness */
private val imageFileNameScores =
    listOf("cover", "folder", "artwork", "front", "album")
        .sortedDescending()
        .mapIndexed { index, name -> name to index }
        .toMap()

private val imageMimeTypes =
    setOf(
        "image/bmp",
        "image/gif",
        "image/jpeg",
        "image/png",
        "image/webp",
        "image/heic",
        "image/heif",
    )

private val cachedScreenSize = AtomicInteger(0)

enum class ArtworkSourceType {
    EMBEDDED,
    EXTERNAL,
    MEDIA_STORE,
}

data class ResolvedArtwork(
    val type: ArtworkSourceType,
    val source: String,
)

fun resolveArtworkSource(
    path: String?,
    uri: Uri,
    scanCache: ScanCache? = null,
): ResolvedArtwork? {
    if (path != null) {
        // 1. Check embedded
        val extension = FilenameUtils.getExtension(path).lowercase()
        val hasEmbedded = try {
            if (extension == "opus" || extension == "ogg") {
                FileInputStream(File(path)).buffered().use { stream ->
                    val metadata = readOpusMetadata(stream, false)
                    metadata.userComments[VORBIS_COMMENT_METADATA_BLOCK_PICTURE]?.any { block ->
                        decodeMetadataBlockPicture(block)?.let {
                            imageMimeTypes.contains(it.mimeType.trimAndNormalize())
                        } ?: false
                    } ?: false
                }
            } else {
                AudioFileIO.read(File(path)).tag.firstArtwork != null
            }
        } catch (_: Exception) {
            false
        }
        if (hasEmbedded) return ResolvedArtwork(ArtworkSourceType.EMBEDDED, path)

        // 2. Check folder external artwork file (e.g. cover.jpg, folder.jpg)
        val folder = path.substringBeforeLast('/', "")
        val folderArtwork = if (scanCache != null) {
            scanCache.folderArtwork.getOrPut(folder) {
                OptionalArtwork(findExternalArtworkFile(path)?.absolutePath?.let {
                    ResolvedArtwork(ArtworkSourceType.EXTERNAL, it)
                })
            }.artwork
        } else {
            findExternalArtworkFile(path)?.absolutePath?.let {
                ResolvedArtwork(ArtworkSourceType.EXTERNAL, it)
            }
        }
        if (folderArtwork != null) return folderArtwork
    }

    // 3. Fallback to MediaStore
    return ResolvedArtwork(ArtworkSourceType.MEDIA_STORE, uri.toString())
}

private fun findExternalArtworkFile(path: String?): File? {
    if (path == null) return null

    val directoryName = FilenameUtils.getName(FilenameUtils.getPathNoEndSeparator(path))
    val files = try {
        File(FilenameUtils.getPath(path)).listFiles() ?: emptyArray()
    } catch (_: Exception) {
        emptyArray()
    }

    return files
        .mapNotNull { file ->
            val name = file.nameWithoutExtension
            val extension = file.extension
            val extensionScore = imageFileExtensionScores[extension.lowercase()] ?: return@mapNotNull null
            val nameScore = when {
                name.equals(directoryName, true) -> 998
                else -> imageFileNameScores[name.lowercase()] ?: return@mapNotNull null
            }
            file to (nameScore * 1000 + extensionScore)
        }
        .sortedByDescending { it.second }
        .firstOrNull()?.first
}

fun loadArtwork(
    context: Context,
    uri: Uri,
    path: String?,
    highRes: Boolean = false,
    sizeLimit: Int? = null,
    crop: Boolean = false,
): Bitmap? {
    val forcedSizeLimit =
        sizeLimit
            ?: cachedScreenSize.get().takeIf { it > 0 }
            ?: run {
                val screenSize =
                    (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                        .maximumWindowMetrics
                        .bounds
                val limit = min(screenSize.width(), screenSize.height()).coerceAtLeast(256)
                cachedScreenSize.set(limit)
                limit
            }

    return if (highRes) {
        loadWithLibrary(path, forcedSizeLimit, crop)
            ?: loadExternal(path, forcedSizeLimit, crop)
            ?: loadWithContentResolver(context, uri, forcedSizeLimit, crop)
    } else {
        loadWithContentResolver(context, uri, forcedSizeLimit, crop)
            ?: loadExternal(path, forcedSizeLimit, crop)
    }
}

fun loadArtwork(
    context: Context,
    id: Long,
    path: String?,
    highRes: Boolean = false,
    sizeLimit: Int? = null,
    crop: Boolean = false,
): Bitmap? {
    return loadArtwork(
        context,
        ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
        path,
        highRes,
        sizeLimit,
        crop,
    )
}

fun getEmbeddedArtworkHash(path: String?): Long? {
    return try {
        requireNotNull(path)
        val extension = FilenameUtils.getExtension(path).lowercase()
        val data =
            if (extension == "opus" || extension == "ogg") {
                try {
                    val metadata =
                        FileInputStream(File(path)).buffered().use { stream ->
                            readOpusMetadata(stream, false)
                        }
                    requireNotNull(metadata.userComments[VORBIS_COMMENT_METADATA_BLOCK_PICTURE])
                        .firstNotNullOf { block ->
                            decodeMetadataBlockPicture(block)?.takeIf {
                                imageMimeTypes.contains(it.mimeType.trimAndNormalize())
                            }
                        }
                        .data
                } catch (_: Exception) {
                    AudioFileIO.read(File(path)).tag.firstArtwork.binaryData
                }
            } else {
                AudioFileIO.read(File(path)).tag.firstArtwork.binaryData
            }
        data.contentHashCode().toLong()
    } catch (_: Exception) {
        null
    }
}

private fun loadWithLibrary(path: String?, sizeLimit: Int?, crop: Boolean): Bitmap? {
    return try {
        requireNotNull(path)
        val extension = FilenameUtils.getExtension(path).lowercase()
        val data =
            if (extension == "opus" || extension == "ogg") {
                try {
                    val metadata =
                        FileInputStream(File(path)).buffered().use { stream ->
                            readOpusMetadata(stream, false)
                        }
                    // TODO: find the "front cover" instead of using the first artwork
                    // currently not doing that to avoid OOM
                    requireNotNull(metadata.userComments[VORBIS_COMMENT_METADATA_BLOCK_PICTURE])
                        .firstNotNullOf { block ->
                            decodeMetadataBlockPicture(block)?.takeIf {
                                imageMimeTypes.contains(it.mimeType.trimAndNormalize())
                            }
                        }
                        .data
                } catch (_: Exception) {
                    AudioFileIO.read(File(path)).tag.firstArtwork.binaryData
                }
            } else {
                AudioFileIO.read(File(path)).tag.firstArtwork.binaryData
            }
        decodeBitmap(data.let(ByteBuffer::wrap).let(ImageDecoder::createSource), sizeLimit, crop)
    } catch (_: Exception) {
        null
    }
}

private fun loadWithContentResolver(
    context: Context,
    uri: Uri,
    sizeLimit: Int,
    crop: Boolean,
): Bitmap? {
    return try {
        context.contentResolver.loadThumbnail(uri, Size(sizeLimit, sizeLimit), null).let {
            // TODO: expand loadThumbnail() and crop inside decoder
            if (crop && it.width != it.height) {
                val shortestSide = min(it.width, it.height)
                val x = (it.width - shortestSide) / 2
                val y = (it.height - shortestSide) / 2
                Bitmap.createBitmap(it, x, y, shortestSide, shortestSide)
            } else {
                it
            }
        }
    } catch (_: Exception) {
        null
    }
}

private fun loadExternal(path: String?, sizeLimit: Int?, crop: Boolean): Bitmap? {
    if (path == null) return null

    val trackName = FilenameUtils.getBaseName(path)
    val directoryName = FilenameUtils.getName(FilenameUtils.getPathNoEndSeparator(path))
    val files =
        try {
            File(FilenameUtils.getPath(path)).listFiles() ?: emptyArray()
        } catch (_: Exception) {
            emptyArray()
        }
    return files
        .mapNotNull {
            val name = it.nameWithoutExtension
            val extension = it.extension
            // higher score is better
            val extensionScore =
                imageFileExtensionScores[extension.lowercase()] ?: return@mapNotNull null
            val nameScore =
                when {
                    name.equals(trackName, true) -> 999
                    name.equals(directoryName, true) -> 998
                    else -> imageFileNameScores[name.lowercase()] ?: return@mapNotNull null
                }

            it to (nameScore * 1000 + extensionScore)
        }
        .sortedByDescending { it.second }
        .firstNotNullOfOrNull { (file, _) ->
            decodeBitmap(ImageDecoder.createSource(file), sizeLimit, crop)
        }
}

private fun decodeBitmap(source: ImageDecoder.Source, sizeLimit: Int?, crop: Boolean): Bitmap? {
    return try {
        ImageDecoder.decodeBitmap(source) { decoder, info, source ->
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)

            val resizeFactor =
                sizeLimit?.toFloat()?.div(max(info.size.width, info.size.height))?.takeIf {
                    it.isFinite() && it > 0 && it < 1
                } ?: 1f
            val finalWidth =
                (resizeFactor * info.size.width)
                    .roundToIntOrZero()
                    .coerceInOrMin(1, info.size.width)
            val finalHeight =
                (resizeFactor * info.size.height)
                    .roundToIntOrZero()
                    .coerceInOrMin(1, info.size.height)

            if (resizeFactor != 1f) {
                decoder.setTargetSize(finalWidth, finalHeight)
            }
            if (crop && finalWidth != finalHeight) {
                val shortestSide = min(finalWidth, finalHeight)
                val x = (finalWidth - shortestSide) / 2
                val y = (finalHeight - shortestSide) / 2
                decoder.crop = Rect(x, y, x + shortestSide, y + shortestSide)
            }
        }
    } catch (ex: Exception) {
        Log.e("LontraMusic", "Can't decode bitmap", ex)
        null
    }
}
