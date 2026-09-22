package com.sanka1610.reprodroid.ui.apps

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.Role
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
import com.sanka1610.reprodroid.ui.settings.*
import com.sanka1610.reprodroid.ui.shared.*
import java.time.Instant
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UiRAppsScreen(
    apps: List<RegisteredAppRecord>,
    groups: List<AppGroupEntity>,
    query: String,
    grouped: Boolean,
    selectedIds: Set<String> = emptySet(),
    refreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    onToggleSelection: (String) -> Unit = {},
    onSelect: (String) -> Unit,
    onAdd: () -> Unit,
    onManageGroups: () -> Unit,
) {
    val filtered = remember(apps, query) { filterApps(apps, query) }
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (filtered.isEmpty()) {
                LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                    item { EmptyState(
                        title = stringResource(if (apps.isEmpty()) R.string.apps_empty_title else R.string.apps_search_empty_title),
                        body = stringResource(if (apps.isEmpty()) R.string.apps_empty_body else R.string.apps_search_empty_body),
                        actionLabel = if (apps.isEmpty()) stringResource(R.string.action_add_app) else null,
                        onAction = onAdd,
                    ) }
                }
                TextButton(onClick = onManageGroups, modifier = Modifier.padding(bottom = 72.dp)) {
                    Text(stringResource(R.string.action_manage_groups))
                }
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    if (!grouped || groups.isEmpty()) {
                        items(filtered, key = { it.app.registeredAppId }) { record ->
                            AppListRow(record, record.app.registeredAppId in selectedIds, selectedIds.isNotEmpty(), onSelect, onToggleSelection)
                            HorizontalDivider()
                        }
                    } else {
                        val sections = groups.map { it.groupId to it.displayName } + (null to null)
                        sections.forEach { (id, name) ->
                            val records = filtered.filter { it.app.groupId == id || (id == null && groups.none { group -> group.groupId == it.app.groupId }) }
                            if (records.isNotEmpty()) {
                                item(key = "header:$id") {
                                    Text(name ?: stringResource(R.string.group_ungrouped), Modifier.padding(top = 16.dp, bottom = 4.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                }
                                items(records, key = { it.app.registeredAppId }) { record ->
                                    AppListRow(record, record.app.registeredAppId in selectedIds, selectedIds.isNotEmpty(), onSelect, onToggleSelection)
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                    item(key = "manage-groups") {
                        SettingsLink(stringResource(R.string.action_manage_groups), onManageGroups)
                        Spacer(Modifier.height(if (selectedIds.isEmpty()) 80.dp else 144.dp))
                    }
                }
            }
        }
    }
}

@Composable
internal fun GroupManagementScreen(
    groups: List<AppGroupEntity>,
    grouped: Boolean,
    onGroupingChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onCreate: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onReorder: (List<String>) -> Unit,
    onDelete: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    var orderedGroups by remember(groups) { mutableStateOf(groups) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    fun move(groupId: String, direction: Int): Boolean {
        val from = orderedGroups.indexOfFirst { it.groupId == groupId }
        if (from < 0) return false
        val to = (from + direction).coerceIn(0, orderedGroups.lastIndex)
        if (from == to) return false
        val next = orderedGroups.toMutableList()
        next.add(to, next.removeAt(from))
        orderedGroups = next
        onReorder(next.map(AppGroupEntity::groupId))
        return true
    }
    BackScaffoldTitle(stringResource(R.string.action_manage_groups), onBack) {
        SettingsPage {
            DropdownSetting(
                label = stringResource(R.string.apps_display_mode), value = grouped,
                options = linkedMapOf(true to stringResource(R.string.apps_display_grouped), false to stringResource(R.string.apps_display_list)),
                onSelect = onGroupingChange, compact = true,
            )
            HorizontalDivider()
            orderedGroups.forEachIndexed { index, group ->
                ReorderableGroupRow(group, index, orderedGroups.lastIndex, draggingId == group.groupId,
                    onDragging = { active -> draggingId = group.groupId.takeIf { active } },
                    onMove = { move(group.groupId, it) },
                    onEdit = { editingId = group.groupId; name = group.displayName; editing = true },
                    onDelete = { deletingId = group.groupId },
                )
                HorizontalDivider()
            }
            if (groups.isEmpty()) Text(stringResource(R.string.groups_empty), Modifier.padding(vertical = 12.dp))
            TextButton(onClick = { editingId = null; name = ""; editing = true }) { Text(stringResource(R.string.action_create_group)) }
        }
    }
    if (editing) AlertDialog(
        onDismissRequest = { editing = false },
        title = { Text(stringResource(if (editingId == null) R.string.action_create_group else R.string.action_rename_group)) },
        text = { OutlinedTextField(value = name, onValueChange = { if (it.length <= MAX_GROUP_NAME_LENGTH) name = it }, label = { Text(stringResource(R.string.group_name)) }, singleLine = true) },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = {
            val id = editingId
            if (id == null) onCreate(name) else onRename(id, name)
            editing = false
        }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = { editing = false }) { Text(stringResource(R.string.action_cancel)) } },
    )
    deletingId?.let { id ->
        AlertDialog(onDismissRequest = { deletingId = null },
            title = { Text(stringResource(R.string.action_delete_group)) },
            text = { Text(stringResource(R.string.group_delete_explanation)) },
            confirmButton = { TextButton(onClick = { onDelete(id); deletingId = null }) { Text(stringResource(R.string.action_delete_group)) } },
            dismissButton = { TextButton(onClick = { deletingId = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun AppListRow(
    record: RegisteredAppRecord, selected: Boolean, selectionMode: Boolean,
    onSelect: (String) -> Unit, onToggleSelection: (String) -> Unit,
) {
    val latest = record.latestRelease
    val asset = latest?.selectedAsset
    val author = record.displayAuthor()
    val relativeTime = relativeTime(asset?.updateEvaluatedAt ?: record.app.lastReleaseCheckedAt ?: latest?.snapshot?.lastObservedAt)
    val update = updateLabel(asset?.updateStatus)
    Row(
        Modifier.fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
            .semantics { this.selected = selected }
            .combinedClickable(
                role = if (selectionMode) Role.Checkbox else Role.Button,
                onLongClickLabel = stringResource(R.string.apps_select),
                onLongClick = { if (!selected) onToggleSelection(record.app.registeredAppId) },
                onClick = { if (selectionMode) onToggleSelection(record.app.registeredAppId) else onSelect(record.app.registeredAppId) },
            ).heightIn(min = 76.dp).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ManagedAppIcon(record, 44.dp)
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
        if (selectionMode) Checkbox(checked = selected, onCheckedChange = null)
        else Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
    Surface(
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
        color = if (dragging) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
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

internal fun filterApps(apps: List<RegisteredAppRecord>, query: String): List<RegisteredAppRecord> = apps.filter { record ->
    record.app.resolvedDisplayName.contains(query, ignoreCase = true) ||
        record.app.canonicalRepositoryUrl.contains(query, ignoreCase = true) ||
        record.latestRelease?.selectedAsset?.packageName?.contains(query, ignoreCase = true) == true
}
