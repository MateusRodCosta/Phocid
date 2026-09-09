package com.mateusrodcosta.apps.lontramusic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.mateusrodcosta.apps.lontramusic.data.ArtworkColorPreference
import com.mateusrodcosta.apps.lontramusic.data.ArtworkModel
import com.mateusrodcosta.apps.lontramusic.data.getArtworkColor
import com.mateusrodcosta.apps.lontramusic.ui.theme.LocalDarkTheme

@Immutable
sealed class Artwork {
    abstract fun getColor(artworkColorPreference: ArtworkColorPreference): Color

    @Immutable
    data class Track(val track: com.mateusrodcosta.apps.lontramusic.data.Track) : Artwork() {
        override fun getColor(artworkColorPreference: ArtworkColorPreference): Color {
            return track.getArtworkColor(artworkColorPreference)
        }
    }

    @Immutable
    data class Icon(val icon: ImageVector, val color: Color) : Artwork() {
        override fun getColor(artworkColorPreference: ArtworkColorPreference): Color {
            return color
        }
    }
}

@Composable
fun ArtworkImage(
    artwork: Artwork,
    artworkColorPreference: ArtworkColorPreference,
    shape: Shape,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    sticky: Boolean = false,
) {
    val darkTheme = LocalDarkTheme.current
    val icon =
        when (artwork) {
            is Artwork.Track -> Icons.Outlined.MusicNote
            is Artwork.Icon -> artwork.icon
        }

    Box(modifier = modifier.clip(shape)) {
        val color =
            remember(artwork, artworkColorPreference) {
                artwork.getColor(artworkColorPreference)
            }

        val backgroundColor =
            if (darkTheme) lerp(color, Color.Black, 0.4f)
            else lerp(color, Color.White, 0.9f)

        if (artwork is Artwork.Track && artwork.track.hasArtwork) {
            val artworkModel =
                remember(
                    artwork.track.artworkHash,
                    artwork.track.artworkSourcePath,
                    artwork.track.artworkType,
                    artwork.track.id,
                ) {
                    ArtworkModel(
                        type = artwork.track.artworkType,
                        source = artwork.track.artworkSourcePath,
                        hash = artwork.track.artworkHash,
                        id = artwork.track.id,
                        path = artwork.track.path,
                    )
                }

            Box(modifier = Modifier.fillMaxSize().background(backgroundColor))

            AsyncImage(
                model = artworkModel,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
                placeholder = ColorPainter(backgroundColor),
                error = ColorPainter(backgroundColor),
            )
        } else {
            // No artwork: show current track's theme instantly
            Box(
                modifier = Modifier.fillMaxSize().background(backgroundColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.fillMaxSize(0.5f))
            }
        }
    }
}
