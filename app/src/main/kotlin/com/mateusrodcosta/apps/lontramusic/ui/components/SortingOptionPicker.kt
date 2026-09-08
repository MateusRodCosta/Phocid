package com.mateusrodcosta.apps.lontramusic.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mateusrodcosta.apps.lontramusic.Constants
import com.mateusrodcosta.apps.lontramusic.R
import com.mateusrodcosta.apps.lontramusic.data.SortingOption
import com.mateusrodcosta.apps.lontramusic.globals.Strings

@Composable
inline fun SortingOptionPicker(
    sortingOptions: Map<String, SortingOption>,
    activeSortingOptionId: String,
    sortAscending: Boolean,
    crossinline onSetSortingOption: (String) -> Unit,
    crossinline onSetSortAscending: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        SelectBox(
            items =
                sortingOptions.values.map { value ->
                    value.stringId?.let { Strings[it] } ?: Constants.UNKNOWN
                },
            activeIndex = sortingOptions.keys.indexOf(activeSortingOptionId),
            onSetActiveIndex = {
                onSetSortingOption(sortingOptions.keys.asIterable().elementAt(it))
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(16.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = sortAscending,
                onClick = { onSetSortAscending(true) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) {
                Text(Strings[R.string.sorting_ascending])
            }
            SegmentedButton(
                selected = !sortAscending,
                onClick = { onSetSortAscending(false) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) {
                Text(Strings[R.string.sorting_descending])
            }
        }
    }
}
