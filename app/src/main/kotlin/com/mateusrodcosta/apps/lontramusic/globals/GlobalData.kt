package com.mateusrodcosta.apps.lontramusic.globals

import com.mateusrodcosta.apps.lontramusic.data.LibraryIndex
import com.mateusrodcosta.apps.lontramusic.data.PlayerState
import com.mateusrodcosta.apps.lontramusic.data.PlayerTransientState
import com.mateusrodcosta.apps.lontramusic.data.PlaylistManager
import com.mateusrodcosta.apps.lontramusic.data.Preferences
import com.mateusrodcosta.apps.lontramusic.data.UnfilteredTrackIndex
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * These are meant for sharing data between contexts. End consumers should not read these directly!
 *
 * Initialized and saved by [com.mateusrodcosta.apps.lontramusic.MainApplication].
 */
object GlobalData {
    val initialized = CompletableDeferred<Unit>()

    @Volatile lateinit var preferences: MutableStateFlow<Preferences>
    @Volatile lateinit var unfilteredTrackIndex: MutableStateFlow<UnfilteredTrackIndex>
    @Volatile lateinit var playerState: MutableStateFlow<PlayerState>

    val playerTransientState = MutableStateFlow(PlayerTransientState())

    @Volatile lateinit var libraryIndex: StateFlow<LibraryIndex>

    @Volatile lateinit var playlistManager: PlaylistManager
}
