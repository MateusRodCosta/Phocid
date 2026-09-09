@file:OptIn(ExperimentalMaterial3Api::class)

package com.mateusrodcosta.apps.lontramusic.ui.views

import android.os.SystemClock
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mateusrodcosta.apps.lontramusic.Dialog
import com.mateusrodcosta.apps.lontramusic.MainViewModel
import com.mateusrodcosta.apps.lontramusic.R
import com.mateusrodcosta.apps.lontramusic.data.PlayerTimerSettings
import com.mateusrodcosta.apps.lontramusic.globals.Strings
import com.mateusrodcosta.apps.lontramusic.globals.format
import com.mateusrodcosta.apps.lontramusic.ui.components.DialogBase
import com.mateusrodcosta.apps.lontramusic.ui.components.SteppedSliderWithNumber
import com.mateusrodcosta.apps.lontramusic.ui.components.UtilityCheckBoxListItem
import com.mateusrodcosta.apps.lontramusic.utils.icuFormat
import kotlin.math.ceil
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Stable
class TimerDialog : Dialog() {
    @Composable
    override fun Compose(viewModel: MainViewModel) {
        val playerManager = viewModel.playerManager
        val uiManager = viewModel.uiManager
        var durationMinutes by remember {
            mutableLongStateOf(uiManager.playerTimerSettings.value.duration.inWholeMinutes)
        }
        var finishLastTrack by remember {
            mutableStateOf(uiManager.playerTimerSettings.value.finishLastTrack)
        }
        var isTimerActive by remember { mutableStateOf(playerManager.getTimerState() != null) }
        var activeTimerRemainingSeconds by remember { mutableLongStateOf(0) }
        var activeTimerFinishLastTrack by remember { mutableStateOf(true) }
        val maxDurationMinutes = 60

        LaunchedEffect(Unit) {
            while (isActive) {
                val state = playerManager.getTimerState()
                isTimerActive = state != null
                activeTimerRemainingSeconds =
                    state?.first?.let {
                        ceil((it - SystemClock.elapsedRealtime()).toFloat() / 1000).toLong()
                    } ?: 0
                activeTimerFinishLastTrack = state?.second != false
                delay(42.milliseconds) // 24 fps
            }
        }

        DialogBase(
            title = Strings[R.string.player_timer],
            onConfirm = {
                if (isTimerActive) {
                    playerManager.cancelTimer()
                    uiManager.toast(Strings[R.string.toast_timer_canceled])
                } else {
                    playerManager.setTimer(
                        PlayerTimerSettings(durationMinutes.minutes, finishLastTrack)
                    )
                    uiManager.playerTimerSettings.value =
                        PlayerTimerSettings(durationMinutes.minutes, finishLastTrack)
                    uiManager.toast(Strings[R.string.toast_timer_set].icuFormat(durationMinutes))
                }
                viewModel.uiManager.closeDialog()
            },
            confirmText =
                if (isTimerActive) Strings[R.string.player_timer_cancel]
                else Strings[R.string.player_timer_set],
            onDismiss = { viewModel.uiManager.closeDialog() },
            dismissText = Strings[R.string.commons_close],
        ) {
            Column {
                SteppedSliderWithNumber(
                    number =
                        if (isTimerActive) activeTimerRemainingSeconds.seconds.format()
                        else durationMinutes.minutes.format(),
                    value =
                        if (isTimerActive)
                            activeTimerRemainingSeconds.toFloat() / 60 / maxDurationMinutes
                        else durationMinutes.toFloat() / maxDurationMinutes,
                    onValueChange = {
                        if (!isTimerActive) {
                            durationMinutes =
                                (it * maxDurationMinutes).roundToLong().coerceAtLeast(1)
                        }
                    },
                    steps = maxDurationMinutes - 1,
                    valueRange = 0f..1f,
                    modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth(),
                    numberColor =
                        if (isTimerActive) MaterialTheme.colorScheme.primary else Color.Unspecified,
                    enabled = !isTimerActive,
                )
                UtilityCheckBoxListItem(
                    text = Strings[R.string.player_timer_finish_last_track],
                    checked = if (isTimerActive) activeTimerFinishLastTrack else finishLastTrack,
                    onCheckedChange = { finishLastTrack = it },
                    enabled = !isTimerActive,
                )
            }
        }
    }
}
