package org.muilab.notigpt.view.component.appbar

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import org.muilab.notigpt.R
import org.muilab.notigpt.view.component.SearchBar
import org.muilab.notigpt.viewModel.DrawerViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun AppTopBar(
    drawerViewModel: DrawerViewModel,
    isSearchExpanded: Boolean,
    onSearchToggled: (Boolean) -> Unit
) {

    val isSortingMode = drawerViewModel.isSortingMode.collectAsState()

    // 1. Replace the Row with TopAppBar
    TopAppBar(
        // 2. Customize colors to match your original design
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
        ),

        // 3. Use the 'title' slot for your title or search bar
        title = {
            AnimatedContent(
                targetState = isSearchExpanded,
                transitionSpec = {
                    fadeIn(animationSpec = tween(200)).togetherWith(fadeOut(animationSpec = tween(200)))
                },
                label = "Search Bar Animation"
            ) { expanded ->
                if (expanded) {
                    // Your existing SearchBar fits here perfectly
                    SearchBar(drawerViewModel, onSearchToggled)
                } else {
                    // The standard title text
                    Text(
                        text = "NotiManager",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }
        },

        // 4. Use the 'actions' slot for your IconButtons
        actions = {
            // Hide actions when search is expanded to make space
            if (!isSearchExpanded) {
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
                IconButton(
                    modifier = Modifier.minimumInteractiveComponentSize(),
                    onClick = { drawerViewModel.markAllNotisRead() }
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.mark_read),
                        contentDescription = "Mark Read"
                    )
                }
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
//                IconButton(
//                    modifier = Modifier.minimumInteractiveComponentSize(),
//                    onClick = {  }
//                ) {
//                    Icon(Icons.Default.MoreVert, contentDescription = "More Options")
//                }
            }
        }
    )
}