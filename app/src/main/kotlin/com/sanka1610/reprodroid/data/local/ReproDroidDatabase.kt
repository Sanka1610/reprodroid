package com.sanka1610.reprodroid.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sanka1610.reprodroid.data.provider.GitHubRepositoryParser

@Database(
    entities = [
        JobEntity::class,
        ArtifactEntity::class,
        LogEntity::class,
        InstallAttemptEntity::class,
        RegisteredAppEntity::class,
        AppGroupEntity::class,
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
        BuildEnvironmentManifestEntity::class,
        BuildEnvironmentDependencyEntity::class,
        SourceScanEntity::class,
        SourceScanDetectorCountEntity::class,
        SourceScanFindingEntity::class,
        AppRepositoryBindingEntity::class,
        SourceDiscoveryEntity::class,
        GradleCandidateEntity::class,
        AppBuildConfigurationEntity::class,
        AppSourceHeadEntity::class,
        ResourceAvailabilityEntity::class,
        StorageReservationEntity::class,
        RetentionHoldEntity::class,
        CleanupRunEntity::class,
        CleanupItemEntity::class,
        AuditExportEntity::class,
        ToolchainInstallationReferenceEntity::class,
        ReleaseCheckSettingsEntity::class,
        AppReleaseCheckOverrideEntity::class,
        ReleaseScheduleStateEntity::class,
        ReleaseCheckRunEntity::class,
        ReleaseCandidateEntity::class,
        NotificationOutboxEntity::class,
        ProviderCooldownEntity::class,
        NotificationDedupHeaderEntity::class,
        ProviderRepresentationEntity::class,
    ],
    version = 20,
    exportSchema = true,
)
abstract class ReproDroidDatabase : RoomDatabase() {
    abstract fun jobDao(): JobDao
    abstract fun managedAppDao(): ManagedAppDao
    abstract fun storageDao(): StorageDao
    abstract fun toolchainDao(): ToolchainDao
    abstract fun releaseCheckDao(): ReleaseCheckDao

    companion object {
        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                requirePositiveProviderIds(db)
                db.execSQL("PRAGMA defer_foreign_keys = ON")
                val dependentTables = listOf(
                    "comparison_runs",
                    "release_install_attempts",
                    "comparison_entries",
                    "advanced_comparison_entries",
                    "apk_entry_evidence",
                    "advanced_comparison_summaries",
                    "semantic_difference_evidence",
                )
                dependentTables.forEach { table ->
                    db.execSQL("CREATE TEMP TABLE migration20_backup_$table AS SELECT * FROM $table")
                }
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS release_snapshots_new (
                        releaseSnapshotId TEXT NOT NULL,
                        registeredAppId TEXT NOT NULL,
                        providerReleaseId TEXT NOT NULL,
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
                        observationSha256 TEXT NOT NULL DEFAULT '',
                        lastObservedAt TEXT NOT NULL DEFAULT '',
                        selectedProviderAssetId TEXT,
                        PRIMARY KEY(releaseSnapshotId),
                        FOREIGN KEY(registeredAppId) REFERENCES registered_apps(registeredAppId)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO release_snapshots_new (
                        releaseSnapshotId, registeredAppId, providerReleaseId, tagName,
                        resolvedCommitSha, releaseName, releaseUrl, targetCommitishRaw,
                        isDraft, isPrerelease, isImmutable, releaseCreatedAt, publishedAt,
                        fetchedAt, observationSha256, lastObservedAt, selectedProviderAssetId
                    )
                    SELECT releaseSnapshotId, registeredAppId, CAST(providerReleaseId AS TEXT), tagName,
                        resolvedCommitSha, releaseName, releaseUrl, targetCommitishRaw,
                        isDraft, isPrerelease, isImmutable, releaseCreatedAt, publishedAt,
                        fetchedAt, observationSha256, lastObservedAt,
                        CASE WHEN selectedProviderAssetId IS NULL THEN NULL
                            ELSE CAST(selectedProviderAssetId AS TEXT) END
                    FROM release_snapshots
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS release_assets_new (
                        releaseAssetId TEXT NOT NULL,
                        releaseSnapshotId TEXT NOT NULL,
                        providerAssetId TEXT NOT NULL,
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
                        updateStatus TEXT NOT NULL DEFAULT 'NOT_EVALUATED',
                        updateEvaluatedAt TEXT,
                        comparisonEligibility TEXT NOT NULL,
                        incomparableReason TEXT,
                        downloadedAt TEXT,
                        PRIMARY KEY(releaseAssetId),
                        FOREIGN KEY(releaseSnapshotId) REFERENCES release_snapshots_new(releaseSnapshotId)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO release_assets_new (
                        releaseAssetId, releaseSnapshotId, providerAssetId, assetName,
                        stableAssetUrl, selectionReason, contentType, providerSizeBytes,
                        providerDigestSha256, downloadStatus, downloadErrorCode,
                        downloadErrorMessage, localContentPath, downloadedSizeBytes,
                        computedRawSha256, responseEtag, finalDownloadHost, packageName,
                        versionName, versionCode, signingCertificateSha256, currentSignerSha256,
                        existingInstallStatus, installedVersionName, installedVersionCode,
                        updateStatus, updateEvaluatedAt, comparisonEligibility,
                        incomparableReason, downloadedAt
                    )
                    SELECT releaseAssetId, releaseSnapshotId, CAST(providerAssetId AS TEXT), assetName,
                        stableAssetUrl, selectionReason, contentType, providerSizeBytes,
                        providerDigestSha256, downloadStatus, downloadErrorCode,
                        downloadErrorMessage, localContentPath, downloadedSizeBytes,
                        computedRawSha256, responseEtag, finalDownloadHost, packageName,
                        versionName, versionCode, signingCertificateSha256, currentSignerSha256,
                        existingInstallStatus, installedVersionName, installedVersionCode,
                        updateStatus, updateEvaluatedAt, comparisonEligibility,
                        incomparableReason, downloadedAt
                    FROM release_assets
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE release_assets")
                db.execSQL("DROP TABLE release_snapshots")
                db.execSQL("ALTER TABLE release_snapshots_new RENAME TO release_snapshots")
                db.execSQL("ALTER TABLE release_assets_new RENAME TO release_assets")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_release_snapshots_registeredAppId " +
                        "ON release_snapshots(registeredAppId)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_release_snapshots_registeredAppId_observationSha256 " +
                        "ON release_snapshots(registeredAppId, observationSha256)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_release_assets_releaseSnapshotId " +
                        "ON release_assets(releaseSnapshotId)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_release_assets_releaseSnapshotId_providerAssetId " +
                        "ON release_assets(releaseSnapshotId, providerAssetId)",
                )
                dependentTables.forEach { table ->
                    db.execSQL("INSERT OR REPLACE INTO $table SELECT * FROM migration20_backup_$table")
                }
                dependentTables.asReversed().forEach { table ->
                    db.execSQL("DROP TABLE migration20_backup_$table")
                }
                RELEASE_CHECK_SCHEMA_SQL.forEach(db::execSQL)
            }
        }

        private fun requirePositiveProviderIds(db: SupportSQLiteDatabase) {
            val invalid = db.query(
                """
                SELECT
                    (SELECT COUNT(*) FROM release_snapshots WHERE providerReleaseId <= 0) +
                    (SELECT COUNT(*) FROM release_snapshots
                        WHERE selectedProviderAssetId IS NOT NULL AND selectedProviderAssetId <= 0) +
                    (SELECT COUNT(*) FROM release_assets WHERE providerAssetId <= 0)
                """.trimIndent(),
            ).use { cursor ->
                cursor.moveToFirst()
                cursor.getLong(0)
            }
            check(invalid == 0L) { "Room19 contains a non-positive provider ID; migration stopped without reset." }
        }

        private val RELEASE_CHECK_SCHEMA_SQL = listOf(
            """CREATE TABLE IF NOT EXISTS release_check_settings (singletonId INTEGER NOT NULL, enabled INTEGER NOT NULL, scheduleMode TEXT NOT NULL, intervalHours INTEGER NOT NULL, dailyLocalMinute INTEGER NOT NULL, releaseChannel TEXT NOT NULL, networkPolicy TEXT NOT NULL, batteryPolicy TEXT NOT NULL, revision INTEGER NOT NULL, updatedAt TEXT NOT NULL, PRIMARY KEY(singletonId))""",
            """CREATE TABLE IF NOT EXISTS app_release_check_overrides (registeredAppId TEXT NOT NULL, enabled INTEGER, scheduleMode TEXT, intervalHours INTEGER, dailyLocalMinute INTEGER, releaseChannel TEXT, networkPolicy TEXT, batteryPolicy TEXT, notificationMuted INTEGER NOT NULL, revision INTEGER NOT NULL, updatedAt TEXT NOT NULL, PRIMARY KEY(registeredAppId), FOREIGN KEY(registeredAppId) REFERENCES registered_apps(registeredAppId) ON UPDATE NO ACTION ON DELETE CASCADE)""",
            """CREATE TABLE IF NOT EXISTS release_schedule_states (registeredAppId TEXT NOT NULL, lastAttemptAt TEXT, lastTerminalAt TEXT, nextEligibleAt TEXT NOT NULL, waitingReason TEXT NOT NULL, consecutiveRetry INTEGER NOT NULL, updatedAt TEXT NOT NULL, PRIMARY KEY(registeredAppId), FOREIGN KEY(registeredAppId) REFERENCES registered_apps(registeredAppId) ON UPDATE NO ACTION ON DELETE CASCADE)""",
            "CREATE INDEX IF NOT EXISTS index_release_schedule_states_nextEligibleAt ON release_schedule_states(nextEligibleAt)",
            "CREATE INDEX IF NOT EXISTS index_release_schedule_states_waitingReason ON release_schedule_states(waitingReason)",
            """CREATE TABLE IF NOT EXISTS release_check_runs (checkRunId TEXT NOT NULL, registeredAppId TEXT NOT NULL, trigger TEXT NOT NULL, effectiveSettingsJson TEXT NOT NULL, outcome TEXT NOT NULL, providerEvidenceJson TEXT NOT NULL, startedAt TEXT NOT NULL, finishedAt TEXT NOT NULL, PRIMARY KEY(checkRunId), FOREIGN KEY(registeredAppId) REFERENCES registered_apps(registeredAppId) ON UPDATE NO ACTION ON DELETE CASCADE)""",
            "CREATE INDEX IF NOT EXISTS index_release_check_runs_registeredAppId ON release_check_runs(registeredAppId)",
            "CREATE INDEX IF NOT EXISTS index_release_check_runs_finishedAt ON release_check_runs(finishedAt)",
            "CREATE INDEX IF NOT EXISTS index_release_check_runs_outcome ON release_check_runs(outcome)",
            """CREATE TABLE IF NOT EXISTS release_candidates (candidateId TEXT NOT NULL, registeredAppId TEXT NOT NULL, provider TEXT NOT NULL, instance TEXT NOT NULL, providerRepositoryId TEXT NOT NULL, providerReleaseId TEXT NOT NULL, tagName TEXT NOT NULL, resolvedCommitSha TEXT NOT NULL, releaseName TEXT NOT NULL, releaseUrl TEXT NOT NULL, targetCommitishRaw TEXT NOT NULL, isPrerelease INTEGER NOT NULL, isImmutable INTEGER NOT NULL, releaseCreatedAt TEXT NOT NULL, publishedAt TEXT NOT NULL, assetsJson TEXT NOT NULL, observationSha256 TEXT NOT NULL, state TEXT NOT NULL, unseen INTEGER NOT NULL, firstSeenAt TEXT NOT NULL, lastSeenAt TEXT NOT NULL, PRIMARY KEY(candidateId), FOREIGN KEY(registeredAppId) REFERENCES registered_apps(registeredAppId) ON UPDATE NO ACTION ON DELETE CASCADE)""",
            "CREATE INDEX IF NOT EXISTS index_release_candidates_registeredAppId ON release_candidates(registeredAppId)",
            "CREATE INDEX IF NOT EXISTS index_release_candidates_state ON release_candidates(state)",
            "CREATE INDEX IF NOT EXISTS index_release_candidates_lastSeenAt ON release_candidates(lastSeenAt)",
            "CREATE UNIQUE INDEX IF NOT EXISTS index_release_candidates_registeredAppId_observationSha256 ON release_candidates(registeredAppId, observationSha256)",
            """CREATE TABLE IF NOT EXISTS notification_outbox (outboxId TEXT NOT NULL, candidateId TEXT NOT NULL, registeredAppId TEXT NOT NULL, notificationType TEXT NOT NULL, notificationId INTEGER NOT NULL, state TEXT NOT NULL, createdAt TEXT NOT NULL, attemptedAt TEXT, terminalAt TEXT, errorCode TEXT, PRIMARY KEY(outboxId), FOREIGN KEY(candidateId) REFERENCES release_candidates(candidateId) ON UPDATE NO ACTION ON DELETE CASCADE)""",
            "CREATE INDEX IF NOT EXISTS index_notification_outbox_candidateId ON notification_outbox(candidateId)",
            "CREATE INDEX IF NOT EXISTS index_notification_outbox_state ON notification_outbox(state)",
            "CREATE INDEX IF NOT EXISTS index_notification_outbox_notificationId ON notification_outbox(notificationId)",
            """CREATE TABLE IF NOT EXISTS provider_cooldowns (provider TEXT NOT NULL, instance TEXT NOT NULL, reason TEXT NOT NULL, notBefore TEXT NOT NULL, rateLimitRemaining INTEGER, rateLimitResetAt TEXT, updatedAt TEXT NOT NULL, PRIMARY KEY(provider, instance))""",
            "CREATE INDEX IF NOT EXISTS index_provider_cooldowns_notBefore ON provider_cooldowns(notBefore)",
            """CREATE TABLE IF NOT EXISTS notification_dedup_headers (dedupKey TEXT NOT NULL, registeredAppId TEXT NOT NULL, provider TEXT NOT NULL, instance TEXT NOT NULL, providerRepositoryId TEXT NOT NULL, providerReleaseId TEXT NOT NULL, observationSha256 TEXT NOT NULL, notificationType TEXT NOT NULL, disposition TEXT NOT NULL, notificationId INTEGER NOT NULL, firstSeenAt TEXT NOT NULL, lastSeenAt TEXT NOT NULL, PRIMARY KEY(dedupKey))""",
            "CREATE INDEX IF NOT EXISTS index_notification_dedup_headers_registeredAppId ON notification_dedup_headers(registeredAppId)",
            "CREATE UNIQUE INDEX IF NOT EXISTS index_notification_dedup_headers_notificationId ON notification_dedup_headers(notificationId)",
            "CREATE INDEX IF NOT EXISTS index_notification_dedup_headers_lastSeenAt ON notification_dedup_headers(lastSeenAt)",
            """CREATE TABLE IF NOT EXISTS provider_representations (endpointKey TEXT NOT NULL, provider TEXT NOT NULL, instance TEXT NOT NULL, providerRepositoryId TEXT NOT NULL, etag TEXT, responseBody TEXT NOT NULL, receivedAt TEXT NOT NULL, PRIMARY KEY(endpointKey))""",
            "CREATE INDEX IF NOT EXISTS index_provider_representations_provider_instance_providerRepositoryId ON provider_representations(provider, instance, providerRepositoryId)",
        )

        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS app_groups (
                        groupId TEXT NOT NULL,
                        displayName TEXT NOT NULL,
                        sortOrder INTEGER NOT NULL,
                        createdAt TEXT NOT NULL,
                        updatedAt TEXT NOT NULL,
                        PRIMARY KEY(groupId)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_app_groups_sortOrder ON app_groups(sortOrder)",
                )
                db.execSQL("ALTER TABLE registered_apps ADD COLUMN displayNameOverride TEXT")
                db.execSQL("ALTER TABLE registered_apps ADD COLUMN authorDisplayOverride TEXT")
                db.execSQL("ALTER TABLE registered_apps ADD COLUMN note TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE registered_apps ADD COLUMN groupId TEXT")
                db.execSQL(
                    "ALTER TABLE registered_apps ADD COLUMN trackingState TEXT NOT NULL DEFAULT 'ACTIVE'",
                )
                db.execSQL("ALTER TABLE registered_apps ADD COLUMN trackingStoppedAt TEXT")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_registered_apps_groupId ON registered_apps(groupId)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_registered_apps_trackingState ON registered_apps(trackingState)",
                )
                db.execSQL(
                    "ALTER TABLE global_settings ADD COLUMN dynamicColorEnabled INTEGER NOT NULL DEFAULT 1",
                )
            }
        }

        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf(
                    "genericComparisonId TEXT",
                    "genericAttempt TEXT",
                    "genericConfigurationRevision INTEGER",
                    "genericConfigurationSha256 TEXT",
                    "genericExpectedArtifactFileName TEXT",
                    "genericRetryOfJobId TEXT",
                    "genericMemoryBytes INTEGER",
                    "genericDiscoverySha256 TEXT",
                ).forEach { db.execSQL("ALTER TABLE jobs ADD COLUMN $it") }
                listOf(
                    "genericConfigurationSha256 TEXT",
                    "genericAttempt TEXT",
                    "genericDiscoverySha256 TEXT",
                ).forEach { db.execSQL("ALTER TABLE build_environment_manifests ADD COLUMN $it") }
                listOf(
                    "runnerContract TEXT NOT NULL DEFAULT 'legacy-v1'",
                    "buildConfigurationRevision INTEGER",
                    "buildConfigurationSha256 TEXT",
                    "officialIdentitySha256 TEXT",
                    "officialApkSha256 TEXT",
                    "officialApkSizeBytes INTEGER",
                    "officialPackageName TEXT",
                    "officialVersionName TEXT",
                    "officialVersionCode INTEGER",
                    "selectedArtifactFileName TEXT",
                    "runnerComparisonId TEXT",
                    "resourceRetryOfComparisonRunId TEXT",
                    "resourceRetryCount INTEGER NOT NULL DEFAULT 0",
                ).forEach { db.execSQL("ALTER TABLE comparison_runs ADD COLUMN $it") }
            }
        }

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS toolchain_installation_references (
                        runnerId TEXT NOT NULL,
                        installationId TEXT NOT NULL,
                        operationId TEXT NOT NULL,
                        registeredAppId TEXT,
                        buildSettingsRevision INTEGER,
                        planSha256 TEXT NOT NULL,
                        catalogSha256 TEXT NOT NULL,
                        state TEXT NOT NULL,
                        observedAt TEXT NOT NULL,
                        PRIMARY KEY(runnerId, installationId)
                    )
                    """.trimIndent(),
                )
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE global_settings ADD COLUMN androidStorageBudgetBytes INTEGER NOT NULL DEFAULT 4294967296")
                db.execSQL("ALTER TABLE global_settings ADD COLUMN storageWarningPercent INTEGER NOT NULL DEFAULT 80")
                db.execSQL("ALTER TABLE release_snapshots ADD COLUMN observationSha256 TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE release_snapshots ADD COLUMN lastObservedAt TEXT NOT NULL DEFAULT ''")
                populateReleaseObservationHashes(db)
                db.execSQL("DROP INDEX IF EXISTS index_release_snapshots_registeredAppId_providerReleaseId")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_release_snapshots_registeredAppId_observationSha256 " +
                        "ON release_snapshots(registeredAppId, observationSha256)",
                )
                createStorageTables(db)
            }
        }

        private fun populateReleaseObservationHashes(db: SupportSQLiteDatabase) {
            db.query(
                """
                SELECT s.releaseSnapshotId, s.registeredAppId, s.providerReleaseId, s.tagName,
                    s.resolvedCommitSha, s.releaseName, s.releaseUrl, s.targetCommitishRaw,
                    s.isDraft, s.isPrerelease, s.isImmutable, s.releaseCreatedAt, s.publishedAt,
                    s.fetchedAt, a.provider, a.canonicalRepositoryUrl,
                    b.provider, b.instance, b.providerRepositoryId,
                    x.providerAssetId, x.assetName, x.stableAssetUrl, x.contentType,
                    x.providerSizeBytes, x.providerDigestSha256, x.selectionReason
                FROM release_snapshots s
                JOIN registered_apps a ON a.registeredAppId = s.registeredAppId
                LEFT JOIN app_repository_bindings b ON b.registeredAppId = s.registeredAppId
                LEFT JOIN release_assets x ON x.releaseAssetId = (
                    SELECT releaseAssetId FROM release_assets candidate
                    WHERE candidate.releaseSnapshotId = s.releaseSnapshotId
                        AND candidate.providerAssetId = s.selectedProviderAssetId
                    ORDER BY candidate.releaseAssetId LIMIT 1
                )
                ORDER BY s.releaseSnapshotId
                """.trimIndent(),
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val snapshotId = cursor.getString(0)
                    val fallbackProvider = cursor.getString(14)
                    val repositoryUrl = cursor.getString(15)
                    val provider = if (cursor.isNull(16)) fallbackProvider else cursor.getString(16)
                    val instance = if (cursor.isNull(17)) "github.com" else cursor.getString(17)
                    val repositoryId = if (cursor.isNull(18)) repositoryUrl else cursor.getString(18)
                    val hasSelectedAsset = !cursor.isNull(19)
                    val hash = ReleaseObservationHasher.sha256(
                        ReleaseObservationInput(
                            provider = provider,
                            instance = instance,
                            providerRepositoryId = repositoryId,
                            providerReleaseId = cursor.getLong(2).toString(),
                            tagName = cursor.getString(3),
                            resolvedCommitSha = cursor.getString(4),
                            releaseName = cursor.getString(5),
                            releaseUrl = cursor.getString(6),
                            targetCommitishRaw = cursor.getString(7),
                            isDraft = cursor.getInt(8) != 0,
                            isPrerelease = cursor.getInt(9) != 0,
                            isImmutable = cursor.getInt(10) != 0,
                            releaseCreatedAt = cursor.getString(11),
                            publishedAt = cursor.getString(12),
                            providerAssetId = if (hasSelectedAsset) cursor.getLong(19).toString() else null,
                            assetName = if (hasSelectedAsset) cursor.getString(20) else null,
                            stableAssetUrl = if (hasSelectedAsset) cursor.getString(21) else null,
                            contentType = if (hasSelectedAsset) cursor.getString(22) else null,
                            providerSizeBytes = if (hasSelectedAsset) cursor.getLong(23) else null,
                            providerDigestSha256 = if (cursor.isNull(24)) null else cursor.getString(24),
                            selectionReason = if (hasSelectedAsset) cursor.getString(25) else null,
                        ),
                    )
                    db.execSQL(
                        "UPDATE release_snapshots SET observationSha256 = ?, lastObservedAt = ? WHERE releaseSnapshotId = ?",
                        arrayOf(hash, cursor.getString(13), snapshotId),
                    )
                }
            }
        }

        private fun createStorageTables(db: SupportSQLiteDatabase) {
            STORAGE_SCHEMA_SQL.forEach(db::execSQL)
        }

        private val STORAGE_SCHEMA_SQL = listOf(
            """CREATE TABLE IF NOT EXISTS resource_availability (ownerType TEXT NOT NULL, ownerId TEXT NOT NULL, resourceKind TEXT NOT NULL, resourceId TEXT NOT NULL, state TEXT NOT NULL, observedBytes INTEGER, knownSha256 TEXT, lastUsedAt TEXT, checkedAt TEXT NOT NULL, deletionRunId TEXT, deletionReason TEXT, PRIMARY KEY(ownerType, ownerId, resourceKind, resourceId))""",
            "CREATE INDEX IF NOT EXISTS index_resource_availability_state ON resource_availability(state)",
            "CREATE INDEX IF NOT EXISTS index_resource_availability_resourceKind_resourceId ON resource_availability(resourceKind, resourceId)",
            """CREATE TABLE IF NOT EXISTS storage_reservations (reservationId TEXT NOT NULL, area TEXT NOT NULL, purpose TEXT NOT NULL, resourceKind TEXT NOT NULL, resourceId TEXT NOT NULL, requestedBytes INTEGER NOT NULL, principalId TEXT NOT NULL, operationId TEXT NOT NULL, state TEXT NOT NULL, createdAt TEXT NOT NULL, updatedAt TEXT NOT NULL, PRIMARY KEY(reservationId))""",
            "CREATE INDEX IF NOT EXISTS index_storage_reservations_state ON storage_reservations(state)",
            "CREATE INDEX IF NOT EXISTS index_storage_reservations_area_resourceKind_resourceId ON storage_reservations(area, resourceKind, resourceId)",
            """CREATE TABLE IF NOT EXISTS retention_holds (holdId TEXT NOT NULL, runnerId TEXT NOT NULL, principalId TEXT NOT NULL, resourceKind TEXT NOT NULL, resourceId TEXT NOT NULL, reason TEXT NOT NULL, clientReferenceType TEXT NOT NULL, clientReferenceId TEXT NOT NULL, requestSha256 TEXT NOT NULL, state TEXT NOT NULL, createdOperationId TEXT NOT NULL, releasedOperationId TEXT, createdAt TEXT NOT NULL, releasedAt TEXT, PRIMARY KEY(holdId))""",
            "CREATE INDEX IF NOT EXISTS index_retention_holds_runnerId ON retention_holds(runnerId)",
            "CREATE INDEX IF NOT EXISTS index_retention_holds_resourceKind_resourceId ON retention_holds(resourceKind, resourceId)",
            "CREATE INDEX IF NOT EXISTS index_retention_holds_state ON retention_holds(state)",
            """CREATE TABLE IF NOT EXISTS cleanup_runs (cleanupRunId TEXT NOT NULL, previewId TEXT NOT NULL, ownerType TEXT NOT NULL, ownerId TEXT NOT NULL, area TEXT NOT NULL, state TEXT NOT NULL, filterSha256 TEXT NOT NULL, truncated INTEGER NOT NULL, expiresAt TEXT NOT NULL, releasedBytes INTEGER NOT NULL, startedAt TEXT, finishedAt TEXT, createdAt TEXT NOT NULL, PRIMARY KEY(cleanupRunId))""",
            "CREATE INDEX IF NOT EXISTS index_cleanup_runs_state ON cleanup_runs(state)",
            "CREATE INDEX IF NOT EXISTS index_cleanup_runs_ownerType ON cleanup_runs(ownerType)",
            """CREATE TABLE IF NOT EXISTS cleanup_items (itemId TEXT NOT NULL, cleanupRunId TEXT NOT NULL, resourceKind TEXT NOT NULL, resourceId TEXT NOT NULL, observedBytes INTEGER NOT NULL, observedToken TEXT NOT NULL, eligibleAt TEXT NOT NULL, protectionReasons TEXT NOT NULL, selected INTEGER NOT NULL, result TEXT, releasedBytes INTEGER NOT NULL, reasonCode TEXT, reasonMessage TEXT, PRIMARY KEY(itemId), FOREIGN KEY(cleanupRunId) REFERENCES cleanup_runs(cleanupRunId) ON UPDATE NO ACTION ON DELETE CASCADE)""",
            "CREATE INDEX IF NOT EXISTS index_cleanup_items_cleanupRunId ON cleanup_items(cleanupRunId)",
            "CREATE INDEX IF NOT EXISTS index_cleanup_items_resourceKind_resourceId ON cleanup_items(resourceKind, resourceId)",
            """CREATE TABLE IF NOT EXISTS audit_exports (auditExportId TEXT NOT NULL, scopeType TEXT NOT NULL, scopeJson TEXT NOT NULL, filterJson TEXT NOT NULL, state TEXT NOT NULL, stagingName TEXT, payloadSha256 TEXT, bundleSha256 TEXT, sizeBytes INTEGER, recordCount INTEGER, errorCode TEXT, createdAt TEXT NOT NULL, updatedAt TEXT NOT NULL, PRIMARY KEY(auditExportId))""",
            "CREATE INDEX IF NOT EXISTS index_audit_exports_state ON audit_exports(state)",
            "CREATE INDEX IF NOT EXISTS index_audit_exports_createdAt ON audit_exports(createdAt)",
        )

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS index_registered_apps_canonicalRepositoryUrl")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_registered_apps_canonicalRepositoryUrl " +
                        "ON registered_apps(canonicalRepositoryUrl)",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS app_repository_bindings (
                        registeredAppId TEXT NOT NULL,
                        provider TEXT NOT NULL,
                        instance TEXT NOT NULL,
                        providerRepositoryId TEXT,
                        identityStatus TEXT NOT NULL,
                        registrationSlot TEXT NOT NULL,
                        verifiedAt TEXT,
                        PRIMARY KEY(registeredAppId),
                        FOREIGN KEY(registeredAppId) REFERENCES registered_apps(registeredAppId)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "index_app_repository_bindings_provider_instance_providerRepositoryId_registrationSlot " +
                        "ON app_repository_bindings(provider, instance, providerRepositoryId, registrationSlot)",
                )
                db.query(
                    "SELECT registeredAppId, provider, canonicalRepositoryUrl FROM registered_apps",
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        val registeredAppId = cursor.getString(0)
                        val legacyProvider = cursor.getString(1)
                        val repositoryUrl = cursor.getString(2)
                        val isGitHubProvider = legacyProvider in setOf(
                            "GITHUB_RELEASES",
                            "PUBLIC_GITHUB_RELEASES",
                        )
                        val hasValidLocator = isGitHubProvider && runCatching {
                            GitHubRepositoryParser.parse(repositoryUrl)
                        }.isSuccess
                        db.execSQL(
                            """
                            INSERT INTO app_repository_bindings (
                                registeredAppId, provider, instance, providerRepositoryId,
                                identityStatus, registrationSlot, verifiedAt
                            ) VALUES (?, ?, 'github.com', NULL, ?, 'PRIMARY', NULL)
                            """.trimIndent(),
                            arrayOf(
                                registeredAppId,
                                if (isGitHubProvider) "GITHUB" else legacyProvider,
                                if (hasValidLocator) "LEGACY_UNRESOLVED" else "LEGACY_INVALID",
                            ),
                        )
                    }
                }
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS source_discoveries (
                        discoveryId TEXT NOT NULL,
                        registeredAppId TEXT NOT NULL,
                        repositoryProvider TEXT NOT NULL,
                        repositoryInstance TEXT NOT NULL,
                        providerRepositoryId TEXT NOT NULL,
                        requestedBranch TEXT NOT NULL,
                        resolvedCommitSha TEXT,
                        rootTreeSha TEXT,
                        state TEXT NOT NULL,
                        reason TEXT,
                        entryCount INTEGER NOT NULL,
                        requestCount INTEGER NOT NULL,
                        receivedBytes INTEGER NOT NULL,
                        maxDepth INTEGER NOT NULL,
                        candidateCount INTEGER NOT NULL,
                        excludedSymlinkCount INTEGER NOT NULL,
                        excludedSubmoduleCount INTEGER NOT NULL,
                        excludedCacheTreeCount INTEGER NOT NULL,
                        startedAt TEXT NOT NULL,
                        finishedAt TEXT,
                        PRIMARY KEY(discoveryId),
                        FOREIGN KEY(registeredAppId) REFERENCES registered_apps(registeredAppId)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_source_discoveries_registeredAppId " +
                        "ON source_discoveries(registeredAppId)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_source_discoveries_registeredAppId_discoveryId " +
                        "ON source_discoveries(registeredAppId, discoveryId)",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS gradle_candidates (
                        discoveryId TEXT NOT NULL,
                        relativePath TEXT NOT NULL,
                        buildRoot TEXT NOT NULL,
                        fileKind TEXT NOT NULL,
                        blobSha TEXT NOT NULL,
                        mode TEXT NOT NULL,
                        dsl TEXT NOT NULL,
                        PRIMARY KEY(discoveryId, relativePath),
                        FOREIGN KEY(discoveryId) REFERENCES source_discoveries(discoveryId)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_gradle_candidates_discoveryId " +
                        "ON gradle_candidates(discoveryId)",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS app_build_configurations (
                        registeredAppId TEXT NOT NULL,
                        revision INTEGER NOT NULL,
                        schemaVersion INTEGER NOT NULL,
                        canonicalJson TEXT NOT NULL,
                        contentSha256 TEXT NOT NULL,
                        validationState TEXT NOT NULL,
                        createdAt TEXT NOT NULL,
                        PRIMARY KEY(registeredAppId, revision),
                        FOREIGN KEY(registeredAppId) REFERENCES registered_apps(registeredAppId)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_app_build_configurations_registeredAppId " +
                        "ON app_build_configurations(registeredAppId)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_app_build_configurations_registeredAppId_revision " +
                        "ON app_build_configurations(registeredAppId, revision)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_app_build_configurations_registeredAppId_contentSha256 " +
                        "ON app_build_configurations(registeredAppId, contentSha256)",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS app_source_heads (
                        registeredAppId TEXT NOT NULL,
                        latestDiscoveryId TEXT,
                        selectedConfigurationRevision INTEGER,
                        updatedAt TEXT NOT NULL,
                        PRIMARY KEY(registeredAppId),
                        FOREIGN KEY(registeredAppId) REFERENCES registered_apps(registeredAppId)
                            ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(registeredAppId, latestDiscoveryId)
                            REFERENCES source_discoveries(registeredAppId, discoveryId)
                            ON UPDATE NO ACTION ON DELETE NO ACTION,
                        FOREIGN KEY(registeredAppId, selectedConfigurationRevision)
                            REFERENCES app_build_configurations(registeredAppId, revision)
                            ON UPDATE NO ACTION ON DELETE NO ACTION
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_app_source_heads_registeredAppId_latestDiscoveryId " +
                        "ON app_source_heads(registeredAppId, latestDiscoveryId)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_app_source_heads_registeredAppId_selectedConfigurationRevision " +
                        "ON app_source_heads(registeredAppId, selectedConfigurationRevision)",
                )
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE jobs ADD COLUMN sandboxMode TEXT")
                db.execSQL("ALTER TABLE jobs ADD COLUMN sandboxOrigin TEXT")
                db.execSQL("ALTER TABLE jobs ADD COLUMN sandboxProfileId TEXT")
                db.execSQL("ALTER TABLE jobs ADD COLUMN sandboxCleanupStatus TEXT")
                db.execSQL("ALTER TABLE jobs ADD COLUMN sandboxResponseSeen INTEGER NOT NULL DEFAULT 0")
                // Existing rows are legacy-unavailable, not newly-created placeholders.
                db.execSQL("UPDATE jobs SET sandboxResponseSeen = 1")
                db.execSQL("ALTER TABLE build_environment_manifests ADD COLUMN sandboxJson TEXT")
            }
        }

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

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS build_environment_manifests (
                        jobId TEXT NOT NULL,
                        schemaVersion INTEGER NOT NULL,
                        commitSha TEXT NOT NULL,
                        javaVersion TEXT NOT NULL,
                        javaVendor TEXT NOT NULL,
                        gradleVersion TEXT NOT NULL,
                        androidSdkApiLevel INTEGER NOT NULL,
                        buildToolsVersion TEXT NOT NULL,
                        apkSha256 TEXT NOT NULL,
                        retrievedAt TEXT NOT NULL,
                        PRIMARY KEY(jobId),
                        FOREIGN KEY(jobId) REFERENCES jobs(jobId)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS build_environment_dependencies (
                        jobId TEXT NOT NULL,
                        ordinal INTEGER NOT NULL,
                        fileName TEXT NOT NULL,
                        sha256 TEXT NOT NULL,
                        PRIMARY KEY(jobId, ordinal),
                        FOREIGN KEY(jobId) REFERENCES jobs(jobId)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_build_environment_dependencies_jobId " +
                        "ON build_environment_dependencies(jobId)",
                )
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE jobs ADD COLUMN effectiveDependencyPinning " +
                        "TEXT NOT NULL DEFAULT 'NONE'",
                )
                db.execSQL(
                    "ALTER TABLE comparison_runs ADD COLUMN runnerDependencyPinning " +
                        "TEXT NOT NULL DEFAULT 'NONE'",
                )
                db.execSQL(
                    "ALTER TABLE comparison_runs ADD COLUMN repeatRunnerDependencyPinning " +
                        "TEXT NOT NULL DEFAULT 'NONE'",
                )
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE jobs ADD COLUMN effectiveSourceDateEpoch INTEGER")
                db.execSQL(
                    "ALTER TABLE jobs ADD COLUMN effectiveNoBuildCache " +
                        "INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL("ALTER TABLE jobs ADD COLUMN effectiveFixedLocale TEXT")
                db.execSQL("ALTER TABLE build_environment_manifests ADD COLUMN sourceDateEpoch INTEGER")
                db.execSQL(
                    "ALTER TABLE build_environment_manifests ADD COLUMN noBuildCache " +
                        "INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL("ALTER TABLE build_environment_manifests ADD COLUMN fixedLocale TEXT")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS source_scans (
                        jobId TEXT NOT NULL,
                        schemaVersion INTEGER NOT NULL,
                        resolvedCommitSha TEXT NOT NULL,
                        scannerVersion TEXT NOT NULL,
                        resultSha256 TEXT NOT NULL,
                        scannedFiles INTEGER NOT NULL,
                        scannedBytes INTEGER NOT NULL,
                        skippedBinaryFiles INTEGER NOT NULL,
                        skippedSymlinks INTEGER NOT NULL,
                        findingCount INTEGER NOT NULL,
                        requiresReview INTEGER NOT NULL,
                        reviewed INTEGER NOT NULL,
                        retrievedAt TEXT NOT NULL,
                        PRIMARY KEY(jobId),
                        FOREIGN KEY(jobId) REFERENCES jobs(jobId)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS source_scan_detector_counts (
                        jobId TEXT NOT NULL,
                        detectorId TEXT NOT NULL,
                        count INTEGER NOT NULL,
                        PRIMARY KEY(jobId, detectorId),
                        FOREIGN KEY(jobId) REFERENCES source_scans(jobId)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_source_scan_detector_counts_jobId " +
                        "ON source_scan_detector_counts(jobId)",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS source_scan_findings (
                        jobId TEXT NOT NULL,
                        ordinal INTEGER NOT NULL,
                        detectorId TEXT NOT NULL,
                        displayPath TEXT NOT NULL,
                        line INTEGER,
                        `column` INTEGER,
                        PRIMARY KEY(jobId, ordinal),
                        FOREIGN KEY(jobId) REFERENCES source_scans(jobId)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_source_scan_findings_jobId " +
                        "ON source_scan_findings(jobId)",
                )
            }
        }
    }
}
