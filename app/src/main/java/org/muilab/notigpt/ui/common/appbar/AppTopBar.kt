package org.muilab.notigpt.ui.common.appbar

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import org.muilab.notigpt.R
import org.muilab.notigpt.ui.common.component.SearchBar
import org.muilab.notigpt.ui.notification.viewmodel.DrawerViewModel

/**
 * Shared top app bar. Shows the hamburger + app name + search at the home root, and a back arrow +
 * screen title on pushed destinations. Search is offered only where [showSearch] is set.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun AppTopBar(
    drawerViewModel: DrawerViewModel,
    isSearchExpanded: Boolean,
    onSearchToggled: (Boolean) -> Unit,
    onMenuClicked: () -> Unit,
    screenTitle: String? = null,
    onNavigateBack: (() -> Unit)? = null,
    showSearch: Boolean = true,
    searchQuery: String? = null,
    onSearchQueryChange: ((String) -> Unit)? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    /** Screen-specific trailing actions (e.g. "Clear all"), shown to the right of search when not searching. */
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface,
        ),
        navigationIcon = {
            if (onNavigateBack != null) {
                IconButton(
                    modifier = Modifier.minimumInteractiveComponentSize(),
                    onClick = onNavigateBack,
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.a11y_back))
                }
            } else {
                IconButton(
                    modifier = Modifier.minimumInteractiveComponentSize(),
                    onClick = onMenuClicked,
                ) {
                    Icon(painter = painterResource(id = R.drawable.menu), contentDescription = stringResource(R.string.a11y_menu))
                }
            }
        },
        title = {
            AnimatedContent(
                targetState = isSearchExpanded && showSearch,
                transitionSpec = {
                    fadeIn(animationSpec = tween(200)).togetherWith(fadeOut(animationSpec = tween(200)))
                },
                label = "Search Bar Animation"
            ) { expanded ->
                if (expanded) {
                    SearchBar(
                        drawerViewModel = drawerViewModel,
                        onSearchToggled = onSearchToggled,
                        queryStringOverride = searchQuery,
                        onQueryStringChange = onSearchQueryChange,
                    )
                } else {
                    Text(
                        text = screenTitle ?: stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
            }
        },
        actions = {
            if (showSearch && !isSearchExpanded) {
                IconButton(
                    modifier = Modifier.minimumInteractiveComponentSize(),
                    onClick = { onSearchToggled(true) }
                ) {
                    Icon(painterResource(id = R.drawable.search), contentDescription = stringResource(R.string.a11y_search))
                }
            }
            if (!isSearchExpanded) actions()
        }
    )
}
