package com.vamshi.field.data.repository

import android.content.Context
import com.vamshi.field.data.AppDatabase
import com.vamshi.field.domain.model.backup.*
import com.vamshi.field.domain.model.standards.TestSource
import com.vamshi.field.domain.repository.BackupRepository
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import com.vamshi.field.data.backup.DriveBackupHelper
import javax.inject.Inject

class BackupRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appDatabase: AppDatabase,
    private val gson: Gson,
    private val driveBackupHelper: DriveBackupHelper
) : BackupRepository {

    private val backupDao = appDatabase.backupDao()
    private val syncState = MutableStateFlow(false)
    private val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    override suspend fun backupToDrive() = withContext(Dispatchers.IO) {
        syncState.value = true
        try {
            // Extract all data
            // Named arguments from here down: these payload constructors are long enough
            // that a positional slip would be silent and would only surface as data loss
            // on someone's restore.
            val individuals = backupDao.getAllIndividuals().map {
                BackupIndividual(
                    id = it.id,
                    firstName = it.firstName,
                    lastName = it.lastName,
                    dateOfBirth = it.dateOfBirth,
                    gender = it.sex.name,
                    notes = it.notes,
                    medicalAlert = it.medicalAlert,
                    isRestricted = it.isRestricted,
                    email = it.email,
                    isActive = it.isActive,
                    isDeleted = it.isDeleted,
                    createdAt = it.createdAt,
                    updatedAt = it.updatedAt
                )
            }
            val groups = backupDao.getAllGroups().map {
                BackupGroup(
                    id = it.id,
                    name = it.name,
                    type = it.category ?: "",
                    isActive = !it.isDeleted,
                    location = it.location,
                    cycle = it.cycle,
                    createdAt = it.createdAt
                )
            }
            val groupMembers = backupDao.getAllGroupMembers().map {
                BackupGroupMemberCrossRef(it.groupId, it.individualId)
            }
            val testingEvents = backupDao.getAllTestingEvents().map {
                BackupTestingEvent(
                    id = it.id,
                    name = it.name,
                    timestamp = it.date,
                    notes = it.notes,
                    groupId = it.groupId,
                    location = it.location,
                    createdAt = it.createdAt
                )
            }
            val eventTests = backupDao.getAllEventTests().map {
                BackupEventTestCrossRef(it.eventId, it.testId)
            }
            val testResults = backupDao.getAllTestResults().map {
                BackupTestResult(
                    id = it.id,
                    eventId = it.eventId,
                    individualId = it.individualId,
                    testId = it.testId,
                    rawScore = it.rawScore,
                    // Still written so an older build can read a newer backup.
                    standardizedScore = it.percentile?.toDouble(),
                    timestamp = it.createdAt,
                    captureMethod = it.captureMethod,
                    notes = null,
                    ageAtTime = it.ageAtTime,
                    percentile = it.percentile,
                    classification = it.classification,
                    normVariantUsed = it.normVariantUsed,
                    weightAtTime = it.weightAtTime,
                    bodyWeightKg = it.bodyWeightKg
                )
            }
            val users = backupDao.getAllUsers().map {
                BackupUser(
                    id = it.id,
                    firstName = it.firstName,
                    lastName = it.lastName,
                    username = it.username,
                    passwordHash = android.util.Base64.encodeToString(it.passwordHash, android.util.Base64.NO_WRAP),
                    passwordSalt = android.util.Base64.encodeToString(it.passwordSalt, android.util.Base64.NO_WRAP),
                    securityQuestion = it.securityQuestion,
                    securityAnswerHash = it.securityAnswerHash?.let { h -> android.util.Base64.encodeToString(h, android.util.Base64.NO_WRAP) },
                    securityAnswerSalt = it.securityAnswerSalt?.let { s -> android.util.Base64.encodeToString(s, android.util.Base64.NO_WRAP) },
                    email = it.email,
                    createdAt = it.createdAt
                )
            }

            // Coach-authored catalog only — seeded tests/categories/norms are reproduced
            // from assets on every install and must not be carried in the backup.
            val customCategories = backupDao.getUserCategories().map {
                BackupTestCategory(it.id, it.name, it.description, it.sortOrder, it.radarAxis)
            }
            val customTests = backupDao.getUserTests().map {
                BackupFitnessTest(
                    id = it.id,
                    categoryId = it.categoryId,
                    name = it.name,
                    unit = it.unit,
                    isHigherBetter = it.isHigherBetter,
                    description = it.description,
                    timingMode = it.timingMode,
                    inputParadigm = it.inputParadigm,
                    athletesPerHeat = it.athletesPerHeat,
                    trialsPerAthlete = it.trialsPerAthlete,
                    validMin = it.validMin,
                    validMax = it.validMax,
                    interpretationStrategy = it.interpretationStrategy,
                    calculationConfig = it.calculationConfig,
                    youtubeId = it.youtubeId,
                    isDeleted = it.isDeleted
                )
            }
            val customNorms = backupDao.getUserNorms().map {
                BackupNormReference(
                    id = it.id,
                    testId = it.testId,
                    variant = it.variant,
                    sex = it.sex.name,
                    ageMin = it.ageMin,
                    ageMax = it.ageMax,
                    minScore = it.minScore,
                    maxScore = it.maxScore,
                    percentile = it.percentile,
                    classification = it.classification
                )
            }

            val payload = BackupPayload(
                individuals, groups, groupMembers, testingEvents, eventTests, testResults, users,
                customCategories = customCategories,
                customTests = customTests,
                customNorms = customNorms,
                schemaVersion = PAYLOAD_SCHEMA_VERSION
            )

            // Serialize to local cache
            val json = gson.toJson(payload)
            val backupFile = File(context.cacheDir, "alearning_backup_staged.json")
            backupFile.writeText(json)

            // Upload to Google Drive using DriveBackupHelper
            driveBackupHelper.uploadToDrive(context, backupFile)
            
            recordBackupTimestamp(System.currentTimeMillis())
        } finally {
            syncState.value = false
        }
    }

    override suspend fun listAvailableBackups(): List<DriveBackupSummary> = withContext(Dispatchers.IO) {
        driveBackupHelper.listBackups(context)
    }

    override suspend fun restoreFromDrive(backupId: String) = withContext(Dispatchers.IO) {
        syncState.value = true
        try {
            // Download from Google Drive
            val backupFile = File(context.cacheDir, "alearning_backup_staged.json")
            driveBackupHelper.downloadFromDrive(context, backupFile, backupId)

            if (!backupFile.exists()) {
                throw BackupException.NoBackupFound
            }

            val json = backupFile.readText()
            val payload = try {
                gson.fromJson(json, BackupPayload::class.java)
                    ?: throw BackupException.CorruptedBackup
            } catch (e: JsonSyntaxException) {
                throw BackupException.CorruptedBackup
            }

            // Single atomic transaction: if mapping/inserting the restored payload
            // throws (e.g. a malformed backup deserializes with null fields), the
            // clear below rolls back with it and local data is left untouched.
            try {
                restoreEntities(payload)
            } catch (e: BackupException) {
                throw e
            } catch (e: Exception) {
                throw BackupException.CorruptedBackup
            }
        } finally {
            syncState.value = false
        }
    }

    internal fun recordBackupTimestamp(timestamp: Long) {
        prefs.edit().putLong(KEY_LAST_BACKUP_TIMESTAMP, timestamp).apply()
    }

    /** Visible for testing: the atomic clear-and-replace at the core of [restoreFromDrive]. */
    internal suspend fun restoreEntities(payload: BackupPayload) {
        val userEntities = payload.users.map {
            com.vamshi.field.data.local.entities.auth.UserEntity(
                id = it.id,
                firstName = it.firstName,
                lastName = it.lastName,
                username = it.username,
                email = it.email,
                passwordHash = android.util.Base64.decode(it.passwordHash, android.util.Base64.NO_WRAP),
                passwordSalt = android.util.Base64.decode(it.passwordSalt, android.util.Base64.NO_WRAP),
                securityQuestion = it.securityQuestion,
                securityAnswerHash = it.securityAnswerHash?.let { h -> android.util.Base64.decode(h, android.util.Base64.NO_WRAP) },
                securityAnswerSalt = it.securityAnswerSalt?.let { s -> android.util.Base64.decode(s, android.util.Base64.NO_WRAP) },
                createdAt = it.createdAt
            )
        }
        // Every `?:` below is the behaviour a pre-existing Drive backup gets, and it is
        // exactly what this code used to do unconditionally. New backups carry the real
        // values. medicalAlert / isRestricted are the reason this mattered most: a restore
        // used to hand back every athlete with no medical alert and no restriction flag.
        val now = System.currentTimeMillis()
        val indEntities = payload.individuals.map {
            com.vamshi.field.data.local.entities.people.IndividualEntity(
                id = it.id,
                firstName = it.firstName,
                lastName = it.lastName,
                dateOfBirth = it.dateOfBirth,
                sex = com.vamshi.field.domain.model.people.BiologicalSex.valueOf(it.gender),
                medicalAlert = it.medicalAlert,
                isRestricted = it.isRestricted ?: false,
                email = it.email,
                isActive = it.isActive ?: true,
                notes = it.notes,
                createdAt = it.createdAt ?: now,
                updatedAt = it.updatedAt ?: now,
                isDeleted = it.isDeleted ?: false
            )
        }
        val grpEntities = payload.groups.map {
            com.vamshi.field.data.local.entities.people.GroupEntity(
                id = it.id,
                name = it.name,
                location = it.location,
                cycle = it.cycle,
                // Backups write `category ?: ""`, so an absent category arrives as a blank
                // string. GroupMapper.toDomain already degrades that to null via runCatching,
                // but storing null keeps the column honest.
                category = it.type.takeIf { t -> t.isNotBlank() },
                createdAt = it.createdAt ?: now,
                updatedAt = now,
                isDeleted = !it.isActive
            )
        }
        val gmEntities = payload.groupMembers.map {
            com.vamshi.field.data.local.entities.people.GroupMemberCrossRef(it.groupId, it.individualId)
        }
        // Restored groups, by id — an event may only point at a group that is actually in
        // this payload. The foreign key is ON DELETE SET NULL, so a dangling groupId would
        // quietly detach the event all over again instead of failing loudly.
        val restoredGroupIds = payload.groups.mapTo(mutableSetOf()) { it.id }
        var orphanedEventCount = 0
        val teEntities = payload.testingEvents.map {
            val resolvedGroupId = it.groupId?.takeIf { id -> id in restoredGroupIds }
            if (it.groupId != null && resolvedGroupId == null) orphanedEventCount++
            com.vamshi.field.data.local.entities.testing.TestingEventEntity(
                id = it.id,
                groupId = resolvedGroupId,
                name = it.name,
                date = it.timestamp,
                location = it.location,
                notes = it.notes,
                createdAt = it.createdAt ?: it.timestamp
            )
        }
        if (orphanedEventCount > 0) {
            android.util.Log.w(
                "BackupRestore",
                "$orphanedEventCount restored event(s) referenced a group that was not in the " +
                    "backup; they were restored as personal sessions."
            )
        }
        val etEntities = payload.eventTests.map {
            com.vamshi.field.data.local.entities.testing.EventTestCrossRef(it.eventId, it.testId)
        }
        val trEntities = payload.testResults.map {
            com.vamshi.field.data.local.entities.testing.TestResultEntity(
                id = it.id,
                eventId = it.eventId,
                individualId = it.individualId,
                testId = it.testId,
                rawScore = it.rawScore,
                ageAtTime = it.ageAtTime ?: 0f,
                weightAtTime = it.weightAtTime,
                bodyWeightKg = it.bodyWeightKg,
                // standardizedScore is the pre-existing write-only copy of the percentile.
                // Reading it as the fallback recovers the zone colour for every backup
                // already sitting in Drive, not just ones taken after this change.
                percentile = it.percentile ?: it.standardizedScore?.toInt(),
                classification = it.classification,
                normVariantUsed = it.normVariantUsed,
                captureMethod = it.captureMethod,
                createdAt = it.timestamp
            )
        }

        // Rebuilt with source = USER so a restored custom test stays editable and, more
        // importantly, stays out of reach of the CSV importer's source-scoped deletes.
        val customCategoryEntities = payload.customCategories.orEmpty().map {
            com.vamshi.field.data.local.entities.standards.TestCategoryEntity(
                id = it.id,
                name = it.name,
                description = it.description,
                sortOrder = it.sortOrder,
                radarAxis = it.radarAxis,
                source = TestSource.USER.name
            )
        }
        val customTestEntities = payload.customTests.orEmpty().map {
            com.vamshi.field.data.local.entities.standards.FitnessTestEntity(
                id = it.id,
                categoryId = it.categoryId,
                name = it.name,
                unit = it.unit,
                isHigherBetter = it.isHigherBetter,
                description = it.description,
                timingMode = it.timingMode,
                inputParadigm = it.inputParadigm,
                athletesPerHeat = it.athletesPerHeat,
                trialsPerAthlete = it.trialsPerAthlete,
                validMin = it.validMin,
                validMax = it.validMax,
                interpretationStrategy = it.interpretationStrategy,
                calculationConfig = it.calculationConfig,
                youtubeId = it.youtubeId,
                isDeleted = it.isDeleted,
                source = TestSource.USER.name
            )
        }
        val customNormEntities = payload.customNorms.orEmpty().map {
            com.vamshi.field.data.local.entities.standards.NormReferenceEntity(
                id = it.id,
                testId = it.testId,
                variant = it.variant,
                sex = com.vamshi.field.domain.model.people.BiologicalSex.valueOf(it.sex),
                ageMin = it.ageMin,
                ageMax = it.ageMax,
                minScore = it.minScore,
                maxScore = it.maxScore,
                percentile = it.percentile,
                classification = it.classification,
                source = TestSource.USER.name
            )
        }

        backupDao.restoreAllData(
            users = userEntities,
            individuals = indEntities,
            groups = grpEntities,
            groupMembers = gmEntities,
            events = teEntities,
            eventTests = etEntities,
            results = trEntities,
            customCategories = customCategoryEntities,
            customTests = customTestEntities,
            customNorms = customNormEntities
        )
    }

    override suspend fun getLastBackupTimestamp(): Long? {
        val timestamp = prefs.getLong(KEY_LAST_BACKUP_TIMESTAMP, -1L)
        return if (timestamp == -1L) null else timestamp
    }

    override fun isSyncing(): Flow<Boolean> {
        return syncState
    }

    private companion object {
        const val KEY_LAST_BACKUP_TIMESTAMP = "last_backup_timestamp"

        /** Bump when the payload gains fields that restore needs to branch on. */
        const val PAYLOAD_SCHEMA_VERSION = 1
    }
}
