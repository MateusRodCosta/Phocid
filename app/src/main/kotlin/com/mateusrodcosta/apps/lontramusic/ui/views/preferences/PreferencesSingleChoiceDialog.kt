package com.mateusrodcosta.apps.lontramusic.ui.views.preferences

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mateusrodcosta.apps.lontramusic.Dialog
import com.mateusrodcosta.apps.lontramusic.MainViewModel
import com.mateusrodcosta.apps.lontramusic.data.Preferences
import com.mateusrodcosta.apps.lontramusic.ui.components.DialogBase
import com.mateusrodcosta.apps.lontramusic.ui.components.UtilityRadioButtonListItem

@Stable
class PreferencesSingleChoiceDialog<T>(
    val title: String,
    val options: List<Pair<T, String>>,
    val activeOption: (Preferences) -> T,
    val updatePreferences: (Preferences, T) -> Preferences,
) : Dialog() {
    @Composable
    override fun Compose(viewModel: MainViewModel) {
        val preferences by viewModel.preferences.collectAsStateWithLifecycle()
        DialogBase(title = title, onConfirmOrDismiss = { viewModel.uiManager.closeDialog() }) {
            LazyColumn {
                items(options) { (option, name) ->
                    UtilityRadioButtonListItem(
                        text = name,
                        selected = activeOption(preferences) == option,
                        onSelect = {
                            viewModel.updatePreferences {
                                updatePreferences(
                                    it,
                                    option,
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}
