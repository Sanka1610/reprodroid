package com.sanka1610.reprodroid.ui.appdetail

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.sanka1610.reprodroid.R
import com.sanka1610.reprodroid.data.local.BuildEnvironmentManifestWithDependencies
import com.sanka1610.reprodroid.data.local.JobRecord
import com.sanka1610.reprodroid.data.local.RegisteredAppRecord
import com.sanka1610.reprodroid.data.repository.BuildManifestWarning
import com.sanka1610.reprodroid.data.repository.DependencyDifferenceKind
import com.sanka1610.reprodroid.data.repository.SourceScanWarning
import com.sanka1610.reprodroid.data.repository.compareBuildEnvironments
import com.sanka1610.reprodroid.data.repository.sandboxManifestText
import com.sanka1610.reprodroid.data.repository.sandboxSelectionText
import com.sanka1610.reprodroid.ui.*
import com.sanka1610.reprodroid.ui.shared.*

@Composable
internal fun ComparisonTechnicalContent(
    record: RegisteredAppRecord,
    runnerJobs: Map<String, JobRecord>,
    buildEnvironmentManifests: Map<String, BuildEnvironmentManifestWithDependencies>,
    buildManifestWarnings: Map<String, BuildManifestWarning>,
    sourceScanWarnings: Map<String, SourceScanWarning>,
    sandboxWarnings: Map<String, String>,
) {
    val comparison = record.currentComparison ?: return
    Column {
        TechnicalValue(stringResource(R.string.technical_expected_recipe), comparison.expectedRecipeId)
        TechnicalValue(
            stringResource(R.string.technical_dependency_pinning, "A"),
            dependencyPinningLabel(comparison.runnerDependencyPinning),
        )
        if (comparison.protocolVersion >= 2 && comparison.repeatRunnerJobId != null) {
            TechnicalValue(
                stringResource(R.string.technical_dependency_pinning, "B"),
                dependencyPinningLabel(comparison.repeatRunnerDependencyPinning),
            )
            if (comparison.runnerDependencyPinning != comparison.repeatRunnerDependencyPinning) {
                Text(
                    stringResource(R.string.technical_dependency_policy_mismatch),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        if (
            comparison.runnerDependencyPinning == "LOCKFILE_OFFLINE" ||
            comparison.repeatRunnerDependencyPinning == "LOCKFILE_OFFLINE"
        ) {
            Text(
                stringResource(R.string.technical_offline_warning),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        TechnicalValue(stringResource(R.string.technical_expected_commit), comparison.expectedCommitSha, true)
        comparison.runnerResolvedCommitSha?.let { TechnicalValue(stringResource(R.string.technical_runner_commit), it, true) }
        comparison.repeatRunnerResolvedCommitSha?.let {
            TechnicalValue(stringResource(R.string.technical_repeat_runner_commit), it, true)
        }
        if (comparison.protocolVersion >= 2) {
            val buildARecord = runnerJobs[comparison.runnerJobId]
            val buildBRecord = comparison.repeatRunnerJobId?.let(runnerJobs::get)
            val buildAJob = buildARecord?.job
            val buildBJob = buildBRecord?.job
            val buildAManifest = buildEnvironmentManifests[comparison.runnerJobId]
            val buildBManifest = comparison.repeatRunnerJobId?.let(buildEnvironmentManifests::get)
            val environmentComparison = compareBuildEnvironments(
                buildAJob,
                buildBJob,
                buildAManifest,
                buildBManifest,
            )
            Text(stringResource(R.string.technical_environment_evidence), style = MaterialTheme.typography.titleSmall)
            TechnicalValue(stringResource(R.string.technical_build_sandbox, "A"), sandboxSelectionText(buildAJob))
            TechnicalValue(stringResource(R.string.technical_build_sandbox, "B"), sandboxSelectionText(buildBJob))
            TechnicalValue(stringResource(R.string.technical_build_execution, "A"), sandboxManifestText(buildAManifest?.manifest?.sandboxJson))
            TechnicalValue(stringResource(R.string.technical_build_execution, "B"), sandboxManifestText(buildBManifest?.manifest?.sandboxJson))
            sandboxWarnings[comparison.runnerJobId]?.let { TechnicalValue(stringResource(R.string.technical_build_sandbox_warning, "A"), it) }
            comparison.repeatRunnerJobId?.let(sandboxWarnings::get)?.let { TechnicalValue(stringResource(R.string.technical_build_sandbox_warning, "B"), it) }
            Text(
                stringResource(R.string.technical_environment_evidence_body),
                style = MaterialTheme.typography.bodySmall,
            )
            SourceScanEvidence(stringResource(R.string.technical_build_source_scan, "A"), buildARecord?.sourceScan)
            SourceScanEvidence(stringResource(R.string.technical_build_source_scan, "B"), buildBRecord?.sourceScan)
            sourceScanWarnings[comparison.runnerJobId]?.let { warning ->
                TechnicalValue(stringResource(R.string.technical_build_source_scan_warning, "A"), "${warning.code}: ${warning.message}")
            }
            comparison.repeatRunnerJobId?.let(sourceScanWarnings::get)?.let { warning ->
                TechnicalValue(stringResource(R.string.technical_build_source_scan_warning, "B"), "${warning.code}: ${warning.message}")
            }
            buildAManifest?.let { evidence ->
                TechnicalValue(
                    stringResource(R.string.technical_build_environment, "A"),
                    stringResource(
                        R.string.technical_build_environment_value,
                        evidence.manifest.javaVersion,
                        evidence.manifest.javaVendor,
                        evidence.manifest.gradleVersion,
                        evidence.manifest.androidSdkApiLevel,
                        evidence.manifest.buildToolsVersion,
                        determinismSummary(
                            evidence.manifest.sourceDateEpoch,
                            evidence.manifest.noBuildCache,
                            evidence.manifest.fixedLocale,
                        ),
                    ),
                )
            }
            buildBManifest?.let { evidence ->
                TechnicalValue(
                    stringResource(R.string.technical_build_environment, "B"),
                    stringResource(
                        R.string.technical_build_environment_value,
                        evidence.manifest.javaVersion,
                        evidence.manifest.javaVendor,
                        evidence.manifest.gradleVersion,
                        evidence.manifest.androidSdkApiLevel,
                        evidence.manifest.buildToolsVersion,
                        determinismSummary(
                            evidence.manifest.sourceDateEpoch,
                            evidence.manifest.noBuildCache,
                            evidence.manifest.fixedLocale,
                        ),
                    ),
                )
            }
            buildManifestWarnings[comparison.runnerJobId]?.let { warning ->
                TechnicalValue(stringResource(R.string.technical_build_manifest_warning, "A"), "${warning.code}: ${warning.message}")
            }
            comparison.repeatRunnerJobId?.let(buildManifestWarnings::get)?.let { warning ->
                TechnicalValue(stringResource(R.string.technical_build_manifest_warning, "B"), "${warning.code}: ${warning.message}")
            }
            if (environmentComparison.comparable) {
                TechnicalValue(
                    stringResource(R.string.technical_dependency_multiset),
                    stringResource(
                        R.string.technical_dependency_multiset_value,
                        environmentComparison.sameCount,
                        environmentComparison.changedCount,
                        environmentComparison.buildAOnlyCount,
                        environmentComparison.buildBOnlyCount,
                    ),
                )
                environmentComparison.differences.asSequence()
                    .filter { it.kind != DependencyDifferenceKind.SAME }
                    .take(MAX_DEPENDENCY_DIFFERENCES_IN_UI)
                    .forEach { difference ->
                        TechnicalValue(
                            difference.fileName,
                            when (difference.kind) {
                                DependencyDifferenceKind.CHANGED -> stringResource(R.string.technical_difference_changed)
                                DependencyDifferenceKind.BUILD_A_ONLY -> stringResource(R.string.technical_difference_build_a_only)
                                DependencyDifferenceKind.BUILD_B_ONLY -> stringResource(R.string.technical_difference_build_b_only)
                                DependencyDifferenceKind.SAME -> stringResource(R.string.technical_difference_same)
                            },
                        )
                    }
            } else {
                TechnicalValue(stringResource(R.string.technical_dependency_comparison), environmentComparison.reason ?: stringResource(R.string.value_not_available))
            }
            if (
                buildAJob?.effectiveRecipeId != buildBJob?.effectiveRecipeId ||
                buildAJob?.effectiveVariantName != buildBJob?.effectiveVariantName ||
                buildAManifest?.manifest?.javaVersion != buildBManifest?.manifest?.javaVersion ||
                buildAManifest?.manifest?.sourceDateEpoch != buildBManifest?.manifest?.sourceDateEpoch ||
                buildAManifest?.manifest?.noBuildCache != buildBManifest?.manifest?.noBuildCache ||
                buildAManifest?.manifest?.fixedLocale != buildBManifest?.manifest?.fixedLocale
            ) {
                Text(
                    stringResource(R.string.technical_environment_mismatch),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        comparison.incomparableReason?.let { TechnicalValue(stringResource(R.string.storage_reason), it) }
        comparison.repeatIncomparableReason?.let { TechnicalValue(stringResource(R.string.technical_repeat_reason), it) }
        record.currentAdvancedComparisonSummaries.forEach { summary ->
            Text(
                when (summary.axis) {
                    "OFFICIAL_PRIMARY" -> stringResource(R.string.technical_advanced_official_primary)
                    "OFFICIAL_REPEAT" -> stringResource(R.string.technical_advanced_official_repeat)
                    "LOCAL_REPEATABILITY" -> stringResource(R.string.technical_advanced_local_repeatability)
                    else -> stringResource(R.string.technical_advanced_other, summary.axis)
                },
                style = MaterialTheme.typography.titleSmall,
            )
            TechnicalValue(stringResource(R.string.technical_apk_entries), "${summary.inventoryOutcome} (${summary.entryCount})")
            TechnicalValue(
                stringResource(R.string.technical_entry_changes),
                stringResource(
                    R.string.technical_entry_changes_value,
                    summary.sameCount,
                    summary.changedCount,
                    summary.addedCount,
                    summary.missingCount,
                ),
            )
            TechnicalValue(stringResource(R.string.technical_dex_structure), summary.dexStructuralOutcome)
            TechnicalValue(stringResource(R.string.technical_manifest_meaning), summary.manifestSemanticOutcome)
            TechnicalValue(stringResource(R.string.technical_resource_table_meaning), summary.resourceTableSemanticOutcome)
            TechnicalValue(stringResource(R.string.technical_semantic_differences), summary.semanticDifferenceCount.toString())
            record.currentSemanticDifferenceEvidence
                .asSequence()
                .filter { it.axis == summary.axis }
                .take(MAX_SEMANTIC_DIFFERENCES_IN_UI)
                .forEach { difference ->
                    TechnicalValue(
                        difference.component,
                        "${difference.result}: ${difference.stableKey}",
                    )
                }
            summary.reason?.let { TechnicalValue(stringResource(R.string.technical_advanced_reason), it) }
        }
    }
}
