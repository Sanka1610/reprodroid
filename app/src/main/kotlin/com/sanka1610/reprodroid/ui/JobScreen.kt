package com.sanka1610.reprodroid.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanka1610.reprodroid.data.local.JobRecord
import com.sanka1610.reprodroid.data.network.JobState
import com.sanka1610.reprodroid.data.network.RevisionType
import com.sanka1610.reprodroid.data.network.SimulationOutcome

@Composable
fun ReproDroidApp(viewModel: JobViewModel) {
    val jobs by viewModel.jobs.collectAsStateWithLifecycle()
    val isSubmitting by viewModel.isSubmitting.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var repositoryUrl by rememberSaveable { mutableStateOf("https://github.com/example/app.git") }
    var revisionType by rememberSaveable { mutableStateOf(RevisionType.BRANCH) }
    var revision by rememberSaveable { mutableStateOf("main") }
    var outcome by rememberSaveable { mutableStateOf(SimulationOutcome.SUCCESS) }

    LifecycleStartEffect(viewModel) {
        viewModel.startVisibleSync()
        onStopOrDispose { viewModel.stopVisibleSync() }
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("ReproDroid", style = MaterialTheme.typography.headlineMedium)
                Text("Phase 1B · persistent simulated jobs", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = repositoryUrl,
                    onValueChange = { repositoryUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Repository URL (not accessed in SIMULATED mode)") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = revision,
                    onValueChange = { revision = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Revision") },
                    singleLine = true,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    RevisionType.entries.forEach { candidate ->
                        RadioButton(
                            selected = revisionType == candidate,
                            onClick = { revisionType = candidate },
                        )
                        Text(candidate.name)
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SimulationOutcome.entries.forEach { candidate ->
                        RadioButton(
                            selected = outcome == candidate,
                            onClick = { outcome = candidate },
                        )
                        Text(candidate.name)
                    }
                    Spacer(Modifier.weight(1f))
                    Button(
                        enabled = !isSubmitting,
                        onClick = { viewModel.createJob(repositoryUrl, revisionType, revision, outcome) },
                    ) {
                        Text(if (isSubmitting) "Creating…" else "Create job")
                    }
                }

                message?.let { currentMessage ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(currentMessage, modifier = Modifier.weight(1f))
                            TextButton(onClick = viewModel::clearMessage) { Text("Dismiss") }
                        }
                    }
                }

                HorizontalDivider()
                if (jobs.isEmpty()) {
                    Text("No persisted jobs.")
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(jobs, key = { it.job.jobId }) { record ->
                            JobCard(
                                record = record,
                                onCancel = { viewModel.cancelJob(record.job.jobId) },
                                onRetry = { viewModel.retryJob(record.job.jobId) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JobCard(
    record: JobRecord,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    val job = record.job
    val state = remember(job.state) { JobState.valueOf(job.state) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(state.name, style = MaterialTheme.typography.titleMedium)
                Text("${job.progressPercent}%")
            }
            LinearProgressIndicator(
                progress = { job.progressPercent / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(job.repositoryUrl, style = MaterialTheme.typography.bodySmall)
            Text(
                "${job.revisionType.lowercase()} ${job.revisionValue} · ${job.simulationOutcome}",
                style = MaterialTheme.typography.bodySmall,
            )
            job.errorMessage?.let { error ->
                Text("${job.errorCode}: $error", color = MaterialTheme.colorScheme.error)
            }

            record.artifacts.forEach { artifact ->
                Text(
                    "APK metadata: ${artifact.fileName} · ${artifact.packageName} " +
                        "${artifact.versionName} (${artifact.sizeBytes} bytes)",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            val recentLogs = record.logs.sortedBy { it.sequence }.takeLast(6)
            if (recentLogs.isNotEmpty()) {
                Text("Logs", style = MaterialTheme.typography.labelLarge)
                recentLogs.forEach { log ->
                    Text(
                        "${log.sequence.toString().padStart(3, '0')} ${log.level} ${log.message}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!state.isTerminal) {
                    TextButton(onClick = onCancel) { Text("Cancel") }
                } else {
                    TextButton(onClick = onRetry) { Text("Retry as new job") }
                }
            }
        }
    }
}
