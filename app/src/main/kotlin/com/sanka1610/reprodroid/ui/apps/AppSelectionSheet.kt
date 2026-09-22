package com.sanka1610.reprodroid.ui.apps

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.sanka1610.reprodroid.R
import com.sanka1610.reprodroid.data.local.AppGroupEntity
import com.sanka1610.reprodroid.data.local.RegisteredAppRecord

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppSelectionSheet(
    selectedApps: List<RegisteredAppRecord>,
    groups: List<AppGroupEntity>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
    onApply: (Map<String, String>, String?) -> Unit,
) {
    val commonGroups = selectedApps.map { it.app.groupId }.distinct()
    var groupId by rememberSaveable { mutableStateOf(commonGroups.singleOrNull()) }
    var hasChoice by rememberSaveable { mutableStateOf(commonGroups.size == 1) }
    val validChoice = hasChoice && (groupId == null || groups.any { it.groupId == groupId })
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.apps_selection_actions_title, selectedApps.size), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.apps_change_group), Modifier.padding(top = 20.dp, bottom = 8.dp), style = MaterialTheme.typography.titleSmall)
            HorizontalDivider()
            Column(Modifier.selectableGroup()) {
                (listOf(null to stringResource(R.string.group_ungrouped)) + groups.map { it.groupId to it.displayName }).forEach { (id, name) ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(
                            selected = hasChoice && groupId == id,
                            enabled = !busy, role = Role.RadioButton,
                            onClick = { groupId = id; hasChoice = true },
                        ), verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = hasChoice && groupId == id, onClick = null, enabled = !busy)
                        Text(name, Modifier.padding(start = 12.dp))
                    }
                }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(enabled = !busy, onClick = onClear) { Text(stringResource(R.string.apps_clear_selection)) }
                Button(enabled = !busy && validChoice && selectedApps.isNotEmpty(), onClick = {
                    onApply(selectedApps.associate { it.app.registeredAppId to it.app.updatedAt }, groupId)
                }) { Text(stringResource(R.string.action_apply)) }
            }
        }
    }
}
