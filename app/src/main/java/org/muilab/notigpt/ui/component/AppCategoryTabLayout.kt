package org.muilab.notigpt.ui.component

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import org.muilab.notigpt.util.Constants.Companion.APP_CATEGORY_ALL
import org.muilab.notigpt.ui.viewmodel.DrawerViewModel

/**
 * App Category Tab Layout - Second level categorization
 * 
 * This component provides a secondary tab layout for filtering notifications
 * by app categories (IM, Media, Social, etc.) within the currently selected
 * primary category (General, Archive, etc.).
 * 
 * Features:
 * - Dynamic tab generation based on available categories
 * - Notification count display for each category
 * - Automatic sorting by notification count (descending)
 * - Smart reset to "All" when primary category changes
 * - Smooth animations and state synchronization
 */

@Composable
fun AppCategoryTabLayout(drawerViewModel: DrawerViewModel) {
    val availableAppCategories by drawerViewModel.availableAppCategories.collectAsState()
    val currentAppCategory by drawerViewModel.appCategory.collectAsState()
    var selectedTabIndex by remember { mutableStateOf(0) }
    val animatedTabIndex by animateIntAsState(targetValue = selectedTabIndex, label = "appCategoryTab")

    // Synchronize local tab index with ViewModel state
    LaunchedEffect(currentAppCategory, availableAppCategories) {
        selectedTabIndex = availableAppCategories.indexOfFirst { it.first == currentAppCategory }.coerceAtLeast(0)
    }

    // Auto-reset to "All" when current category becomes unavailable
    LaunchedEffect(availableAppCategories) {
        if (availableAppCategories.isNotEmpty() && 
            availableAppCategories.none { it.first == currentAppCategory }) {
            drawerViewModel.updateAppCategory(APP_CATEGORY_ALL)
        }
    }

    // Only render if there are categories available
    if (availableAppCategories.isNotEmpty()) {
        ScrollableTabRow(
            selectedTabIndex = selectedTabIndex,
            divider = {
                Spacer(modifier = Modifier.height(3.dp))
            },
            indicator = { tabPositions ->
                // Safety check for tab positions
                if (tabPositions.isNotEmpty() && selectedTabIndex < tabPositions.size) {
                    TabRowDefaults.Indicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        height = 3.dp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            },
            modifier = Modifier
                .wrapContentHeight()
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            availableAppCategories.forEachIndexed { index, (categoryName, count) ->
                Tab(
                    selected = selectedTabIndex == index,
                    modifier = Modifier.height(40.dp),
                    onClick = {
                        selectedTabIndex = index
                        drawerViewModel.updateAppCategory(categoryName)
                    },
                    text = {
                        Text(
                            text = "$categoryName ($count)",
                            style = if (selectedTabIndex == index)
                                MaterialTheme.typography.bodyLarge
                            else
                                MaterialTheme.typography.bodySmall,
                            fontStyle = if (selectedTabIndex == index) 
                                FontStyle.Normal 
                            else 
                                FontStyle.Italic
                        )
                    }
                )
            }
        }
    }
}