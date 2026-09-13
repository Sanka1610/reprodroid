package com.sanka1610.reprodroid.ui.apps

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sanka1610.reprodroid.R
import com.sanka1610.reprodroid.data.local.AppGroupEntity
import com.sanka1610.reprodroid.data.local.RegisteredAppRecord
import com.sanka1610.reprodroid.ui.*
import com.sanka1610.reprodroid.ui.appdetail.ManagedAppIcon
import com.sanka1610.reprodroid.ui.shared.*
import java.time.Instant
import kotlin.math.abs

private const val UNGROUPED_SECTION_ID = "__ungrouped__"
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
                    if (groups.isEmpty()) {
                        item {
                            Card(
                                Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                ),
                            ) {
                                filtered.forEachIndexed { index, record ->
                                    if (index > 0) HorizontalDivider()
                                    AppListRow(record, onSelect)
                                }
                            }
                        }
                    } else {
                        val grouped = filtered.groupBy { it.app.groupId }
                        groups.forEach { group ->
                            val records = grouped[group.groupId].orEmpty()
                            if (records.isNotEmpty()) {
                                item(key = "group:${group.groupId}") {
                                    AppGroupSection(group.groupId, group.displayName, records, onSelect)
                                }
                            }
                        }
                        grouped[null]?.takeIf { it.isNotEmpty() }?.let { records ->
                            item(key = "group:$UNGROUPED_SECTION_ID") {
                                AppGroupSection(
                                    UNGROUPED_SECTION_ID,
                                    stringResource(R.string.group_other),
                                    records,
                                    onSelect,
                                )
                            }
                        }
                    }
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            TextButton(onClick = { showGroups = true }) {
                                Text(stringResource(R.string.action_manage_groups))
                            }
                        }
                    }
                    item { Spacer(Modifier.height(64.dp)) }
                }
            }
        }
        if (filtered.isEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 56.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                TextButton(onClick = { showGroups = true }) {
                    Text(stringResource(R.string.action_manage_groups))
                }
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
private fun AppListRow(record: RegisteredAppRecord, onSelect: (String) -> Unit) {
    val latest = record.latestRelease
    val asset = latest?.selectedAsset
    val author = record.app.authorDisplayOverride?.takeIf(String::isNotBlank)
        ?: repositoryOwner(record.app.canonicalRepositoryUrl)
    val relativeTime = relativeTime(asset?.updateEvaluatedAt ?: record.app.lastReleaseCheckedAt ?: latest?.snapshot?.lastObservedAt)
    val update = updateLabel(asset?.updateStatus)
    Row(
        Modifier.fillMaxWidth().clickable { onSelect(record.app.registeredAppId) }.padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ManagedAppIcon(record, 48.dp)
        Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                record.app.resolvedDisplayName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(R.string.apps_author, author),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(
                    R.string.apps_trust_and_update,
                    trustLabel(record),
                    if (relativeTime == null) update else stringResource(R.string.apps_status_time, update, relativeTime),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AppGroupSection(
    id: String,
    title: String,
    records: List<RegisteredAppRecord>,
    onSelect: (String) -> Unit,
) {
    var expanded by rememberSaveable(id) { mutableStateOf(true) }
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(records.size.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = stringResource(if (expanded) R.string.action_collapse else R.string.action_expand),
            )
        }
        AnimatedVisibility(expanded) {
            Column {
                records.forEach { record ->
                    HorizontalDivider()
                    AppListRow(record, onSelect)
                }
            }
        }
    }
}

private fun relativeTime(value: String?): String? {
    val timestamp = value?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: return null
    return DateUtils.getRelativeTimeSpanString(
        timestamp,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()
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
    var orderedGroups by remember(groups) { mutableStateOf(groups) }
    var draggingId by remember { mutableStateOf<String?>(null) }

    fun move(groupId: String, direction: Int): Boolean {
        val from = orderedGroups.indexOfFirst { it.groupId == groupId }
        val to = (from + direction).coerceIn(0, orderedGroups.lastIndex)
        if (from < 0 || from == to) return false
        val next = orderedGroups.toMutableList()
        val moved = next.removeAt(from)
        next.add(to, moved)
        orderedGroups = next
        onReorder(next.map(AppGroupEntity::groupId))
        return true
    }

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
                if (groups.isNotEmpty()) {
                    Text(
                        stringResource(R.string.groups_reorder_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                orderedGroups.forEachIndexed { index, group ->
                    ReorderableGroupRow(
                        group = group,
                        index = index,
                        lastIndex = orderedGroups.lastIndex,
                        dragging = draggingId == group.groupId,
                        onDragging = { active -> draggingId = group.groupId.takeIf { active } },
                        onMove = { direction -> move(group.groupId, direction) },
                        onEdit = { editingId = group.groupId; name = group.displayName },
                        onDelete = { deletingId = group.groupId },
                    )
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

@Composable
private fun ReorderableGroupRow(
    group: AppGroupEntity,
    index: Int,
    lastIndex: Int,
    dragging: Boolean,
    onDragging: (Boolean) -> Unit,
    onMove: (Int) -> Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val threshold = with(LocalDensity.current) { 44.dp.toPx() }
    val currentOnMove by rememberUpdatedState(onMove)
    val moveUpLabel = stringResource(R.string.action_move_up)
    val moveDownLabel = stringResource(R.string.action_move_down)
    Card(
        Modifier
            .fillMaxWidth()
            .semantics {
                customActions = buildList {
                    if (index > 0) {
                        add(CustomAccessibilityAction(moveUpLabel) {
                            currentOnMove(-1)
                        })
                    }
                    if (index < lastIndex) {
                        add(CustomAccessibilityAction(moveDownLabel) {
                            currentOnMove(1)
                        })
                    }
                }
            }
            .pointerInput(group.groupId) {
                var distance = 0f
                detectDragGesturesAfterLongPress(
                    onDragStart = { onDragging(true) },
                    onDragCancel = { onDragging(false) },
                    onDragEnd = {
                        onDragging(false)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        distance += dragAmount.y
                        if (abs(distance) >= threshold) {
                            currentOnMove(if (distance > 0f) 1 else -1)
                            distance = 0f
                        }
                    },
                )
            },
        colors = CardDefaults.cardColors(
            containerColor = if (dragging) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.groups_drag_handle))
            Text(
                group.displayName,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.action_edit))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_remove))
            }
        }
    }
}
