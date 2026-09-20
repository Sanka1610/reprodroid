package com.sanka1610.reprodroid.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.sanka1610.reprodroid.R
import com.sanka1610.reprodroid.data.local.GlobalSettingsEntity
import com.sanka1610.reprodroid.data.provider.MAX_PROVIDER_TOKEN_BYTES
import com.sanka1610.reprodroid.data.provider.ProviderCredentialAvailability
import com.sanka1610.reprodroid.data.provider.ProviderCredentialStatus
import com.sanka1610.reprodroid.data.provider.ProviderId
import com.sanka1610.reprodroid.ui.*
import com.sanka1610.reprodroid.ui.shared.*
import com.sanka1610.reprodroid.ui.state.ProviderAuthUiState

@Composable
internal fun ProviderSettingsScreen(settings: GlobalSettingsEntity, providerAuthState: ProviderAuthUiState, onSaveProviderToken: (ProviderId, String) -> Unit, onDeleteProviderToken: (ProviderId) -> Unit) {
    var editProvider by remember { mutableStateOf<ProviderId?>(null) }
    var deleteProvider by remember { mutableStateOf<ProviderId?>(null) }
    SettingsPage {
        ProviderCredentialSetting(
            label = stringResource(R.string.settings_github_token),
            status = providerAuthState.statuses[ProviderId.GITHUB]
                ?: ProviderCredentialStatus(ProviderId.GITHUB, ProviderCredentialAvailability.NOT_CONFIGURED),
            busy = ProviderId.GITHUB in providerAuthState.activeProviders,
            onEdit = { editProvider = ProviderId.GITHUB },
            onDelete = { deleteProvider = ProviderId.GITHUB },
        )
        SettingDivider(settings.showSettingsDividers)
        ProviderCredentialSetting(
            label = stringResource(R.string.settings_codeberg_token),
            status = providerAuthState.statuses[ProviderId.CODEBERG]
                ?: ProviderCredentialStatus(ProviderId.CODEBERG, ProviderCredentialAvailability.NOT_CONFIGURED),
            busy = ProviderId.CODEBERG in providerAuthState.activeProviders,
            onEdit = { editProvider = ProviderId.CODEBERG },
            onDelete = { deleteProvider = ProviderId.CODEBERG },
        )
    }
    editProvider?.let { provider ->
            var token by remember(provider) { mutableStateOf("") }
            val status = providerAuthState.statuses[provider]
            AlertDialog(
                onDismissRequest = {
                    token = ""
                    editProvider = null
                },
                title = {
                    Text(
                        stringResource(
                            if (provider == ProviderId.GITHUB) R.string.settings_github_token else R.string.settings_codeberg_token,
                        ),
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.provider_auth_input_body))
                        OutlinedTextField(
                            value = token,
                            onValueChange = { candidate ->
                                if (candidate.toByteArray(Charsets.UTF_8).size <= MAX_PROVIDER_TOKEN_BYTES) token = candidate
                            },
                            label = { Text(stringResource(R.string.provider_auth_token_label)) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = token.isNotEmpty() && token == token.trim() && token.none { it.code !in 0x21..0x7e } &&
                            status?.availability != ProviderCredentialAvailability.UNAVAILABLE,
                        onClick = {
                            val submitted = token
                            token = ""
                            editProvider = null
                            onSaveProviderToken(provider, submitted)
                        },
                    ) { Text(stringResource(R.string.action_save)) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        token = ""
                        editProvider = null
                    }) { Text(stringResource(R.string.action_cancel)) }
                },
            )

    }
    deleteProvider?.let { provider ->
            AlertDialog(
                onDismissRequest = { deleteProvider = null },
                title = { Text(stringResource(R.string.provider_auth_delete_title)) },
                text = {
                    Text(
                        stringResource(
                            R.string.provider_auth_delete_body,
                            stringResource(
                                if (provider == ProviderId.GITHUB) R.string.settings_github_token else R.string.settings_codeberg_token,
                            ),
                        ),
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        deleteProvider = null
                        onDeleteProviderToken(provider)
                    }) { Text(stringResource(R.string.action_delete)) }
                },
                dismissButton = {
                    TextButton(onClick = { deleteProvider = null }) { Text(stringResource(R.string.action_cancel)) }
                },
            )

    }
}
