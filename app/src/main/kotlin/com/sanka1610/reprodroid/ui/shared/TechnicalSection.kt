package com.sanka1610.reprodroid.ui.shared

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.sanka1610.reprodroid.R

@Composable
internal fun TechnicalSection(title: String, content: @Composable () -> Unit) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button) { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                stringResource(if (expanded) R.string.action_collapse else R.string.action_expand))
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
        }
        HorizontalDivider()
    }
}

@Composable
internal fun TechnicalValue(label: String, value: String, monospace: Boolean = false) {
    Column(Modifier.fillMaxWidth()) {
        if (monospace || value.length > 48) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (value.isNotBlank()) CopyValueButton(value)
            }
            SelectionContainer {
                Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default)
            }
        } else {
            Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(0.45f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, Modifier.weight(0.55f), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
