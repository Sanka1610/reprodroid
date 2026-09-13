package com.sanka1610.reprodroid.ui.comparison

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sanka1610.reprodroid.R
import com.sanka1610.reprodroid.data.local.ComparisonRunEntity
import com.sanka1610.reprodroid.ui.*
import com.sanka1610.reprodroid.ui.shared.*
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ComparisonEvidenceScreen(
    comparison: ComparisonRunEntity,
    onBack: () -> Unit,
) {
    BackScaffoldTitle(stringResource(R.string.comparison_evidence_title), onBack) {
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                UiRDetailCard(stringResource(R.string.section_current_state)) {
                    UiRDetailValue(
                        stringResource(R.string.label_comparison_status),
                        statusLabel(comparison.status),
                    )
                    UiRDetailValue(stringResource(R.string.label_verification), statusLabel(comparison.outcome))
                    if (comparison.protocolVersion >= 2) {
                        UiRDetailValue(
                            stringResource(R.string.label_official_repeat),
                            statusLabel(comparison.repeatOfficialOutcome),
                        )
                        UiRDetailValue(
                            stringResource(R.string.label_local_repeatability),
                            statusLabel(comparison.repeatabilityOutcome),
                        )
                    }
                }
            }
            item {
                UiRDetailCard(stringResource(R.string.section_identity)) {
                    UiRDetailValue(
                        stringResource(R.string.comparison_id),
                        comparison.comparisonRunId,
                        true,
                    )
                    UiRDetailValue(
                        stringResource(R.string.label_commit),
                        comparison.expectedCommitSha,
                        true,
                    )
                    UiRDetailValue(
                        stringResource(R.string.comparison_reference_asset),
                        comparison.referenceAssetId,
                        true,
                    )
                    UiRDetailValue(
                        stringResource(R.string.comparison_runner_job),
                        comparison.runnerJobId,
                        true,
                    )
                    comparison.repeatRunnerJobId?.let {
                        UiRDetailValue(stringResource(R.string.comparison_repeat_job), it, true)
                    }
                }
            }
            comparison.incomparableReason?.let { reason ->
                item { UiRDetailCard(stringResource(R.string.comparison_reason)) { Text(reason) } }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}
