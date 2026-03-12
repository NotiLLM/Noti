package org.muilab.notigpt.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.muilab.notigpt.R
import org.muilab.notigpt.model.features.PreferenceEntryPoint
import org.muilab.notigpt.ui.viewmodel.PreferenceViewModel
import org.muilab.notigpt.ui.viewmodel.PreferenceViewModel.BottomSheetStep

/**
 * Reusable BottomSheet for the progressive preference-learning prompts
 * used by Flows 1 (Edit), 2 (Delete), and 3 (Manual Extract).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreferenceLearningBottomSheet(
    preferenceViewModel: PreferenceViewModel,
) {
    val step by preferenceViewModel.bottomSheetStep.collectAsState()
    if (step is BottomSheetStep.Hidden) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = { preferenceViewModel.dismissBottomSheet() },
        sheetState = sheetState,
    ) {
        when (val s = step) {
            is BottomSheetStep.Scope -> ScopeContent(s.entryPoint, preferenceViewModel)
            is BottomSheetStep.Reason -> ReasonContent(s.entryPoint, preferenceViewModel)
            is BottomSheetStep.SubReason -> SubReasonContent(s.subOptions, preferenceViewModel)
            is BottomSheetStep.Syncing -> SyncingContent()
            is BottomSheetStep.Hidden -> { /* handled above */ }
        }
    }
}

@Composable
private fun ScopeContent(
    entryPoint: PreferenceEntryPoint,
    vm: PreferenceViewModel,
) {
    val titleResId = when (entryPoint) {
        PreferenceEntryPoint.EDIT -> R.string.pref_scope_title_edit
        PreferenceEntryPoint.DELETE -> R.string.pref_scope_title_delete
        PreferenceEntryPoint.MANUAL_EXTRACT -> R.string.pref_scope_title_extract
    }

    val options = listOf(
        R.string.pref_scope_just_this_one,
        R.string.pref_scope_remember,
        R.string.pref_scope_make_rule,
        R.string.pref_scope_not_now,
    )

    OptionList(
        titleResId = titleResId,
        optionResIds = options,
        onSelect = { vm.selectScope(it) },
    )
}

@Composable
private fun ReasonContent(
    entryPoint: PreferenceEntryPoint,
    vm: PreferenceViewModel,
) {
    val (titleResId, options) = when (entryPoint) {
        PreferenceEntryPoint.EDIT -> R.string.pref_reason_edit_title to listOf(
            R.string.pref_reason_edit_missing,
            R.string.pref_reason_edit_wording,
            R.string.pref_reason_edit_deadline,
            R.string.pref_reason_edit_person,
            R.string.pref_reason_edit_should_update,
            R.string.pref_reason_edit_too_much,
            R.string.pref_reason_other,
        )
        PreferenceEntryPoint.DELETE -> R.string.pref_reason_delete_title to listOf(
            R.string.pref_reason_delete_handled,
            R.string.pref_reason_delete_not_mine,
            R.string.pref_reason_delete_informational,
            R.string.pref_reason_delete_incorrect,
            R.string.pref_reason_delete_not_interested,
            R.string.pref_reason_other,
        )
        PreferenceEntryPoint.MANUAL_EXTRACT -> R.string.pref_reason_extract_title to listOf(
            R.string.pref_reason_extract_later,
            R.string.pref_reason_extract_followup,
            R.string.pref_reason_extract_urgent,
            R.string.pref_reason_extract_tracking,
            R.string.pref_reason_extract_keep,
            R.string.pref_reason_other,
        )
    }

    OptionList(titleResId = titleResId, optionResIds = options, onSelect = { vm.selectReason(it) })
}

@Composable
private fun SubReasonContent(
    subOptions: List<Int>,
    vm: PreferenceViewModel,
) {
    OptionList(
        titleResId = R.string.pref_subreason_title,
        optionResIds = subOptions,
        onSelect = { vm.selectSubReason(it) },
    )
}

@Composable
private fun SyncingContent() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.pref_syncing), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun OptionList(
    titleResId: Int,
    optionResIds: List<Int>,
    onSelect: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(titleResId), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        optionResIds.forEach { resId ->
            OutlinedButton(
                onClick = { onSelect(resId) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(resId))
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
