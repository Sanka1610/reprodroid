package com.sanka1610.reprodroid.ui.shared

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.sanka1610.reprodroid.R

@Composable
internal fun InformationButton(title: String, body: String) {
    var visible by rememberSaveable { mutableStateOf(false) }
    IconButton(onClick = { visible = true }) {
        Icon(Icons.Default.Info, contentDescription = stringResource(R.string.information_about, title))
    }
    if (visible) {
        AlertDialog(
            onDismissRequest = { visible = false },
            title = { Text(title) },
            text = { Text(body, Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = {
                TextButton(onClick = { visible = false }) { Text(stringResource(R.string.action_close)) }
            },
        )
    }
}
