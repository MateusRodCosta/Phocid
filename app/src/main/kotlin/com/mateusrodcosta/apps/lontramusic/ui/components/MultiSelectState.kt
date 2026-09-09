@file:OptIn(ExperimentalFoundationApi::class)

package com.mateusrodcosta.apps.lontramusic.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.mateusrodcosta.apps.lontramusic.utils.replace
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class Selectable<T>(val value: T, val selected: Boolean)

@Immutable
class SelectableList<T>(items: List<Selectable<T>>) : List<Selectable<T>> by items {
    val selection = items.filter { it.selected }.map { it.value }
}

fun <T> List<Selectable<T>>.toSelectableList(): SelectableList<T> {
    return SelectableList(this)
}

interface MultiSelectManager {
    fun toggleSelect(index: Int)

    fun selectTo(index: Int)

    fun selectAll()

    fun selectInverse()

    fun clearSelection()
}

/**
 * Selection is invalidated upon source change.
 *
 * @param dataSource items must be unique.
 */
class MultiSelectState<T>(
    coroutineScope: CoroutineScope,
    private val dataSource: StateFlow<List<T>>,
) : AutoCloseable, MultiSelectManager {
    private val _items = MutableStateFlow(SelectableList<T>(emptyList()))
    val items = _items.asStateFlow()
    private val lastSelectionIndex = AtomicInteger(-1)
    private val syncJob = coroutineScope.launch {
        dataSource
            .onEach { source ->
                _items.update {
                    source.map { value -> Selectable(value, false) }.toSelectableList()
                }
                lastSelectionIndex.set(-1)
            }
            .collect()
    }

    override fun close() {
        syncJob.cancel()
    }

    override fun toggleSelect(index: Int) {
        _items.update { items ->
            items.replace(index) { it.copy(selected = !it.selected) }.toSelectableList()
        }
        lastSelectionIndex.set(index)
    }

    override fun selectTo(index: Int) {
        _items.update { items ->
            val lastIndex = lastSelectionIndex.get()
            if (lastIndex < 0) {
                items.replace(index) { it.copy(selected = true) }.toSelectableList()
            } else {
                val lower = min(index, lastIndex)
                val upper = max(index, lastIndex)
                items
                    .mapIndexed { index, item -> item.copy(selected = index in lower..upper) }
                    .toSelectableList()
            }
        }
        lastSelectionIndex.set(index)
    }

    override fun selectAll() {
        _items.update { items -> items.map { it.copy(selected = true) }.toSelectableList() }
    }

    override fun selectInverse() {
        var cleared = false
        _items.update { items ->
            val result = items.map { it.copy(selected = !it.selected) }.toSelectableList()
            cleared = !result.selection.isNotEmpty()
            result
        }
        if (cleared) lastSelectionIndex.set(-1)
    }

    override fun clearSelection() {
        _items.update { items -> items.map { it.copy(selected = false) }.toSelectableList() }
        lastSelectionIndex.set(-1)
    }
}

@Stable
inline fun <T> Modifier.multiSelectClickable(
    items: SelectableList<T>,
    index: Int,
    multiSelectManager: MultiSelectManager,
    haptics: HapticFeedback,
    crossinline onClick: () -> Unit,
): Modifier {
    return combinedClickable(
        onClick = {
            if (items.selection.isNotEmpty()) {
                multiSelectManager.toggleSelect(index)
            } else {
                onClick()
            }
        },
        onLongClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            if (items.selection.isNotEmpty()) {
                multiSelectManager.selectTo(index)
            } else {
                multiSelectManager.toggleSelect(index)
            }
        },
    )
}
