package org.muilab.notigpt.view.component

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_ARCHIVE
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_DELETED
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_GENERAL
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_MAKETASK
import org.muilab.notigpt.util.Constants.Companion.NOTI_CATEGORY_TODO
import org.muilab.notigpt.viewModel.DrawerViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TabLayout(context: Context, drawerViewModel: DrawerViewModel, pagerState: PagerState) {
    val scope = rememberCoroutineScope()
    val notiCategories = drawerViewModel.notiCategories.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("") }
    var newCategoryName by remember { mutableStateOf("") }

    var selectedTabIndex by remember { mutableStateOf(0) }
    val animatedTabIndex by animateIntAsState(targetValue = selectedTabIndex)

    LaunchedEffect(Unit) {
        drawerViewModel.loadCategories()
    }

    ScrollableTabRow(
        selectedTabIndex = pagerState.currentPage,
        divider = {
            Spacer(modifier = Modifier.height(5.dp))
        },
        indicator = { tabPositions ->
            TabRowDefaults.Indicator(
                modifier = Modifier.tabIndicatorOffset(tabPositions[animatedTabIndex]),
                height = 5.dp,
                color = Color.White
            )
        },
        modifier = Modifier
            .wrapContentHeight()
            .fillMaxWidth()
    ) {
        notiCategories.value.forEachIndexed { index, notiCategory ->
            Tab(
                selected = selectedTabIndex == index,
                modifier = Modifier.height(48.dp),
                onClick = {
                    if (selectedTabIndex != index) {
                        selectedTabIndex = index
                        drawerViewModel.updateCategory(notiCategory.categoryName)
                    } else {
                        val defaultCategories = listOf(
                            NOTI_CATEGORY_GENERAL,
                            NOTI_CATEGORY_TODO,
                            NOTI_CATEGORY_ARCHIVE,
                            NOTI_CATEGORY_MAKETASK,
//                            NOTI_CATEGORY_DELETED
                        )
                        if (notiCategory.categoryName !in defaultCategories) {
                            selectedCategory = notiCategory.categoryName
                            showDeleteDialog = true
                        } else {
                            Toast.makeText(context, "Cannot delete 'All' category", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                text = {
                    Text(
                        text = notiCategory.categoryName,
                        style = if (selectedTabIndex == index)
                            MaterialTheme.typography.headlineMedium
                        else
                            MaterialTheme.typography.bodySmall
                    )
                }
            )
        }
        Tab(
            selected = selectedTabIndex == notiCategories.value.size,
            modifier = Modifier.height(48.dp),
            onClick = {
                newCategoryName = ""
                showAddDialog = true
            },
            icon = {
                Icon(imageVector = Icons.Default.Add, contentDescription = null)
            },
            text = {}
        )
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add New Category") },
            text = {
                TextField(
                    value = newCategoryName,
                    onValueChange = {
                        newCategoryName = it
                        Log.d("Category", "$it | $newCategoryName")
                    },
                    placeholder = { Text("Enter category name") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newCategoryName.isNotBlank()) {
                        scope.launch {
                            drawerViewModel.insertCategory(newCategoryName)
                        }
                        showAddDialog = false
                    } else {
                        scope.launch {
                            Toast.makeText(context, "Category name cannot be empty", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog for Deleting a Category
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { "Delete Category" },
            text = { "Are you sure you want to delete '$selectedCategory'?" },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedCategory?.let { category ->
                            scope.launch {
                                drawerViewModel.deleteCategory(selectedCategory)
                                drawerViewModel.updateCategory(NOTI_CATEGORY_GENERAL)
                                pagerState.animateScrollToPage(0)
                            }
                        }
                        showDeleteDialog = false
                    },
                ) {
                    Text("Yes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("No")
                }
            }
        )
    }
}