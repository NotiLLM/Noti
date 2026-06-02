package org.muilab.notigpt.ui.common.appbar

import androidx.compose.runtime.Composable
import org.muilab.notigpt.ui.notification.viewmodel.DrawerViewModel

// Legacy: appCategory filtering has been removed.
/**
 * Category filter chip row for the app bar area.
 *
 * This component is currently a lightweight app-bar slot. If category filtering returns to the product surface,
 * wire it through DrawerViewModel rather than storing filter state locally.
 */
@Composable
fun AppCategoryFilterChips(@Suppress("UNUSED_PARAMETER") drawerViewModel: DrawerViewModel) {
    // no-op
}