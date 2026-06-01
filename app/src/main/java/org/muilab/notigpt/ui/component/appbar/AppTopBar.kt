package org.muilab.notigpt.ui.component.appbar

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import org.muilab.notigpt.R
import org.muilab.notigpt.ui.component.SearchBar
import org.muilab.notigpt.ui.viewmodel.DrawerViewModel

/**
 * Top app bar for navigation title, search, and context actions.
 *
 * Keep app-bar UI decisions here while delegating search state and feature actions to the active screen or
 * ViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun AppTopBar(
    drawerViewModel: DrawerViewModel,
    isSearchExpanded: Boolean,
    onSearchToggled: (Boolean) -> Unit,
    isSettingsShown: Boolean,
    onSettingsShown: (Boolean) -> Unit,
    showNotificationActions: Boolean = true,
) {

    val isSortingMode = drawerViewModel.isSortingMode.collectAsState()

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        title = {

            if (isSettingsShown) {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.titleLarge
                )
            } else {
                AnimatedContent(
                    targetState = isSearchExpanded,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(200)).togetherWith(
                            fadeOut(
                                animationSpec = tween(
                                    200
                                )
                            )
                        )
                    },
                    label = "Search Bar Animation"
                ) { expanded ->
                    if (expanded) {
                        // Your existing SearchBar fits here perfectly
                        SearchBar(drawerViewModel, onSearchToggled)
                    } else {
                        // The standard title text
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
            }
        },
        actions = {
            // Hide actions when search is expanded to make space
            if (!isSettingsShown) {
                if (!isSearchExpanded) {

                    if (showNotificationActions) {
                        IconButton(
                            modifier = Modifier.minimumInteractiveComponentSize(),
                            onClick = { onSearchToggled(true) }
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                        IconButton(
                            modifier = Modifier.minimumInteractiveComponentSize(),
                            onClick = { drawerViewModel.deleteAllNotis() }
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.sweep),
                                contentDescription = "Sweep"
                            )
                        }

                        // Reorder / Sorting mode toggle
                        IconButton(
                            modifier = Modifier.minimumInteractiveComponentSize(),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = if (isSortingMode.value) MaterialTheme.colorScheme.primary else Color.Transparent,
                                contentColor = if (isSortingMode.value) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            ),
                            onClick = { drawerViewModel.toggleSortingMode() }
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.reorder),
                                contentDescription = "Reorder"
                            )
                        }
                    }

                    IconButton(
                        modifier = Modifier.minimumInteractiveComponentSize(),
                        onClick = { onSettingsShown(true) }
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.settings),
                            contentDescription = "Settings"
                        )
                    }
                }
            } else {
                IconButton(
                    modifier = Modifier.minimumInteractiveComponentSize(),
                    onClick = { onSettingsShown(false) }
                ) {
                    Icon(painterResource(R.drawable.close), contentDescription = "Close Settings")
                }
            }
        }
    )
}