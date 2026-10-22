package io.github.nikoapps.dialer.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.nikoapps.commons.ui.ScreenTopBar
import io.github.nikoapps.dialer.R

/** Placeholder until the helper settings list (people, screen, calls, voice, helper) is built. */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        ScreenTopBar(
            title = stringResource(R.string.settings_open),
            backIcon = painterResource(R.drawable.ic_arrow_back),
            backLabel = stringResource(R.string.back),
            onBack = onBack,
        )
        Text(
            text = stringResource(R.string.settings_coming),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(24.dp),
        )
    }
}
