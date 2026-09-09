package com.mateusrodcosta.apps.lontramusic

import android.app.Application
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mateusrodcosta.apps.lontramusic.data.Lyrics
import com.mateusrodcosta.apps.lontramusic.data.PlayerManager
import com.mateusrodcosta.apps.lontramusic.data.Preferences
import com.mateusrodcosta.apps.lontramusic.data.scanTracks
import com.mateusrodcosta.apps.lontramusic.globals.GlobalData
import com.mateusrodcosta.apps.lontramusic.ui.views.library.LibraryScreenTabInfo
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.ExperimentalSerializationApi

class MainViewModel(private val application: Application) : AndroidViewModel(application) {
    private val _initialized = MutableStateFlow(false)
    val initialized = _initialized.asStateFlow()
    private val initializationStarted = AtomicBoolean(false)
    private val scanMutex = Mutex()

    lateinit var playerManager: PlayerManager
    lateinit var uiManager: UiManager

    val preferences
        get() = GlobalData.preferences.asStateFlow()

    val unfilteredTrackIndex
        get() = GlobalData.unfilteredTrackIndex.asStateFlow()

    val libraryIndex
        get() = GlobalData.libraryIndex

    val playlistManager
        get() = GlobalData.playlistManager

    val lyricsCache = AtomicReference(null as Pair<Long, Lyrics>?)
    val playlistIoDirectory = MutableStateFlow(null as Uri?)
    private val _libraryScanState = MutableStateFlow(null as Boolean?)
    /**
     * - null: not scanning
     * - true: forced (manual)
     * - false: not forced (auto)
     */
    val libraryScanState = _libraryScanState.asStateFlow()
    private val _libraryScanProgress = MutableStateFlow(null as Pair<Int, Int>?)
    val libraryScanProgress = _libraryScanProgress.asStateFlow()

    fun initialize() {
        if (!initializationStarted.getAndSet(true)) {
            viewModelScope.launch {
                GlobalData.initialized.await()
                playerManager =
                    PlayerManager(GlobalData.playerState, GlobalData.playerTransientState)
                uiManager =
                    UiManager(
                        application.applicationContext,
                        viewModelScope,
                        preferences,
                        libraryIndex,
                        playlistManager,
                    )
                playerManager.initialize(application.applicationContext)
                _initialized.update { true }
            }
        }
    }

    override fun onCleared() {
        playerManager.close()
        uiManager.close()
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun scanLibrary(force: Boolean): Job {
        return viewModelScope.launch {
            withContext(Dispatchers.IO) {
                if (scanMutex.tryLock()) {
                    Log.d("LontraMusic", "Library scan started")
                    try {
                        _libraryScanProgress.update { null }
                        _libraryScanState.update { force }

                        if (force || preferences.value.alwaysRescanMediaStore) {
                            // Try to obtain all external storage paths through hack.
                            // Result from getExternalStorageDirectory() is still kept in case the
                            // hack no longer works.
                            val storages =
                                application.applicationContext
                                    .getExternalFilesDirs(null)
                                    .mapNotNull {
                                        it?.parentFile?.parentFile?.parentFile?.parentFile?.path
                                    }
                                    .plus(Environment.getExternalStorageDirectory().path)
                                    .distinct()
                                    .toTypedArray()
                            withTimeoutOrNull(5.seconds) {
                                suspendCancellableCoroutine<Unit> { continuation ->
                                    MediaScannerConnection.scanFile(
                                        application.applicationContext,
                                        storages,
                                        arrayOf("audio/*"),
                                    ) { _, _ ->
                                        if (continuation.isActive) {
                                            continuation.resume(Unit)
                                        }
                                    }
                                }
                            }
                        }

                        val newTrackIndex =
                            scanTracks(
                                application.applicationContext,
                                preferences.value.advancedMetadataExtraction,
                                preferences.value.disableArtworkColorExtraction,
                                if (force) null else unfilteredTrackIndex.value,
                                preferences.value.artistMetadataSeparators,
                                preferences.value.artistMetadataSeparatorExceptions,
                                preferences.value.genreMetadataSeparators,
                                preferences.value.genreMetadataSeparatorExceptions,
                            ) { current, total ->
                                _libraryScanProgress.update { current to total }
                            }
                        if (newTrackIndex != null) {
                            GlobalData.unfilteredTrackIndex.update { newTrackIndex }
                            GlobalData.libraryIndex.first {
                                it.flowVersion >= newTrackIndex.flowVersion
                            }
                            Log.d("LontraMusic", "Library scan completed")
                        } else {
                            Log.d("LontraMusic", "Library scan aborted: permission denied")
                        }
                        playlistManager.syncPlaylists()
                    } finally {
                        scanMutex.unlock()
                        _libraryScanState.update { null }
                    }
                } else {
                    scanMutex.withLock {}
                }
            }
        }
    }

    fun updatePreferences(transform: (Preferences) -> Preferences) {
        GlobalData.preferences.update(transform)
    }

    fun updateTabInfo(index: Int, transform: (LibraryScreenTabInfo) -> LibraryScreenTabInfo) {
        GlobalData.preferences.update { preferences ->
            val type = preferences.tabs[index].type
            preferences.copy(
                tabSettings =
                    preferences.tabSettings.mapValues {
                        if (it.key == type) transform(it.value) else it.value
                    }
            )
        }
    }
}
