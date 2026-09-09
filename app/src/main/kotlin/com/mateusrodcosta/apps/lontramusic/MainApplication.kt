package com.mateusrodcosta.apps.lontramusic

import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.glance.appwidget.updateAll
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
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
import okio.Path.Companion.toOkioPath

class MainApplication : Application(), SingletonImageLoader.Factory {
    private val mainScope = MainScope()
    private val defaultScope = CoroutineScope(mainScope.coroutineContext + Dispatchers.Default)
    private val ioScope = CoroutineScope(mainScope.coroutineContext + Dispatchers.IO)
    private val saveManagers = mutableListOf<SaveManager<*>>()

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val activityManager = getSystemService(ActivityManager::class.java)
        val isLowRam = activityManager?.isLowRamDevice == true
        val percent = if (isLowRam) 0.10 else 0.20

        return ImageLoader.Builder(context)
            .components {
                add(TrackFetcher.Factory())
                add(TrackKeyer())
            }
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, percent).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(100L * 1024L * 1024L)
                    .build()
            }
            .crossfade(150)
            .build()
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_MODERATE) {
            SingletonImageLoader.get(this).memoryCache?.clear()
        } else if (level >= TRIM_MEMORY_UI_HIDDEN) {
            SingletonImageLoader.get(this).memoryCache?.let { cache ->
                cache.trimToSize(cache.size / 2)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        val exitInfos =
            getSystemService(ActivityManager::class.java)
                ?.getHistoricalProcessExitReasons(packageName, 0, 1)
        exitInfos?.firstOrNull()?.let { info ->
            if (
                info.reason == ApplicationExitInfo.REASON_OTHER &&
                    info.description?.contains("MemoryLimiter:AnonSwap") == true
            ) {
                Log.w(
                    "LontraMusic",
                    "Previous exit triggered by Android 17 MemoryLimiter:AnonSwap: ${info.description}",
                )
            }
        }

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
                        loadCbor<Preferences>(context, Constants.PREFERENCES_FILE_NAME, false)
                            ?.upgrade() ?: Preferences()
                    )
                unfilteredTrackIndex =
                    MutableStateFlow(
                        loadCbor<UnfilteredTrackIndex>(
                                context,
                                Constants.TRACK_INDEX_FILE_NAME,
                                false,
                            )
                            ?.upgrade() ?: UnfilteredTrackIndex(null, emptyMap())
                    )
                playerState =
                    MutableStateFlow(
                        loadCbor<PlayerState>(
                            context,
                            Constants.PLAYER_STATE_FILE_NAME,
                            isCache = false,
                        ) ?: PlayerState()
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
                    SaveManager(
                        context,
                        ioScope,
                        preferences,
                        Constants.PREFERENCES_FILE_NAME,
                        false,
                    )
                saveManagers +=
                    SaveManager(
                        context,
                        ioScope,
                        unfilteredTrackIndex,
                        Constants.TRACK_INDEX_FILE_NAME,
                        false,
                    )
                saveManagers +=
                    SaveManager(
                        context,
                        ioScope,
                        playerState,
                        Constants.PLAYER_STATE_FILE_NAME,
                        false,
                    )

                defaultScope.launch {
                    playerState
                        .combine(defaultScope, libraryIndex) { state, _ -> state }
                        .onEach { MainAppWidget().updateAll(context) }
                        .collect()
                }

                initialized.complete(Unit)
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
