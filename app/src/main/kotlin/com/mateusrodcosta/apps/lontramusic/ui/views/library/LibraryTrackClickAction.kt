package com.mateusrodcosta.apps.lontramusic.ui.views.library

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.graphics.vector.ImageVector
import com.mateusrodcosta.apps.lontramusic.R
import com.mateusrodcosta.apps.lontramusic.UiManager
import com.mateusrodcosta.apps.lontramusic.data.PlayerManager
import com.mateusrodcosta.apps.lontramusic.data.Track
import com.mateusrodcosta.apps.lontramusic.globals.Strings
import com.mateusrodcosta.apps.lontramusic.utils.icuFormat
import kotlinx.serialization.Serializable

@Serializable
enum class LibraryTrackClickAction(
    val stringId: Int,
    val icon: ImageVector?,
    val invoke:
        (
            tracks: List<Track>,
            index: Int,
            playerManager: PlayerManager,
            uiManager: UiManager,
        ) -> Unit,
) {
    OPEN_MENU(R.string.preferences_library_track_click_action_open_menu, null, { _, _, _, _ -> }),
    PLAY_ALL(
        R.string.track_play_all,
        Icons.AutoMirrored.Filled.PlaylistPlay,
        { tracks, index, playerManager, uiManager -> playerManager.setTracks(tracks, index) },
    ),
    PLAY(
        R.string.track_play,
        Icons.Filled.PlayArrow,
        { tracks, index, playerManager, uiManager ->
            playerManager.setTracks(listOf(tracks[index]), 0)
        },
    ),
    PLAY_NEXT(
        R.string.track_play_next,
        Icons.Filled.ChevronRight,
        { tracks, index, playerManager, uiManager ->
            playerManager.playNext(listOf(tracks[index]))
            uiManager.toast(Strings[R.string.toast_track_queued].icuFormat(1))
        },
    ),
    ADD_TO_QUEUE(
        R.string.track_add_to_queue,
        Icons.Filled.Add,
        { tracks, index, playerManager, uiManager ->
            playerManager.addTracks(listOf(tracks[index]))
            uiManager.toast(Strings[R.string.toast_track_queued].icuFormat(1))
        },
    ),
}

inline fun LibraryTrackClickAction.invokeOrOpenMenu(
    tracks: List<Track>,
    index: Int,
    playerManager: PlayerManager,
    uiManager: UiManager,
    onOpenMenu: () -> Unit,
) {
    if (this == LibraryTrackClickAction.OPEN_MENU) onOpenMenu()
    else invoke(tracks, index, playerManager, uiManager)
}
