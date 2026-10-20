package com.radiojhero.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.radiojhero.app.R

/**
 * The app bar shown on top of every screen. Its background switches to a container color once
 * the screen content has been scrolled.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    scrolled: Boolean,
    onNavigateUp: (() -> Unit)? = null,
    title: @Composable () -> Unit = { Logo() },
    actions: @Composable RowScope.() -> Unit = {},
) {
    val containerColor by animateColorAsState(
        if (scrolled) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface,
        animationSpec = tween(200),
        label = "AppTopBarContainerColor",
    )

    TopAppBar(
        title = title,
        navigationIcon = {
            if (onNavigateUp != null) {
                IconButton(onClick = onNavigateUp) {
                    Icon(
                        painterResource(R.drawable.ic_baseline_arrow_back_24),
                        contentDescription = stringResource(R.string.navigate_up),
                    )
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = containerColor,
            scrolledContainerColor = containerColor,
        ),
    )
}

@Composable
private fun Logo() {
    Image(
        painterResource(R.mipmap.ic_logojhero),
        contentDescription = stringResource(R.string.app_name),
    )
}
