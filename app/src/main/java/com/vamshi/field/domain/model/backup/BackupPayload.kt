package com.vamshi.field.domain.model.backup

data class BackupPayload(
    val individuals: List<BackupIndividual>,
    val groups: List<BackupGroup>,
    val groupMembers: List<BackupGroupMemberCrossRef>,
    val testingEvents: List<BackupTestingEvent>,
    val eventTests: List<BackupEventTestCrossRef>,
    val testResults: List<BackupTestResult>,
    val users: List<BackupUser>,

    // Coach-authored catalog rows (TestSource.USER). Seeded catalog rows are deliberately
    // excluded — they come from assets on every install, so backing them up would just
    // bloat the file and risk restoring a stale copy over a newer CSV.
    //
    // These MUST restore before testResults: test_results.testId is a RESTRICT foreign key
    // to fitness_tests, so a backup containing results for a custom test would otherwise
    // fail the constraint and abort the entire restore as CorruptedBackup.
    //
    // Nullable on purpose. Coaches have backups in Drive written before these fields
    // existed, and Gson instantiates data classes through Unsafe — it bypasses the
    // constructor, so a Kotlin default is NOT applied for a missing JSON field. Declaring
    // these non-null with `= emptyList()` would hand back a null through a non-null type
    // and NPE on the first .map(), aborting the restore as CorruptedBackup: exactly the
    // data loss these fields exist to prevent. Read them via orEmpty().
    val customCategories: List<BackupTestCategory>? = null,
    val customTests: List<BackupFitnessTest>? = null,
    val customNorms: List<BackupNormReference>? = null
)

data class BackupTestCategory(
    val id: String,
    val name: String,
    val description: String?,
    val sortOrder: Int,
    val radarAxis: String?
)

data class BackupFitnessTest(
    val id: String,
    val categoryId: String,
    val name: String,
    val unit: String,
    val isHigherBetter: Boolean,
    val description: String?,
    val timingMode: String,
    val inputParadigm: String,
    val athletesPerHeat: Int?,
    val trialsPerAthlete: Int,
    val validMin: Double?,
    val validMax: Double?,
    val interpretationStrategy: String,
    val calculationConfig: String?,
    val youtubeId: String?,
    val isDeleted: Boolean
)

data class BackupNormReference(
    val id: String,
    val testId: String,
    val variant: String?,
    val sex: String,
    val ageMin: Float,
    val ageMax: Float,
    val minScore: Double,
    val maxScore: Double,
    val percentile: Int,
    val classification: String?
)

data class BackupIndividual(
    val id: String,
    val firstName: String,
    val lastName: String,
    val dateOfBirth: Long,
    val gender: String,
    val notes: String?
)

data class BackupGroup(
    val id: String,
    val name: String,
    val type: String,
    val isActive: Boolean
)

data class BackupGroupMemberCrossRef(
    val groupId: String,
    val individualId: String
)

data class BackupTestingEvent(
    val id: String,
    val name: String,
    val timestamp: Long,
    val notes: String?
)

data class BackupEventTestCrossRef(
    val eventId: String,
    val testId: String
)

data class BackupTestResult(
    val id: String,
    val eventId: String,
    val individualId: String,
    val testId: String,
    val rawScore: Double,
    val standardizedScore: Double?,
    val timestamp: Long,
    val captureMethod: String,
    val notes: String?
)

data class BackupUser(
    val id: String,
    val firstName: String,
    val lastName: String,
    val username: String,
    val passwordHash: String,
    val passwordSalt: String,
    val securityQuestion: String?,
    val securityAnswerHash: String?,
    val securityAnswerSalt: String?,
    val email: String? = null,
    val createdAt: Long
)
