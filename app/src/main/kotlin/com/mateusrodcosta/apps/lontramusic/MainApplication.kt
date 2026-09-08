package com.mateusrodcosta.apps.lontramusic

import android.app.Application
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.glance.appwidget.updateAll
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import com.mateusrodcosta.apps.lontramusic.data.LibraryIndex
import com.mateusrodcosta.apps.lontramusic.data.PlayerState
import com.mateusrodcosta.apps.lontramusic.data.PlaylistManager
import com.mateusrodcosta.apps.lontramusic.data.Preferences
import com.mateusrodcosta.apps.lontramusic.data.SaveManager
import com.mateusrodcosta.apps.lontramusic.data.TrackFetcher
import com.mateusrodcosta.apps.lontramusic.data.TrackKeyer
import com.mateusrodcosta.apps.lontramusic.data.UnfilteredTrackIndex
import com.mateusrodcosta.apps.lontramusic.data.loadCbor
import com.mateusrodcosta.apps.lontramusic.globals.GlobalData
import com.mateusrodcosta.apps.lontramusic.globals.StringSource
import com.mateusrodcosta.apps.lontramusic.globals.Strings
import com.mateusrodcosta.apps.lontramusic.utils.combine
import com.mateusrodcosta.apps.lontramusic.utils.icuFormat
import com.mateusrodcosta.apps.lontramusic.utils.map
import java.io.File
import kotlin.system.exitProcess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class MainApplication : Application(), SingletonImageLoader.Factory {
    private val mainScope = MainScope()
    private val defaultScope = CoroutineScope(mainScope.coroutineContext + Dispatchers.Default)
    private val ioScope = CoroutineScope(mainScope.coroutineContext + Dispatchers.IO)
    private val saveManagers = mutableListOf<SaveManager<*>>()

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(TrackFetcher.Factory())
                add(TrackKeyer())
            }
            .crossfade(150)
            .build()
    }

    override fun onCreate() {
        super.onCreate()

        Strings =
            object : StringSource {
                override fun get(id: Int): String {
                    return getString(id)
                }
            }

        Thread.setDefaultUncaughtExceptionHandler(::onUncaughtException)

        with(GlobalData) {
            val context = this@MainApplication
            ioScope.launch {
                preferences =
                    MutableStateFlow(
                        loadCbor<Preferences>(context, Constants.PREFERENCES_FILE_NAME, false)?.upgrade()
                            ?: Preferences()
                    )
                unfilteredTrackIndex =
                    MutableStateFlow(
                        loadCbor<UnfilteredTrackIndex>(context, Constants.TRACK_INDEX_FILE_NAME, false)
                            ?.upgrade() ?: UnfilteredTrackIndex(null, emptyMap())
                    )
                playerState =
                    MutableStateFlow(
                        loadCbor<PlayerState>(context, Constants.PLAYER_STATE_FILE_NAME, isCache = false)
                            ?: PlayerState()
                    )

                // LibraryIndex() is expensive, so extracting only the relevant
                // preferences first would avoid unnecessary computation
                libraryIndex =
                    unfilteredTrackIndex.combine(
                        defaultScope,
                        preferences.map(defaultScope) {
                            object {
                                val collator = it.sortCollator
                                val blacklist = it.blacklistRegexes
                                val whitelist = it.whitelistRegexes
                            }
                        },
                    ) { trackIndex, tuple ->
                        LibraryIndex(trackIndex, tuple.collator, tuple.blacklist, tuple.whitelist)
                    }

                playlistManager = PlaylistManager(context, defaultScope, preferences, libraryIndex)
                playlistManager.initialize()

                saveManagers +=
                        SaveManager(context, ioScope, preferences, Constants.PREFERENCES_FILE_NAME, false)
                saveManagers +=
                        SaveManager(
                            context,
                            ioScope,
                            unfilteredTrackIndex,
                            Constants.TRACK_INDEX_FILE_NAME,
                            false,
                        )
                saveManagers +=
                        SaveManager(context, ioScope, playerState, Constants.PLAYER_STATE_FILE_NAME, false)

                defaultScope.launch {
                    playerState.onEach { MainAppWidget().updateAll(context) }.collect()
                }

                initialized.set(true)
            }
        }
    }

    private fun onUncaughtException(@Suppress("unused") thread: Thread, ex: Throwable) {
        Log.e("LontraMusic", "Uncaught exception", ex)
        val file = File(getExternalFilesDir(null), "crash.txt")

        file.bufferedWriter().use { writer ->
            writer.write(BuildConfig.VERSION_NAME)
            writer.write("\n\n")
            writer.write("API level ${Build.VERSION.SDK_INT}")
            writer.write("\n\n")
            writer.write(ex.stackTraceToString())
            writer.write("\n\n")

            try {
                Runtime.getRuntime().exec("logcat -d").inputStream.bufferedReader().use { reader ->
                    while (true) {
                        val line = reader.readLine()
                        if (line == null) break
                        writer.write(line)
                        writer.write("\n")
                    }
                }
            } catch (ex: Exception) {
                writer.write("An exception occurred reading logcat:\n")
                writer.write(ex.stackTraceToString())
            }
        }

        Toast.makeText(
                this,
                Strings[R.string.toast_crash_saved_to].icuFormat(file.path),
                Toast.LENGTH_LONG,
            )
            .show()

        exitProcess(1)
    }
}
