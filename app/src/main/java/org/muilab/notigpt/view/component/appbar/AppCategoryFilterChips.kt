import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.muilab.notigpt.util.Constants.Companion.APP_CATEGORY_ALL
import org.muilab.notigpt.viewModel.DrawerViewModel


@RequiresApi(Build.VERSION_CODES.S)
@OptIn(ExperimentalMaterial3Api::class) // FilterChip is still experimental
@Composable
fun AppCategoryFilterChips(drawerViewModel: DrawerViewModel) {
    // 1. Collect state from the ViewModel, just like before.
    val availableAppCategories by drawerViewModel.availableAppCategories.collectAsState()
    val currentAppCategory by drawerViewModel.appCategory.collectAsState()

    // 2. Preserve the essential business logic to auto-reset the category.
    // This logic is independent of the UI and ensures data consistency.
    LaunchedEffect(availableAppCategories, currentAppCategory) {
        if (availableAppCategories.isNotEmpty() &&
            availableAppCategories.none { it.first == currentAppCategory }) {
            drawerViewModel.updateAppCategory(APP_CATEGORY_ALL)
        }
    }

    // 3. Render the LazyRow only if there are categories.
    if (availableAppCategories.isNotEmpty()) {
        LazyRow(
            // Add some padding and spacing for a better look
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(
                items = availableAppCategories,
                key = { (categoryName, _) -> categoryName } // Use category name as a stable key
            ) { (categoryName, count) ->

                // Determine if this chip is the selected one directly from the ViewModel state.
                // This removes the need for a local 'selectedTabIndex'.
                val isSelected = categoryName == currentAppCategory

                FilterChip(
                    selected = isSelected,
                    onClick = {
                        // The only action needed is to update the ViewModel.
                        // The UI will recompose automatically based on the new state.
                        drawerViewModel.updateAppCategory(categoryName)
                    },
                    label = {
                        Text(
                            text = "$categoryName ($count)",
                            // Replicate the text styling from your original TabRow
                            style = if (isSelected)
                                MaterialTheme.typography.bodyLarge
                            else
                                MaterialTheme.typography.bodySmall,
                            fontWeight = if (isSelected)
                                FontWeight.Bold
                            else
                                FontWeight.Normal
                            // You can also use fontStyle if you prefer the italic look:
                            // fontStyle = if (isSelected) FontStyle.Normal else FontStyle.Italic
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        // 選中時使用品牌色容器
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        // 未選中時使用低階層顏色
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    // Optional: Add a leading icon for selected chips
                    leadingIcon = if (isSelected) {
                        {
                            Icon(
                                imageVector = Icons.Default.Done,
                                contentDescription = "Selected",
                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                            )
                        }
                    } else {
                        null
                    }
                )
            }
        }
    }
}