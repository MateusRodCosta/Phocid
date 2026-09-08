package com.mateusrodcosta.apps.lontramusic.service

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.mateusrodcosta.apps.lontramusic.Constants
import com.mateusrodcosta.apps.lontramusic.data.ArtworkModel
import com.mateusrodcosta.apps.lontramusic.data.ArtworkSourceType
import com.mateusrodcosta.apps.lontramusic.data.ArtworkType
import com.mateusrodcosta.apps.lontramusic.data.resolveArtworkSource
import com.mateusrodcosta.apps.lontramusic.globals.GlobalData
import java.util.concurrent.Executors
import kotlinx.coroutines.runBlocking

@UnstableApi
class CustomizedBitmapLoader(private val context: Context) : BitmapLoader {
    private val listeningExecutorService = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())
    private val imageLoader = SingletonImageLoader.get(context)

    override fun supportsMimeType(mimeType: String): Boolean = true

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        return listeningExecutorService.submit<Bitmap> {
            runBlocking {
                val request = ImageRequest.Builder(context)
                    .data(data)
                    .size(512, 512)
                    .allowHardware(false)
                    .build()
                imageLoader.execute(request).image?.toBitmap() ?: throw Exception("Failed to decode bitmap")
            }
        }
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        return listeningExecutorService.submit<Bitmap> {
            runBlocking {
                val request = ImageRequest.Builder(context)
                    .data(uri)
                    .size(512, 512)
                    .allowHardware(false)
                    .build()
                imageLoader.execute(request).image?.toBitmap() ?: throw Exception("Failed to load bitmap from uri: $uri")
            }
        }
    }

    override fun loadBitmapFromMetadata(metadata: MediaMetadata): ListenableFuture<Bitmap>? {
        val uri = metadata.extras?.getString(Constants.URI_KEY)?.toUri() ?: return null
        val path = metadata.extras?.getString(Constants.FILE_PATH_KEY)

        return listeningExecutorService.submit<Bitmap> {
            runBlocking {
                val id = try { ContentUris.parseId(uri) } catch (_: Exception) { -1L }
                val track = if (id != -1L) GlobalData.libraryIndex.value.tracks[id] else null

                val model: Any = if (track != null && track.hasArtwork) {
                    ArtworkModel(
                        type = track.artworkType,
                        source = track.artworkSourcePath,
                        hash = track.artworkHash,
                        id = track.id,
                        path = track.path
                    )
                } else if (path != null) {
                    // Fallback discovery if index is not ready
                    val resolved = resolveArtworkSource(path, uri)
                    if (resolved != null) {
                        ArtworkModel(
                            type = when (resolved.type) {
                                ArtworkSourceType.EMBEDDED -> ArtworkType.EMBEDDED
                                ArtworkSourceType.EXTERNAL -> ArtworkType.EXTERNAL
                                ArtworkSourceType.MEDIA_STORE -> ArtworkType.MEDIA_STORE
                            },
                            source = resolved.source,
                            hash = null,
                            id = id,
                            path = path
                        )
                    } else {
                        uri
                    }
                } else {
                    uri
                }

                val request = ImageRequest.Builder(context)
                    .data(model)
                    .size(512, 512)
                    .allowHardware(false)
                    .build()
                
                imageLoader.execute(request).image?.toBitmap() ?: throw Exception("No artwork found")
            }
        }
    }
}
