package worq.order.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PortableBackupValidatorTest {
    @Test
    fun codecRoundTripsEveryPortableFieldAndValidatorAcceptsTheCompleteFixture() {
        val data = completeData()
        val manifest = validManifest()

        val decodedData = PortableBackupJsonCodec.decodeData(PortableBackupJsonCodec.encodeData(data))
        val decodedManifest =
            PortableBackupJsonCodec.decodeManifest(PortableBackupJsonCodec.encodeManifest(manifest))

        assertEquals(data, (decodedData as PortableBackupDecodeResult.Success).value)
        assertEquals(manifest, (decodedManifest as PortableBackupDecodeResult.Success).value)
        assertTrue(PortableBackupValidator.validateData(data) is PortableBackupDataValidationResult.Valid)
        assertEquals(
            PortableBackupManifestValidationResult.Current,
            PortableBackupValidator.validateManifest(manifest),
        )
    }

    @Test
    fun validatorRejectsFixtureInvalidGraphClassesBeforeAnyReplacementCanStart() {
        val data = completeData()
        val task = data.tasks.single { it.id == "task-complete" }
        val duplicateClient = data.clients.first().copy(name = "Duplicate")
        val brokenReference = task.copy(clientId = "missing-client")
        val duplicateSnapshot =
            task.copy(
                tagSnapshots = task.tagSnapshots + task.tagSnapshots.first().copy(id = "snapshot-copy"),
            )
        val openInterval = task.copy(intervals = task.intervals.map { it.copy(stopEpochMs = null) })
        val invalidEnum = task.copy(workType = "NOT_A_WORK_TYPE")
        val invalidZone = task.copy(zoneId = "No/Such_Zone")
        val tooLongText = task.copy(description = "x".repeat(1000), tagSnapshots = emptyList())
        val tooLongTag = data.tags.first().copy(text = "t".repeat(401), normalizedText = "t".repeat(401))

        assertFailure(
            data.copy(clients = data.clients + duplicateClient),
            PortableBackupValidationFailure.DUPLICATE_ID,
        )
        assertFailure(
            data.copy(tasks = data.tasks.map { if (it.id == task.id) brokenReference else it }),
            PortableBackupValidationFailure.BROKEN_REFERENCE,
        )
        assertFailure(
            data.copy(tasks = data.tasks.map { if (it.id == task.id) duplicateSnapshot else it }),
            PortableBackupValidationFailure.DUPLICATE_SNAPSHOT,
        )
        assertFailure(
            data.copy(tasks = data.tasks.map { if (it.id == task.id) openInterval else it }),
            PortableBackupValidationFailure.OPEN_INTERVAL,
        )
        assertFailure(
            data.copy(tasks = data.tasks.map { if (it.id == task.id) invalidEnum else it }),
            PortableBackupValidationFailure.INVALID_ENUM,
        )
        assertFailure(
            data.copy(tasks = data.tasks.map { if (it.id == task.id) invalidZone else it }),
            PortableBackupValidationFailure.INVALID_ZONE_ID,
        )
        assertFailure(
            data.copy(tasks = data.tasks.map { if (it.id == task.id) tooLongText else it }),
            PortableBackupValidationFailure.TEXT_LIMIT,
        )
        assertFailure(
            data.copy(tags = listOf(tooLongTag) + data.tags.drop(1)),
            PortableBackupValidationFailure.TAG_LIMIT,
        )
    }

    @Test
    fun strictCodecRejectsRuntimeOrInstallationIdentityFields() {
        val encoded = PortableBackupJsonCodec.encodeData(completeData())
        val withExcludedField = encoded.replaceFirst("{", "{\"exportOriginId\":\"source-device\",")
        val withActiveTimer = encoded.replaceFirst("{", "{\"activeTimer\":{},")

        assertTrue(PortableBackupJsonCodec.decodeData(withExcludedField) is PortableBackupDecodeResult.Invalid)
        val activeTimerResult = PortableBackupJsonCodec.decodeData(withActiveTimer)
        assertTrue(activeTimerResult is PortableBackupDecodeResult.Invalid)
        assertEquals(
            PortableBackupDecodeError.ACTIVE_TIMER_PRESENT,
            (activeTimerResult as PortableBackupDecodeResult.Invalid).error,
        )
    }

    @Test
    fun strictCodecRejectsDuplicateKeysAmbiguousEscapedKeysAndInvalidInstants() {
        val encoded = PortableBackupJsonCodec.encodeData(completeData())
        val duplicateKey = encoded.replaceFirst("{", "{\"dataModelVersion\":7,")
        val escapedDuplicateKey = encoded.replaceFirst("{", "{\"dataModel\\u0056ersion\":7,")
        val invalidInstant =
            PortableBackupJsonCodec.encodeManifest(validManifest())
                .replace(
                    "\"createdAtEpochMs\":${PortableBackupV1Fixtures.createdAt.toEpochMilli()}",
                    "\"createdAtEpochMs\":\"not-an-instant\"",
                )

        assertInvalidDecode(duplicateKey, PortableBackupDecodeError.INVALID_SHAPE)
        assertInvalidDecode(escapedDuplicateKey, PortableBackupDecodeError.INVALID_SHAPE)
        val manifestResult = PortableBackupJsonCodec.decodeManifest(invalidInstant)
        assertTrue(manifestResult is PortableBackupDecodeResult.Invalid)
        assertEquals(
            PortableBackupDecodeError.INVALID_INSTANT,
            (manifestResult as PortableBackupDecodeResult.Invalid).error,
        )
    }

    @Test
    fun manifestVersionDispatchFailsClosedForFutureAndRequiresExplicitLegacyUpgrade() {
        val current = validManifest()
        assertTrue(
            PortableBackupValidator.validateManifest(
                current.copy(backupFormatVersion = PORTABLE_BACKUP_FORMAT_VERSION + 1),
            ) is PortableBackupManifestValidationResult.UnsupportedFutureVersion,
        )
        assertTrue(
            PortableBackupValidator.validateManifest(current.copy(backupFormatVersion = 0))
                is PortableBackupManifestValidationResult.UpgradeRequired,
        )
        assertTrue(
            PortableBackupValidator.validateManifest(
                current.copy(
                    productId = "not.worqorder",
                    backupFormatVersion = PORTABLE_BACKUP_FORMAT_VERSION + 1,
                ),
            ) is PortableBackupManifestValidationResult.Invalid,
        )

        val old = current.copy(backupFormatVersion = 0)
        assertTrue(
            PortableBackupUpgraderRegistry().upgradeToCurrent(old, "{\"legacy\":true}")
                is PortableBackupUpgradeResult.Unsupported,
        )
        val registry =
            PortableBackupUpgraderRegistry(
                setOf(
                    object : PortableBackupFormatUpgrader {
                        override val fromFormatVersion: Int = 0

                        override fun upgradeToCurrent(
                            manifest: PortableBackupManifestV1,
                            encodedDataJson: String,
                        ): PortableBackupUpgradeResult =
                            PortableBackupUpgradeResult.Upgraded(
                                manifest.copy(backupFormatVersion = PORTABLE_BACKUP_FORMAT_VERSION),
                                completeData(),
                            )
                    },
                ),
            )
        val result = registry.upgradeToCurrent(old, "{\"legacy\":true}")
        assertTrue(result is PortableBackupUpgradeResult.Upgraded)
        assertEquals(1, (result as PortableBackupUpgradeResult.Upgraded).manifest.backupFormatVersion)
    }

    @Test
    fun validatorRejectsCardinalitySelectionAndDirectoryUniquenessViolations() {
        val data = completeData()
        val task = data.tasks.single { it.id == "task-complete" }
        val secondInterval = task.intervals.single().copy(id = "interval-second")
        assertFailure(
            data.copy(
                tasks =
                    data.tasks.map {
                        if (it.id == task.id) it.copy(intervals = it.intervals + secondInterval)
                        else it
                    },
            ),
            PortableBackupValidationFailure.INVALID_INTERVAL,
        )
        assertFailure(
            data.copy(selection = requireNotNull(data.selection).copy(seriesId = "wrong-series")),
            PortableBackupValidationFailure.INVALID_SELECTION,
        )
        assertFailure(
            data.copy(
                tags =
                    data.tags +
                        data.tags.first().copy(
                            id = "tag-duplicate-text",
                            createdAtEpochMs = data.tags.first().createdAtEpochMs + 1,
                            updatedAtEpochMs = data.tags.first().updatedAtEpochMs + 1,
                        ),
            ),
            PortableBackupValidationFailure.INVALID_TAG,
        )
        assertFailure(
            data.copy(
                settings = data.settings.copy(selectedConsultantId = "consultant-archived"),
            ),
            PortableBackupValidationFailure.BROKEN_REFERENCE,
        )
    }

    private fun assertFailure(
        data: PortableBackupDataV1,
        expected: PortableBackupValidationFailure,
    ) {
        val result = PortableBackupValidator.validateData(data)
        assertTrue(result is PortableBackupDataValidationResult.Invalid)
        assertTrue(expected in (result as PortableBackupDataValidationResult.Invalid).failures)
    }

    private fun assertInvalidDecode(
        json: String,
        expected: PortableBackupDecodeError,
    ) {
        val result = PortableBackupJsonCodec.decodeData(json)
        assertTrue(result is PortableBackupDecodeResult.Invalid)
        assertEquals(expected, (result as PortableBackupDecodeResult.Invalid).error)
    }

    private fun validManifest(): PortableBackupManifestV1 =
        PortableBackupManifestV1(
            productId = PORTABLE_BACKUP_PRODUCT_ID,
            backupFormatVersion = PORTABLE_BACKUP_FORMAT_VERSION,
            producerVersionName = "0.6.0",
            producerVersionCode = 6,
            createdAtEpochMs = PortableBackupV1Fixtures.createdAt.toEpochMilli(),
            dataModelVersion = PORTABLE_BACKUP_DATA_MODEL_VERSION,
            dataEncoding = "UTF-8",
            compression = "DEFLATE",
            dataByteCount = 1,
            dataSha256 = "a".repeat(64),
        )

    private fun completeData(): PortableBackupDataV1 {
        val fixture = PortableBackupV1Fixtures.complete
        return PortableBackupDataV1(
            dataModelVersion = fixture.dataModelVersion,
            clients =
                fixture.clients.map { client ->
                    PortableBackupClientV1(
                        id = client.id,
                        name = client.name,
                        canonicalName = client.canonicalName,
                        isActive = client.active,
                        createdAtEpochMs = PortableBackupV1Fixtures.createdAt.toEpochMilli(),
                        updatedAtEpochMs = PortableBackupV1Fixtures.createdAt.toEpochMilli(),
                        archivedAtEpochMs = client.archivedAt?.toEpochMilli(),
                    )
                },
            consultants =
                fixture.consultants.map { consultant ->
                    PortableBackupConsultantV1(
                        id = consultant.id,
                        name = consultant.name,
                        canonicalName = consultant.canonicalName,
                        isActive = consultant.active,
                        createdAtEpochMs = PortableBackupV1Fixtures.createdAt.toEpochMilli(),
                        updatedAtEpochMs = PortableBackupV1Fixtures.createdAt.toEpochMilli(),
                        archivedAtEpochMs = consultant.archivedAt?.toEpochMilli(),
                    )
                },
            tags =
                fixture.tags.map { tag ->
                    PortableBackupTagV1(
                        id = tag.id,
                        category = tag.category,
                        text = tag.text,
                        normalizedText = tag.normalizedText,
                        createdAtEpochMs = tag.createdAt.toEpochMilli(),
                        updatedAtEpochMs = tag.createdAt.toEpochMilli(),
                    )
                },
            tasks =
                fixture.tasks.map { task ->
                    PortableBackupTaskV1(
                        id = task.id,
                        seriesId = task.seriesId,
                        clientId = task.clientId,
                        consultantId = task.consultantId,
                        consultantNameSnapshot = task.consultantNameSnapshot.orEmpty(),
                        description = task.description,
                        hardwareSoftwarePurchases = task.expense,
                        workType = task.workType,
                        billingStatus = task.billingStatus,
                        mileage = task.mileage.ifBlank { null },
                        notes = task.notes,
                        workDateEpochDay = task.workDate.toEpochDay(),
                        zoneId = task.zoneId,
                        createdAtEpochMs = task.createdAt.toEpochMilli(),
                        updatedAtEpochMs = task.updatedAt.toEpochMilli(),
                        tagSnapshots =
                            (task.descriptionTags + task.expenseTags).map { snapshot ->
                                PortableBackupTaskTagSnapshotV1(
                                    id = snapshot.id,
                                    category = snapshot.category,
                                    text = snapshot.text,
                                    sourceTagId = snapshot.sourceTagId,
                                    selectionOrder = snapshot.order,
                                    createdAtEpochMs = snapshot.createdAt.toEpochMilli(),
                                )
                            },
                        intervals =
                            task.intervals.map { interval ->
                                PortableBackupIntervalV1(
                                    id = interval.id,
                                    taskId = interval.taskId,
                                    startEpochMs = interval.start.toEpochMilli(),
                                    stopEpochMs = interval.stop.toEpochMilli(),
                                    wasManuallyEdited = interval.manuallyEdited,
                                    createdAtEpochMs = interval.start.toEpochMilli(),
                                    updatedAtEpochMs = interval.stop.toEpochMilli(),
                                )
                            },
                    )
                },
            settings =
                PortableBackupSettingsV1(
                    themeMode = fixture.preferences.themeMode,
                    timeZoneMode = fixture.preferences.timeZoneMode,
                    manualZoneId = fixture.preferences.manualZoneId,
                    defaultExportDestination = fixture.preferences.defaultExportDestination,
                    lastExportAttempt =
                        fixture.preferences.exportHistory.singleOrNull()?.let { attempt ->
                            PortableBackupLastExportAttemptV1(
                                destination = attempt.destination,
                                workDateEpochDay = attempt.workDate.toEpochDay(),
                                attemptedAtEpochMs = attempt.attemptedAt.toEpochMilli(),
                                outcome = attempt.outcome,
                                errorCategory = attempt.errorCategory,
                            )
                        },
                    selectedConsultantId = fixture.preferences.selectedConsultantId,
                    landscapeHandedness = fixture.preferences.landscapeHandedness,
                ),
            selection =
                fixture.preferences.selectedTaskId?.let { taskId ->
                    PortableBackupSelectionV1(
                        taskId = taskId,
                        seriesId = requireNotNull(fixture.preferences.selectedSeriesId),
                        selectedOnEpochDay = requireNotNull(fixture.preferences.selectedDate).toEpochDay(),
                        selectedInZoneId = requireNotNull(fixture.preferences.selectedZoneId),
                    )
                },
        )
    }
}
