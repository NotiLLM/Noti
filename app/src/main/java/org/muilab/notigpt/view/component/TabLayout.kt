package org.muilab.notigpt.view.component

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
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
                modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                height = 5.dp,
                color = Color.White
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
    ) {
        notiCategories.value.forEachIndexed { index, notiCategory ->
            Tab(
                selected = pagerState.currentPage == index,
                onClick = {
                    scope.launch {
                        if (pagerState.currentPage != index) {
                            pagerState.animateScrollToPage(index)
                            drawerViewModel.updateCategory(notiCategory.categoryName)
                        } else {
                            if (notiCategory.categoryName != "All") {
                                selectedCategory = notiCategory.categoryName
                                showDeleteDialog = true
                            } else {
                                Toast.makeText(context, "Cannot delete 'All' category", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                text = { Text(notiCategory.categoryName) }
            )
        }
        Tab(
            selected = pagerState.currentPage == notiCategories.value.size,
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
                                drawerViewModel.updateCategory("All")
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