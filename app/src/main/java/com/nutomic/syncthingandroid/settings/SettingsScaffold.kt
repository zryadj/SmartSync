package com.nutomic.syncthingandroid.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.nutomic.syncthingandroid.R
import com.nutomic.syncthingandroid.util.isTelevision

/**
 * Smartisan settings chrome: a compact white bar with a centered ink title,
 * a back arrow and the page content below. Sub-pages used to show a large
 * collapsing M3 title, which broke the centered-title rule used everywhere else.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScaffold(
    title: String,
    @Suppress("UNUSED_PARAMETER") description: String? = null,
    content: (LazyListScope.() -> Unit) = {},
) {
    val configuration = LocalConfiguration.current
    val navigator = LocalSettingsNavigator.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        // Smartisan: let the striped window canvas show through.
        containerColor = Color.Transparent,
        topBar = {
            if (configuration.isTelevision) {
                // Use normal top app bar because of low vertical space
                TopAppBar(
                    title = { Text(title) }
                    // TVs remotes have dedicated back buttons,
                    // so material guidelines suggest to not show the back button
                )
            } else {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = title,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { navigator.navigateBack() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(id = R.string.back)
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        content = { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                content = content,
            )
        },
    )
}
