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
            val individuals = backupDao.getAllIndividuals().map { 
                BackupIndividual(it.id, it.firstName, it.lastName, it.dateOfBirth, it.sex.name, it.notes)
            }
            val groups = backupDao.getAllGroups().map {
                BackupGroup(it.id, it.name, it.category ?: "", !it.isDeleted)
            }
            val groupMembers = backupDao.getAllGroupMembers().map {
                BackupGroupMemberCrossRef(it.groupId, it.individualId)
            }
            val testingEvents = backupDao.getAllTestingEvents().map {
                BackupTestingEvent(it.id, it.name, it.date, it.notes)
            }
            val eventTests = backupDao.getAllEventTests().map {
                BackupEventTestCrossRef(it.eventId, it.testId)
            }
            val testResults = backupDao.getAllTestResults().map {
                BackupTestResult(it.id, it.eventId, it.individualId, it.testId, it.rawScore, it.percentile?.toDouble(), it.createdAt, it.captureMethod, null)
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
                customNorms = customNorms
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
        val indEntities = payload.individuals.map {
            com.vamshi.field.data.local.entities.people.IndividualEntity(
                id = it.id, firstName = it.firstName, lastName = it.lastName,
                dateOfBirth = it.dateOfBirth, sex = com.vamshi.field.domain.model.people.BiologicalSex.valueOf(it.gender), notes = it.notes
            )
        }
        val grpEntities = payload.groups.map {
            com.vamshi.field.data.local.entities.people.GroupEntity(
                id = it.id, name = it.name, location = null, cycle = null, category = it.type, isDeleted = !it.isActive
            )
        }
        val gmEntities = payload.groupMembers.map {
            com.vamshi.field.data.local.entities.people.GroupMemberCrossRef(it.groupId, it.individualId)
        }
        val teEntities = payload.testingEvents.map {
            com.vamshi.field.data.local.entities.testing.TestingEventEntity(
                id = it.id, name = it.name, date = it.timestamp, notes = it.notes
            )
        }
        val etEntities = payload.eventTests.map {
            com.vamshi.field.data.local.entities.testing.EventTestCrossRef(it.eventId, it.testId)
        }
        val trEntities = payload.testResults.map {
            com.vamshi.field.data.local.entities.testing.TestResultEntity(
                id = it.id, eventId = it.eventId, individualId = it.individualId, testId = it.testId,
                rawScore = it.rawScore, ageAtTime = 0f, captureMethod = it.captureMethod, createdAt = it.timestamp
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
    }
}
