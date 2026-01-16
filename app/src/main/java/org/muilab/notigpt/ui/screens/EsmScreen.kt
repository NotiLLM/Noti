package org.muilab.notigpt.ui.screens

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.json.JSONArray
import org.json.JSONObject
import org.muilab.notigpt.R
import org.muilab.notigpt.domain.esm.EsmUserSnapshot
import org.muilab.notigpt.domain.esm.IRBShortSurveyV2
import org.muilab.notigpt.ui.screens.esm.EsmNotiCardLikePreview
import org.muilab.notigpt.ui.screens.esm.EsmReminderPreview
import org.muilab.notigpt.ui.viewmodel.EsmViewModel
import org.muilab.notigpt.util.time.getAbsoluteTimeStr
import org.muilab.notigpt.util.time.getRelativeTimeStr

@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun EsmScreen(esmViewModel: EsmViewModel? = null) {
    val ctx = LocalContext.current
    val vm: EsmViewModel = esmViewModel ?: viewModel()
    val available by vm.available.collectAsState()
    val active by vm.activeInstance.collectAsState()
    val currentQ by vm.currentQuestionId.collectAsState()
    val answers by vm.answers.collectAsState()
    val trail by vm.questionTrail.collectAsState()

    LaunchedEffect(Unit) { vm.refresh() }

    if (active == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.esm_short_surveys), style = MaterialTheme.typography.titleLarge)

            if (available.isEmpty()) {
                Text(stringResource(R.string.esm_no_short_surveys))
            } else {
                Text(stringResource(R.string.esm_available_count, available.size))
                available.forEach { inst ->
                    var reminderTitle by remember(inst.reminderId) { mutableStateOf<String?>(null) }
                    LaunchedEffect(inst.reminderId) {
                        reminderTitle = vm.loadReminderTitle(inst.reminderId)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                // Only UI chrome: fallback title
                                text = reminderTitle?.takeIf { it.isNotBlank() } ?: stringResource(R.string.esm_short_survey),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = stringResource(
                                    R.string.esm_expires,
                                    getAbsoluteTimeStr(inst.expiresAt),
                                    getRelativeTimeStr(inst.expiresAt, ctx)
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(onClick = { vm.openInstance(inst) }) {
                            Text(stringResource(R.string.ui_action_open))
                        }
                    }
                }
            }
        }
        return
    }

    val inst = active!!
    val qid = currentQ ?: return
    val q = IRBShortSurveyV2.questionById(qid) ?: return

    val snapshotJson by vm.activeSnapshotJson.collectAsState()
    val activeReminder by vm.activeReminder.collectAsState()
    val activeNotiPreviews by vm.activeNotiPreviews.collectAsState()
    val surveyCtx = remember(snapshotJson, activeReminder) {
        snapshotJson?.let { EsmUserSnapshot.parse(it, activeReminder) }
    }

    var answerValue by remember(qid, inst.instanceId, answers[qid]) {
        mutableStateOf(
            try {
                val saved = answers[qid]
                if (saved == null) "" else JSONObject(saved).opt("value")?.toString() ?: ""
            } catch (_: Exception) {
                ""
            }
        )
    }
    var otherText by remember(qid, inst.instanceId, answers[qid]) {
        mutableStateOf(
            try {
                val saved = answers[qid]
                if (saved == null) "" else JSONObject(saved).optString("other", "")
            } catch (_: Exception) {
                ""
            }
        )
    }
    val scroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(stringResource(R.string.esm_short_survey), style = MaterialTheme.typography.titleSmall)
            if (trail.size > 1) {
                IconButton(onClick = { vm.goBackQuestion() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.ui_action_back))
                }
            }
        }

        // introText intentionally stays as-is (Taiwan-focused content)
        Text(IRBShortSurveyV2.introText(inst.triggerType), style = MaterialTheme.typography.bodyMedium)

        // User-friendly context: show reminder + noti previews.
        when {
            snapshotJson == null -> {
                Text(
                    text = stringResource(R.string.esm_loading_context),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LaunchedEffect(inst.snapshotId) { vm.refreshActiveSnapshot() }
            }

            surveyCtx == null -> {
                Text(
                    text = stringResource(R.string.esm_cannot_show_context),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            else -> {
                Text(stringResource(R.string.esm_reminder), style = MaterialTheme.typography.titleSmall)
                EsmReminderPreview(surveyCtx.reminder)

                var notisExpanded by remember(inst.instanceId) { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { notisExpanded = !notisExpanded }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.esm_related_notifications, activeNotiPreviews.size),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Icon(
                        imageVector = if (notisExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (notisExpanded) stringResource(R.string.a11y_collapse) else stringResource(R.string.a11y_expand),
                    )
                }

                if (notisExpanded) {
                    if (activeNotiPreviews.isEmpty()) {
                        Text(
                            text = stringResource(R.string.esm_no_related_notifications),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        activeNotiPreviews.forEach { du ->
                            EsmNotiCardLikePreview(notiDisplayUnit = du)
                        }
                    }
                }
            }
        }

        // Question text + option labels intentionally NOT localized
        Text(q.text, style = MaterialTheme.typography.titleMedium)

        when (q.type) {
            IRBShortSurveyV2.QType.YES_NO,
            IRBShortSurveyV2.QType.SINGLE_CHOICE,
            IRBShortSurveyV2.QType.LIKERT_5 -> {
                q.options.forEach { opt ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = answerValue == opt.id,
                            onClick = { answerValue = opt.id }
                        )
                        Text(opt.label)
                    }
                }
                val other = q.options.firstOrNull { it.isOther }
                if (other != null && answerValue == other.id) {
                    OutlinedTextField(
                        value = otherText,
                        onValueChange = { otherText = it },
                        label = { Text(stringResource(R.string.ui_action_other)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (trail.size > 1) {
                        TextButton(onClick = { vm.goBackQuestion() }) { Text(stringResource(R.string.ui_action_back)) }
                    }
                    Button(
                        enabled = answerValue.isNotBlank(),
                        onClick = {
                            val json = JSONObject().apply {
                                put("value", if (q.type == IRBShortSurveyV2.QType.LIKERT_5) answerValue.toIntOrNull() ?: answerValue else answerValue)
                                val otherId = q.options.firstOrNull { it.isOther }?.id
                                if (otherId != null && answerValue == otherId && otherText.isNotBlank()) {
                                    put("other", otherText)
                                }
                            }.toString()
                            vm.submitAnswer(json)
                        }
                    ) { Text(stringResource(R.string.ui_action_next)) }
                }
            }

            IRBShortSurveyV2.QType.MULTI_CHOICE -> {
                // Use immutable Set state to ensure recomposition on changes.
                var selected by remember(qid, inst.instanceId, answers[qid]) {
                    val initial = mutableSetOf<String>()
                    try {
                        val saved = answers[qid]
                        if (!saved.isNullOrBlank()) {
                            val v = JSONObject(saved).opt("value")
                            if (v is JSONArray) {
                                for (i in 0 until v.length()) initial.add(v.optString(i))
                            }
                        }
                    } catch (_: Exception) {
                    }
                    mutableStateOf(initial.toSet())
                }

                val otherOptId = q.options.firstOrNull { it.isOther }?.id

                q.options.forEach { opt ->
                    val checked = selected.contains(opt.id)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selected = if (checked) selected - opt.id else selected + opt.id
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.Checkbox(
                            checked = checked,
                            onCheckedChange = { on ->
                                selected = if (on) selected + opt.id else selected - opt.id
                            }
                        )
                        Text(opt.label)
                    }
                }

                if (otherOptId != null && selected.contains(otherOptId)) {
                    OutlinedTextField(
                        value = otherText,
                        onValueChange = { otherText = it },
                        label = { Text(stringResource(R.string.ui_action_other)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (trail.size > 1) {
                        TextButton(onClick = { vm.goBackQuestion() }) { Text(stringResource(R.string.ui_action_back)) }
                    }
                    Button(
                        enabled = selected.isNotEmpty(),
                        onClick = {
                            val arr = JSONArray()
                            selected.forEach { arr.put(it) }
                            val json = JSONObject().apply {
                                put("value", arr)
                                if (otherOptId != null && selected.contains(otherOptId) && otherText.isNotBlank()) {
                                    put("other", otherText)
                                }
                            }.toString()
                            vm.submitAnswer(json)
                        }
                    ) { Text(stringResource(R.string.ui_action_next)) }
                }
            }
        }

        TextButton(onClick = { vm.closeInstance() }) { Text(stringResource(R.string.ui_action_close)) }
    }
}
