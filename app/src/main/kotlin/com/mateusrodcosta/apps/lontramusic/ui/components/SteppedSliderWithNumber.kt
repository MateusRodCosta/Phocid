@file:OptIn(ExperimentalMaterial3Api::class)

package com.mateusrodcosta.apps.lontramusic.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.mateusrodcosta.apps.lontramusic.Constants
import com.mateusrodcosta.apps.lontramusic.R
import com.mateusrodcosta.apps.lontramusic.globals.Strings
import com.mateusrodcosta.apps.lontramusic.ui.theme.Typography

@Composable
fun SteppedSliderWithNumber(
    number: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    steps: Int,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    onReset: (() -> Unit)? = null,
    numberColor: Color = Color.Unspecified,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SingleLineText(
                number,
                style = Typography.headlineLarge.copy(fontFeatureSettings = Constants.TNUM),
                color = numberColor,
                modifier = Modifier.weight(1f),
            )
            if (onReset != null) {
                IconButton(onClick = onReset, enabled = enabled) {
                    Icon(Icons.AutoMirrored.Filled.Undo, Strings[R.string.commons_reset])
                }
            }
        }
        Slider(
            value,
            onValueChange,
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
            onValueChangeFinished = onValueChangeFinished,
            modifier = Modifier.fillMaxWidth(),
            track = { SliderDefaults.Track(it, drawTick = { _, _ -> }) },
        )
    }
}
