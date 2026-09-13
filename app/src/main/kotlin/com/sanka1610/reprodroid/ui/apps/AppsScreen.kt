package com.sanka1610.reprodroid.ui.apps

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sanka1610.reprodroid.R
import com.sanka1610.reprodroid.data.local.AppGroupEntity
import com.sanka1610.reprodroid.data.local.RegisteredAppRecord
import com.sanka1610.reprodroid.ui.*
import com.sanka1610.reprodroid.ui.appdetail.ManagedAppIcon
import com.sanka1610.reprodroid.ui.shared.*
@Composable
internal fun UiRAppsScreen(
    apps: List<RegisteredAppRecord>,
    groups: List<AppGroupEntity>,
    searchExpanded: Boolean = false,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit,
    onCreateGroup: (String) -> Unit,
    onRenameGroup: (String, String) -> Unit,
    onReorderGroups: (List<String>) -> Unit,
    onDeleteGroup: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedGroupId by rememberSaveable { mutableStateOf(ALL_GROUP_ID) }
    var showGroups by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(searchExpanded) {
        if (!searchExpanded) {
            query = ""
            selectedGroupId = ALL_GROUP_ID
        }
    }
    LaunchedEffect(groups, selectedGroupId) {
        if (
            selectedGroupId != ALL_GROUP_ID &&
            selectedGroupId != UNGROUPED_ID &&
            groups.none { it.groupId == selectedGroupId }
        ) {
            selectedGroupId = ALL_GROUP_ID
        }
    }
    val filtered = remember(apps, query, selectedGroupId) {
        apps.filter { record ->
            val inGroup = when (selectedGroupId) {
                ALL_GROUP_ID -> true
                UNGROUPED_ID -> record.app.groupId == null
                else -> record.app.groupId == selectedGroupId
            }
            inGroup && (
                record.app.resolvedDisplayName.contains(query, ignoreCase = true) ||
                    record.app.canonicalRepositoryUrl.contains(query, ignoreCase = true) ||
                    record.latestRelease?.selectedAsset?.packageName?.contains(query, ignoreCase = true) == true
                )
        }
    }
    Column(Modifier.fillMaxSize()) {
        AnimatedVisibility(searchExpanded) {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    placeholder = { Text(stringResource(R.string.apps_search_hint)) },
                    singleLine = true,
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .selectableGroup()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = selectedGroupId == ALL_GROUP_ID,
                        onClick = { selectedGroupId = ALL_GROUP_ID },
                        label = { Text(stringResource(R.string.group_all)) },
                    )
                    FilterChip(
                        selected = selectedGroupId == UNGROUPED_ID,
                        onClick = { selectedGroupId = UNGROUPED_ID },
                        label = { Text(stringResource(R.string.group_ungrouped)) },
                    )
                    groups.forEach { group ->
                        FilterChip(
                            selected = selectedGroupId == group.groupId,
                            onClick = { selectedGroupId = group.groupId },
                            label = { Text(group.displayName) },
                        )
                    }
                }
            }
        }
        Box(Modifier.weight(1f)) {
            if (filtered.isEmpty()) {
                EmptyState(
                    title = stringResource(if (apps.isEmpty()) R.string.apps_empty_title else R.string.apps_search_empty_title),
                    body = stringResource(if (apps.isEmpty()) R.string.apps_empty_body else R.string.apps_search_empty_body),
                    actionLabel = if (apps.isEmpty()) stringResource(R.string.action_add_app) else null,
                    onAction = onAdd,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item { Spacer(Modifier.height(4.dp)) }
                    items(filtered, key = { it.app.registeredAppId }) { record ->
                        AppListCard(record, onSelect)
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(bottom = 56.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            TextButton(onClick = { showGroups = true }) {
                Text(stringResource(R.string.action_manage_groups))
            }
        }
    }
    if (showGroups) {
        GroupManagerDialog(
            groups = groups,
            onDismiss = { showGroups = false },
            onCreate = onCreateGroup,
            onRename = onRenameGroup,
            onReorder = onReorderGroups,
            onDelete = onDeleteGroup,
        )
    }
}

@Composable
private fun AppListCard(record: RegisteredAppRecord, onSelect: (String) -> Unit) {
    val latest = record.latestRelease
    val asset = latest?.selectedAsset
    Card(
        Modifier.fillMaxWidth().clickable { onSelect(record.app.registeredAppId) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            ManagedAppIcon(record)
            Column(Modifier.weight(1f).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        record.app.resolvedDisplayName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(asset?.versionName ?: latest?.snapshot?.tagName ?: stringResource(R.string.value_unknown))
                }
                Text(
                    record.group?.displayName ?: stringResource(R.string.group_ungrouped),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusChip(record.trustLevel?.name ?: record.app.releaseDiscoveryStatus)
                    asset?.updateStatus?.let { StatusChip(it) }
                }
            }
        }
    }
}

@Composable
private fun GroupManagerDialog(
    groups: List<AppGroupEntity>,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onReorder: (List<String>) -> Unit,
    onDelete: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.groups_title)) },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= MAX_GROUP_NAME_LENGTH) name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.group_name)) },
                    singleLine = true,
                )
                Button(
                    enabled = name.isNotBlank(),
                    onClick = {
                        val editing = editingId
                        if (editing == null) onCreate(name) else onRename(editing, name)
                        name = ""
                        editingId = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(if (editingId == null) R.string.action_create_group else R.string.action_rename_group))
                }
                if (groups.isEmpty()) Text(stringResource(R.string.groups_empty))
                groups.forEachIndexed { index, group ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp)) {
                            Text(group.displayName, style = MaterialTheme.typography.titleSmall)
                            Column(Modifier.fillMaxWidth()) {
                                TextButton(
                                    enabled = index > 0,
                                    onClick = {
                                        val ids = groups.map(AppGroupEntity::groupId).toMutableList()
                                        ids[index - 1] = group.groupId
                                        ids[index] = groups[index - 1].groupId
                                        onReorder(ids)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text(stringResource(R.string.action_move_up)) }
                                TextButton(
                                    enabled = index < groups.lastIndex,
                                    onClick = {
                                        val ids = groups.map(AppGroupEntity::groupId).toMutableList()
                                        ids[index + 1] = group.groupId
                                        ids[index] = groups[index + 1].groupId
                                        onReorder(ids)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text(stringResource(R.string.action_move_down)) }
                                TextButton(
                                    onClick = { editingId = group.groupId; name = group.displayName },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(stringResource(R.string.action_edit))
                                }
                                TextButton(
                                    onClick = { deletingId = group.groupId },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(stringResource(R.string.action_remove))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_dismiss)) } },
    )
    deletingId?.let { id ->
        AlertDialog(
            onDismissRequest = { deletingId = null },
            title = { Text(stringResource(R.string.action_delete_group)) },
            text = { Text(stringResource(R.string.group_delete_explanation)) },
            confirmButton = {
                TextButton(onClick = { onDelete(id); deletingId = null }) {
                    Text(stringResource(R.string.action_delete_group))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingId = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}
