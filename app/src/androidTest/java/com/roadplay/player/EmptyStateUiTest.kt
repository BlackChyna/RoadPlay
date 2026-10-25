package com.roadplay.player

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roadplay.player.data.settings.ThemeMode
import com.roadplay.player.ui.components.EmptyState
import com.roadplay.player.ui.theme.RoadPlayTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** First-launch (empty library) UI, tested in isolation so it doesn't depend on device state. */
@RunWith(AndroidJUnit4::class)
class EmptyStateUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun emptyLibraryExplainsWhatToDo() {
        compose.setContent {
            RoadPlayTheme(ThemeMode.DARK) {
                EmptyState(Icons.Rounded.VideoLibrary, "No videos yet", "Import videos from your phone to start watching.")
            }
        }
        compose.onNodeWithText("No videos yet").assertIsDisplayed()
        compose.onNodeWithText("Import videos from your phone to start watching.").assertIsDisplayed()
    }
}
