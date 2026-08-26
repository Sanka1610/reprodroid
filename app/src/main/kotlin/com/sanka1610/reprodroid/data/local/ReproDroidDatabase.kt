package com.sanka1610.reprodroid.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        JobEntity::class,
        ArtifactEntity::class,
        LogEntity::class,
        InstallAttemptEntity::class,
        RegisteredAppEntity::class,
        ReleaseSnapshotEntity::class,
        ReleaseAssetEntity::class,
        ComparisonRunEntity::class,
        ComparisonEntryEntity::class,
        AdvancedComparisonEntryEntity::class,
        ApkEntryEvidenceEntity::class,
        AdvancedComparisonSummaryEntity::class,
        SemanticDifferenceEvidenceEntity::class,
        GlobalSettingsEntity::class,
        ReleaseInstallAttemptEntity::class,
    ],
    version = 9,
    exportSchema = true,
)
abstract class ReproDroidDatabase : RoomDatabase() {
    abstract fun jobDao(): JobDao
    abstract fun managedAppDao(): ManagedAppDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE jobs ADD COLUMN resolvedCommitSha TEXT")
                db.execSQL(
                    "ALTER TABLE jobs ADD COLUMN requiresConfirmation INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL("ALTER TABLE jobs ADD COLUMN effectiveBuildRoot TEXT")
                db.execSQL("ALTER TABLE jobs ADD COLUMN effectiveBuildTasks TEXT")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE artifacts ADD COLUMN downloadStatus TEXT NOT NULL DEFAULT 'NOT_DOWNLOADED'",
                )
                db.execSQL("ALTER TABLE artifacts ADD COLUMN downloadError TEXT")
                db.execSQL("ALTER TABLE artifacts ADD COLUMN localContentPath TEXT")
                db.execSQL("ALTER TABLE artifacts ADD COLUMN downloadedSizeBytes INTEGER")
                db.execSQL("ALTER TABLE artifacts ADD COLUMN downloadedSha256 TEXT")
                db.execSQL("ALTER TABLE artifacts ADD COLUMN signingCertificateSha256 TEXT")
                db.execSQL("ALTER TABLE artifacts ADD COLUMN currentSignerSha256 TEXT")
                db.execSQL("ALTER TABLE artifacts ADD COLUMN existingInstallStatus TEXT")
                db.execSQL("ALTER TABLE artifacts ADD COLUMN installedVersionName TEXT")
                db.execSQL("ALTER TABLE artifacts ADD COLUMN installedVersionCode INTEGER")
                db.execSQL("ALTER TABLE artifacts ADD COLUMN downloadedAt TEXT")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS install_attempts (
                        attemptId TEXT NOT NULL,
                        jobId TEXT NOT NULL,
                        artifactId TEXT NOT NULL,
                        packageInstallerSessionId INTEGER,
                        status TEXT NOT NULL,
                        packageInstallerStatus INTEGER,
                        statusMessage TEXT,
                        createdAt TEXT NOT NULL,
                        updatedAt TEXT NOT NULL,
                        PRIMARY KEY(attemptId),
                        FOREIGN KEY(jobId) REFERENCES jobs(jobId) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_install_attempts_jobId ON install_attempts(jobId)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_install_attempts_artifactId ON install_attempts(artifactId)",
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS registered_apps (
                        registeredAppId TEXT NOT NULL,
                        displayName TEXT NOT NULL,
                        repositoryUrl TEXT NOT NULL,
                        canonicalRepositoryUrl TEXT NOT NULL,
                        provider TEXT NOT NULL,
                        managementMode TEXT NOT NULL,
                        releaseDiscoveryStatus TEXT NOT NULL,
                        releaseDiscoveryErrorCode TEXT,
                        releaseDiscoveryErrorMessage TEXT,
                        releaseMetadataEtag TEXT,
                        lastReleaseCheckedAt TEXT,
                        createdAt TEXT NOT NULL,
                        updatedAt TEXT NOT NULL,
                        PRIMARY KEY(registeredAppId)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_registered_apps_canonicalRepositoryUrl " +
                        "ON registered_apps(canonicalRepositoryUrl)",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS release_snapshots (
                        releaseSnapshotId TEXT NOT NULL,
                        registeredAppId TEXT NOT NULL,
                        providerReleaseId INTEGER NOT NULL,
                        tagName TEXT NOT NULL,
                        resolvedCommitSha TEXT NOT NULL,
                        releaseName TEXT NOT NULL,
                        releaseUrl TEXT NOT NULL,
                        targetCommitishRaw TEXT NOT NULL,
                        isDraft INTEGER NOT NULL,
                        isPrerelease INTEGER NOT NULL,
                        isImmutable INTEGER NOT NULL,
                        releaseCreatedAt TEXT NOT NULL,
                        publishedAt TEXT NOT NULL,
                        fetchedAt TEXT NOT NULL,
                        PRIMARY KEY(releaseSnapshotId),
                        FOREIGN KEY(registeredAppId) REFERENCES registered_apps(registeredAppId)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_release_snapshots_registeredAppId " +
                        "ON release_snapshots(registeredAppId)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_release_snapshots_registeredAppId_providerReleaseId " +
                        "ON release_snapshots(registeredAppId, providerReleaseId)",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS release_assets (
                        releaseAssetId TEXT NOT NULL,
                        releaseSnapshotId TEXT NOT NULL,
                        providerAssetId INTEGER NOT NULL,
                        assetName TEXT NOT NULL,
                        stableAssetUrl TEXT NOT NULL,
                        selectionReason TEXT NOT NULL,
                        contentType TEXT NOT NULL,
                        providerSizeBytes INTEGER NOT NULL,
                        providerDigestSha256 TEXT,
                        downloadStatus TEXT NOT NULL,
                        downloadErrorCode TEXT,
                        downloadErrorMessage TEXT,
                        localContentPath TEXT,
                        downloadedSizeBytes INTEGER,
                        computedRawSha256 TEXT,
                        responseEtag TEXT,
                        finalDownloadHost TEXT,
                        packageName TEXT,
                        versionName TEXT,
                        versionCode INTEGER,
                        signingCertificateSha256 TEXT,
                        currentSignerSha256 TEXT,
                        existingInstallStatus TEXT,
                        installedVersionName TEXT,
                        installedVersionCode INTEGER,
                        comparisonEligibility TEXT NOT NULL,
                        incomparableReason TEXT,
                        downloadedAt TEXT,
                        PRIMARY KEY(releaseAssetId),
                        FOREIGN KEY(releaseSnapshotId) REFERENCES release_snapshots(releaseSnapshotId)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_release_assets_releaseSnapshotId " +
                        "ON release_assets(releaseSnapshotId)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_release_assets_releaseSnapshotId_providerAssetId " +
                        "ON release_assets(releaseSnapshotId, providerAssetId)",
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE registered_apps ADD COLUMN releaseVariantPreference " +
                        "TEXT NOT NULL DEFAULT 'RELEASE'",
                )
                db.execSQL(
                    "ALTER TABLE registered_apps ADD COLUMN preferredAbi " +
                        "TEXT NOT NULL DEFAULT 'ARM64_V8A'",
                )
                db.execSQL(
                    "ALTER TABLE release_snapshots ADD COLUMN selectedProviderAssetId INTEGER",
                )
                db.execSQL(
                    "UPDATE release_snapshots SET selectedProviderAssetId = (" +
                        "SELECT providerAssetId FROM release_assets " +
                        "WHERE release_assets.releaseSnapshotId = release_snapshots.releaseSnapshotId " +
                        "ORDER BY (downloadedAt IS NOT NULL) DESC, downloadedAt DESC, providerAssetId DESC " +
                        "LIMIT 1" +
                        ")",
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE jobs ADD COLUMN effectiveRecipeId TEXT")
                db.execSQL("ALTER TABLE jobs ADD COLUMN effectiveVariantName TEXT")
                db.execSQL("ALTER TABLE jobs ADD COLUMN effectiveJavaMajor INTEGER")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS comparison_runs (
                        comparisonRunId TEXT NOT NULL,
                        registeredAppId TEXT NOT NULL,
                        releaseSnapshotId TEXT NOT NULL,
                        referenceAssetId TEXT NOT NULL,
                        runnerJobId TEXT NOT NULL,
                        localArtifactId TEXT,
                        expectedCommitSha TEXT NOT NULL,
                        runnerResolvedCommitSha TEXT,
                        expectedRecipeId TEXT NOT NULL,
                        runnerRecipeId TEXT,
                        expectedVariantName TEXT NOT NULL,
                        runnerVariantName TEXT,
                        status TEXT NOT NULL,
                        outcome TEXT NOT NULL,
                        incomparableReason TEXT,
                        createdAt TEXT NOT NULL,
                        updatedAt TEXT NOT NULL,
                        completedAt TEXT,
                        PRIMARY KEY(comparisonRunId),
                        FOREIGN KEY(registeredAppId) REFERENCES registered_apps(registeredAppId)
                            ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(releaseSnapshotId) REFERENCES release_snapshots(releaseSnapshotId)
                            ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(referenceAssetId) REFERENCES release_assets(releaseAssetId)
                            ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(runnerJobId) REFERENCES jobs(jobId)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_comparison_runs_registeredAppId ON comparison_runs(registeredAppId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_comparison_runs_releaseSnapshotId ON comparison_runs(releaseSnapshotId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_comparison_runs_referenceAssetId ON comparison_runs(referenceAssetId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_comparison_runs_runnerJobId ON comparison_runs(runnerJobId)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS comparison_entries (
                        comparisonRunId TEXT NOT NULL,
                        entryName TEXT NOT NULL,
                        result TEXT NOT NULL,
                        referenceSizeBytes INTEGER,
                        localSizeBytes INTEGER,
                        referenceSha256 TEXT,
                        localSha256 TEXT,
                        PRIMARY KEY(comparisonRunId, entryName),
                        FOREIGN KEY(comparisonRunId) REFERENCES comparison_runs(comparisonRunId)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_comparison_entries_comparisonRunId " +
                        "ON comparison_entries(comparisonRunId)",
                )
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE registered_apps ADD COLUMN installationSource " +
                        "TEXT NOT NULL DEFAULT 'OFFICIAL_RELEASE'",
                )
                db.execSQL(
                    "ALTER TABLE registered_apps ADD COLUMN maxApkSizeBytes " +
                        "INTEGER NOT NULL DEFAULT 536870912",
                )
                db.execSQL(
                    "ALTER TABLE registered_apps ADD COLUMN useGlobalReleaseVariant " +
                        "INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE registered_apps ADD COLUMN useGlobalPreferredAbi " +
                        "INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE registered_apps ADD COLUMN useGlobalMaxApkSize " +
                        "INTEGER NOT NULL DEFAULT 1",
                )
                db.execSQL(
                    "ALTER TABLE release_assets ADD COLUMN updateStatus " +
                        "TEXT NOT NULL DEFAULT 'NOT_EVALUATED'",
                )
                db.execSQL("ALTER TABLE release_assets ADD COLUMN updateEvaluatedAt TEXT")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS global_settings (
                        singletonId INTEGER NOT NULL,
                        themeMode TEXT NOT NULL,
                        defaultManagementMode TEXT NOT NULL,
                        defaultInstallationSource TEXT NOT NULL,
                        defaultReleaseVariantPreference TEXT NOT NULL,
                        defaultPreferredAbi TEXT NOT NULL,
                        defaultMaxApkSizeBytes INTEGER NOT NULL,
                        updatedAt TEXT NOT NULL,
                        PRIMARY KEY(singletonId)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO global_settings (
                        singletonId, themeMode, defaultManagementMode, defaultInstallationSource,
                        defaultReleaseVariantPreference, defaultPreferredAbi,
                        defaultMaxApkSizeBytes, updatedAt
                    ) VALUES (
                        1, 'DARK', 'VERIFICATION', 'OFFICIAL_RELEASE',
                        'RELEASE', 'ARM64_V8A', 536870912, '1970-01-01T00:00:00Z'
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS release_install_attempts (
                        attemptId TEXT NOT NULL,
                        registeredAppId TEXT NOT NULL,
                        releaseAssetId TEXT NOT NULL,
                        packageInstallerSessionId INTEGER,
                        status TEXT NOT NULL,
                        packageInstallerStatus INTEGER,
                        statusMessage TEXT,
                        createdAt TEXT NOT NULL,
                        updatedAt TEXT NOT NULL,
                        PRIMARY KEY(attemptId),
                        FOREIGN KEY(registeredAppId) REFERENCES registered_apps(registeredAppId)
                            ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(releaseAssetId) REFERENCES release_assets(releaseAssetId)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_release_install_attempts_registeredAppId " +
                        "ON release_install_attempts(registeredAppId)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_release_install_attempts_releaseAssetId " +
                        "ON release_install_attempts(releaseAssetId)",
                )
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE comparison_runs ADD COLUMN protocolVersion INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE comparison_runs ADD COLUMN repeatRunnerJobId TEXT")
                db.execSQL("ALTER TABLE comparison_runs ADD COLUMN repeatLocalArtifactId TEXT")
                db.execSQL("ALTER TABLE comparison_runs ADD COLUMN repeatRunnerResolvedCommitSha TEXT")
                db.execSQL("ALTER TABLE comparison_runs ADD COLUMN repeatRunnerRecipeId TEXT")
                db.execSQL("ALTER TABLE comparison_runs ADD COLUMN repeatRunnerVariantName TEXT")
                db.execSQL(
                    "ALTER TABLE comparison_runs ADD COLUMN repeatOfficialOutcome " +
                        "TEXT NOT NULL DEFAULT 'NOT_EVALUATED'",
                )
                db.execSQL(
                    "ALTER TABLE comparison_runs ADD COLUMN repeatabilityOutcome " +
                        "TEXT NOT NULL DEFAULT 'NOT_EVALUATED'",
                )
                db.execSQL("ALTER TABLE comparison_runs ADD COLUMN repeatIncomparableReason TEXT")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_comparison_runs_repeatRunnerJobId " +
                        "ON comparison_runs(repeatRunnerJobId)",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS advanced_comparison_entries (
                        comparisonRunId TEXT NOT NULL,
                        axis TEXT NOT NULL,
                        entryName TEXT NOT NULL,
                        result TEXT NOT NULL,
                        leftSizeBytes INTEGER,
                        rightSizeBytes INTEGER,
                        leftSha256 TEXT,
                        rightSha256 TEXT,
                        PRIMARY KEY(comparisonRunId, axis, entryName),
                        FOREIGN KEY(comparisonRunId) REFERENCES comparison_runs(comparisonRunId)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_advanced_comparison_entries_comparisonRunId " +
                        "ON advanced_comparison_entries(comparisonRunId)",
                )
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS apk_entry_evidence (
                        comparisonRunId TEXT NOT NULL,
                        axis TEXT NOT NULL,
                        entryName TEXT NOT NULL,
                        category TEXT NOT NULL,
                        result TEXT NOT NULL,
                        leftSizeBytes INTEGER,
                        rightSizeBytes INTEGER,
                        leftCrc32 INTEGER,
                        rightCrc32 INTEGER,
                        leftCompressionMethod INTEGER,
                        rightCompressionMethod INTEGER,
                        leftUncompressedSha256 TEXT,
                        rightUncompressedSha256 TEXT,
                        archiveMetadataChanged INTEGER NOT NULL,
                        PRIMARY KEY(comparisonRunId, axis, entryName),
                        FOREIGN KEY(comparisonRunId) REFERENCES comparison_runs(comparisonRunId)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_apk_entry_evidence_comparisonRunId " +
                        "ON apk_entry_evidence(comparisonRunId)",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS advanced_comparison_summaries (
                        comparisonRunId TEXT NOT NULL,
                        registeredAppId TEXT NOT NULL,
                        axis TEXT NOT NULL,
                        inventoryOutcome TEXT NOT NULL,
                        dexStructuralOutcome TEXT NOT NULL,
                        manifestSemanticOutcome TEXT NOT NULL,
                        resourceTableSemanticOutcome TEXT NOT NULL,
                        reason TEXT,
                        entryCount INTEGER NOT NULL,
                        sameCount INTEGER NOT NULL,
                        changedCount INTEGER NOT NULL,
                        addedCount INTEGER NOT NULL,
                        missingCount INTEGER NOT NULL,
                        semanticDifferenceCount INTEGER NOT NULL,
                        PRIMARY KEY(comparisonRunId, axis),
                        FOREIGN KEY(comparisonRunId) REFERENCES comparison_runs(comparisonRunId)
                            ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(registeredAppId) REFERENCES registered_apps(registeredAppId)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_advanced_comparison_summaries_comparisonRunId " +
                        "ON advanced_comparison_summaries(comparisonRunId)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_advanced_comparison_summaries_registeredAppId " +
                        "ON advanced_comparison_summaries(registeredAppId)",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS semantic_difference_evidence (
                        comparisonRunId TEXT NOT NULL,
                        registeredAppId TEXT NOT NULL,
                        axis TEXT NOT NULL,
                        component TEXT NOT NULL,
                        stableKey TEXT NOT NULL,
                        result TEXT NOT NULL,
                        leftSha256 TEXT,
                        rightSha256 TEXT,
                        PRIMARY KEY(comparisonRunId, axis, component, stableKey),
                        FOREIGN KEY(comparisonRunId) REFERENCES comparison_runs(comparisonRunId)
                            ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(registeredAppId) REFERENCES registered_apps(registeredAppId)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_semantic_difference_evidence_comparisonRunId " +
                        "ON semantic_difference_evidence(comparisonRunId)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_semantic_difference_evidence_registeredAppId " +
                        "ON semantic_difference_evidence(registeredAppId)",
                )
            }
        }
    }
}
