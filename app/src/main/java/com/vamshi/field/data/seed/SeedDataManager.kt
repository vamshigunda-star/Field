package com.vamshi.field.data.seed

import android.content.Context
import com.vamshi.field.data.local.entities.standards.FitnessTestEntity
import com.vamshi.field.data.local.entities.standards.NormReferenceEntity
import com.vamshi.field.data.local.entities.standards.TestCategoryEntity
import com.vamshi.field.data.mapper.standards.toDomain
import com.vamshi.field.domain.model.people.BiologicalSex
import com.vamshi.field.domain.model.standards.RecommendationCategory
import com.vamshi.field.domain.model.standards.RecommendationScope
import com.vamshi.field.domain.model.standards.RecommendationTestLink
import com.vamshi.field.domain.usecase.standards.ImportRecommendationsUseCase
import com.vamshi.field.domain.usecase.standards.ImportStandardsUseCase
import com.vamshi.field.data.local.entities.people.IndividualEntity
import com.vamshi.field.data.local.entities.people.GroupEntity
import com.vamshi.field.data.local.entities.people.GroupMemberCrossRef
import com.vamshi.field.data.local.entities.testing.TestingEventEntity
import com.vamshi.field.data.local.entities.testing.TestResultEntity
import com.vamshi.field.data.local.entities.testing.EventTestCrossRef
import java.util.Calendar
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SeedDataManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val importStandardsUseCase: ImportStandardsUseCase,
    private val importRecommendationsUseCase: ImportRecommendationsUseCase,
    private val database: com.vamshi.field.data.AppDatabase
) {
    companion object {
        private const val PREFS_NAME = "alearning_prefs"

        // Bumping this key re-runs seedIfNeeded() for every existing install on next launch.
        // This is SAFE for user data: catalog tests/categories are upserted in place, only
        // norm_references and the two recommendation tables are replaced wholesale, and
        // coach-created events/results/athletes/groups are never touched. Catalog rows
        // removed from the CSVs are left in the DB (existing results may reference them).
        private const val KEY_DATA_SEEDED = "data_seeded_csv_v14" // Bumped for corrected tests.csv youtube_ids

        // Everything this class writes to the catalog is stamped SEED so the re-import
        // above can scope its deletes and leave coach-authored rows alone. The entity
        // default is USER, so omitting this on a new call site is safe-but-wrong: the
        // row survives reseeds but shows up in the UI as a custom test.
        private const val SEED_SOURCE = "SEED"
    }

    suspend fun seedIfNeeded() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_DATA_SEEDED, false)) {
            android.util.Log.d("SeedDataManager", "Data already seeded")
            return
        }

        android.util.Log.d("SeedDataManager", "Starting data seeding from CSV...")
        // 1. Seed Test Library from CSV
        try {
            val categoryMaps = com.vamshi.field.util.CsvParser.parse(context.assets.open("test_categories.csv"))
            val testMaps = com.vamshi.field.util.CsvParser.parse(context.assets.open("tests.csv"))
            val normMaps = com.vamshi.field.util.CsvParser.parse(context.assets.open("norms.csv"))

            android.util.Log.d("SeedDataManager", "Parsed ${categoryMaps.size} categories, ${testMaps.size} tests, ${normMaps.size} norms")

            val categories = categoryMaps.map { row ->
                TestCategoryEntity(
                    id = row["id"]!!,
                    name = row["name"]!!,
                    // Absent on older CSVs without the column (row["description"] is simply
                    // null); blank values are also normalized to null so the UI's fallback
                    // text applies consistently.
                    description = row["description"]?.trim()?.ifEmpty { null },
                    sortOrder = row["sortOrder"]?.toIntOrNull() ?: 0,
                    radarAxis = row["radarAxis"],
                    source = SEED_SOURCE
                )
            }

            val tests = testMaps.map { row ->
                val rawYoutubeId = row["youtube_id"]?.trim()
                val validatedYoutubeId = if (!rawYoutubeId.isNullOrEmpty()) {
                    if (rawYoutubeId.length == 11 && rawYoutubeId.matches(Regex("^[a-zA-Z0-9_-]{11}$"))) {
                        rawYoutubeId
                    } else {
                        android.util.Log.w("SeedDataManager", "Invalid youtube_id '${rawYoutubeId}' for test ${row["id"]}. Must be exactly 11 characters. Nullifying.")
                        null
                    }
                } else null

                FitnessTestEntity(
                    id = row["id"]!!,
                    categoryId = row["categoryId"]!!,
                    name = row["name"]!!,
                    unit = row["unit"]!!,
                    isHigherBetter = row["isHigherBetter"]?.lowercase() == "true",
                    description = row["description"],
                    timingMode = row["timingMode"] ?: "MANUAL_ENTRY",
                    inputParadigm = row["inputParadigm"] ?: "NUMERIC",
                    athletesPerHeat = row["athletesPerHeat"]?.toIntOrNull(),
                    trialsPerAthlete = row["trialsPerAthlete"]?.toIntOrNull() ?: 1,
                    validMin = row["validMin"]?.toDoubleOrNull(),
                    validMax = row["validMax"]?.toDoubleOrNull(),
                    interpretationStrategy = row["interpretationStrategy"] ?: "NORM_LOOKUP",
                    calculationConfig = row["calculationConfig"],
                    youtubeId = validatedYoutubeId,
                    source = SEED_SOURCE
                )
            }

            val norms = normMaps.map { row ->
                NormReferenceEntity(
                    testId = row["testId"]!!,
                    variant = row["variant"] ?: "Default",
                    sex = BiologicalSex.valueOf(row["sex"]?.uppercase() ?: "MALE"),
                    ageMin = row["ageMin"]?.toFloatOrNull() ?: 0f,
                    ageMax = row["ageMax"]?.toFloatOrNull() ?: 99f,
                    minScore = row["minScore"]?.toDoubleOrNull() ?: 0.0,
                    maxScore = row["maxScore"]?.toDoubleOrNull() ?: 999.0,
                    percentile = row["percentile"]?.toIntOrNull() ?: 0,
                    classification = row["classification"],
                    source = SEED_SOURCE
                )
            }

            importStandardsUseCase(
                categories.map { it.toDomain() },
                tests.map { it.toDomain() },
                norms.map { it.toDomain() }
            )
            android.util.Log.d("SeedDataManager", "Successfully imported standards into database")

            // 2. Seed Test Recommendations from CSV (must run after standards import above,
            // since the cross-ref rows have a CASCADE FK to fitness_tests)
            try {
                val recoCategoryMaps = com.vamshi.field.util.CsvParser.parse(context.assets.open("recommendation_categories.csv"))
                val recoTestMaps = com.vamshi.field.util.CsvParser.parse(context.assets.open("recommendation_tests.csv"))

                val recoCategories = recoCategoryMaps.map { row ->
                    RecommendationCategory(
                        id = row["id"]!!,
                        name = row["name"]!!,
                        description = row["description"],
                        icon = row["icon"]?.takeIf { it.isNotBlank() },
                        scope = row["scope"]?.let {
                            try { RecommendationScope.valueOf(it) } catch (_: Exception) { RecommendationScope.POPULATION }
                        } ?: RecommendationScope.POPULATION,
                        sortOrder = row["sortOrder"]?.toIntOrNull() ?: 0
                    )
                }

                val recoLinks = recoTestMaps.map { row ->
                    RecommendationTestLink(
                        recommendationCategoryId = row["recommendationCategoryId"]!!,
                        testId = row["testId"]!!,
                        sortOrder = row["sortOrder"]?.toIntOrNull() ?: 0,
                        required = row["required"]?.lowercase() == "true"
                    )
                }

                android.util.Log.d("SeedDataManager", "Parsed ${recoCategories.size} recommendation categories, ${recoLinks.size} recommendation links")
                importRecommendationsUseCase(recoCategories, recoLinks, clearExisting = true)
                android.util.Log.d("SeedDataManager", "Successfully imported recommendations into database")
            } catch (e: Exception) {
                android.util.Log.e("SeedDataManager", "Error seeding recommendations from CSV", e)
                e.printStackTrace()
            }

            // --- Seed Dummy Athletes & Results ---
            val count = database.peopleDao().getIndividualCount()
            if (count == 0) {
                android.util.Log.d("SeedDataManager", "Seeding dummy athletes, groups, events, and results...")
                val peopleDao = database.peopleDao()
                val testingDao = database.testingDao()

                // 1. Groups
                val varsityGroup = GroupEntity(
                    id = "group_varsity_id",
                    name = "Varsity Football",
                    location = "Main Field",
                    cycle = "Fall 2026",
                    category = "TEAM"
                )
                val juniorGroup = GroupEntity(
                    id = "group_junior_id",
                    name = "Junior Basketball",
                    location = "Gymnasium",
                    cycle = "Winter 2026",
                    category = "TEAM"
                )
                peopleDao.insertGroup(varsityGroup)
                peopleDao.insertGroup(juniorGroup)

                // 2. Individuals (Athletes)
                val athletes = listOf(
                    IndividualEntity(
                        id = "athlete_alex",
                        firstName = "Alex",
                        lastName = "Mercer",
                        dateOfBirth = Calendar.getInstance().apply { set(2008, 0, 1) }.timeInMillis,
                        sex = BiologicalSex.MALE,
                        medicalAlert = null,
                        isRestricted = false
                    ),
                    IndividualEntity(
                        id = "athlete_sarah",
                        firstName = "Sarah",
                        lastName = "Connor",
                        dateOfBirth = Calendar.getInstance().apply { set(2009, 5, 12) }.timeInMillis,
                        sex = BiologicalSex.FEMALE,
                        medicalAlert = "Asthma",
                        isRestricted = false
                    ),
                    IndividualEntity(
                        id = "athlete_marcus",
                        firstName = "Marcus",
                        lastName = "Fenix",
                        dateOfBirth = Calendar.getInstance().apply { set(2007, 10, 20) }.timeInMillis,
                        sex = BiologicalSex.MALE,
                        medicalAlert = "Previous ACL Sprain",
                        isRestricted = false
                    ),
                    IndividualEntity(
                        id = "athlete_lara",
                        firstName = "Lara",
                        lastName = "Croft",
                        dateOfBirth = Calendar.getInstance().apply { set(2009, 2, 14) }.timeInMillis,
                        sex = BiologicalSex.FEMALE,
                        medicalAlert = null,
                        isRestricted = false
                    ),
                    IndividualEntity(
                        id = "athlete_john",
                        firstName = "John",
                        lastName = "Doe",
                        dateOfBirth = Calendar.getInstance().apply { set(2008, 11, 25) }.timeInMillis,
                        sex = BiologicalSex.MALE,
                        medicalAlert = null,
                        isRestricted = false
                    )
                )
                for (athlete in athletes) {
                    peopleDao.insertIndividual(athlete)
                }

                // 3. Group Memberships
                peopleDao.addMemberToGroup(GroupMemberCrossRef("group_varsity_id", "athlete_alex"))
                peopleDao.addMemberToGroup(GroupMemberCrossRef("group_varsity_id", "athlete_sarah"))
                peopleDao.addMemberToGroup(GroupMemberCrossRef("group_varsity_id", "athlete_marcus"))
                peopleDao.addMemberToGroup(GroupMemberCrossRef("group_varsity_id", "athlete_lara"))

                peopleDao.addMemberToGroup(GroupMemberCrossRef("group_junior_id", "athlete_lara"))
                peopleDao.addMemberToGroup(GroupMemberCrossRef("group_junior_id", "athlete_john"))

                // 4. Events
                val event1 = TestingEventEntity(
                    id = "event_benchmark_1",
                    groupId = "group_varsity_id",
                    name = "Summer Benchmark Testing",
                    date = System.currentTimeMillis() - 86400000L * 10L, // 10 days ago
                    location = "Main Field"
                )
                val event2 = TestingEventEntity(
                    id = "event_benchmark_2",
                    groupId = "group_varsity_id",
                    name = "Winter Combine Trials",
                    date = System.currentTimeMillis() - 86400000L * 30L, // 30 days ago
                    location = "Main Gym"
                )
                testingDao.insertEvent(event1)
                testingDao.insertEvent(event2)

                // 5. Link tests to events (IDs must exist in tests.csv — FK to fitness_tests)
                val testIds = listOf(
                    "test_pushup",
                    "test_pro_agility",
                    "test_pacer",
                    "test_1rm_squat",
                    "test_t_test"
                )
                for (testId in testIds) {
                    testingDao.addTestToEvent(EventTestCrossRef("event_benchmark_1", testId))
                    testingDao.addTestToEvent(EventTestCrossRef("event_benchmark_2", testId))
                }

                // 6. Test Results
                val results = listOf(
                    // Alex Mercer
                    TestResultEntity(eventId = "event_benchmark_1", individualId = "athlete_alex", testId = "test_pushup", rawScore = 65.0, ageAtTime = 18f, percentile = 85, classification = "SUPERIOR"),
                    TestResultEntity(eventId = "event_benchmark_1", individualId = "athlete_alex", testId = "test_pro_agility", rawScore = 4.75, ageAtTime = 18f, percentile = 90, classification = "SUPERIOR"),
                    TestResultEntity(eventId = "event_benchmark_1", individualId = "athlete_alex", testId = "test_pacer", rawScore = 50.0, ageAtTime = 18f, percentile = 75, classification = "HEALTHY"),
                    TestResultEntity(eventId = "event_benchmark_1", individualId = "athlete_alex", testId = "test_1rm_squat", rawScore = 140.0, ageAtTime = 18f, percentile = 80, classification = "HEALTHY"),
                    TestResultEntity(eventId = "event_benchmark_1", individualId = "athlete_alex", testId = "test_t_test", rawScore = 9.2, ageAtTime = 18f, percentile = 88, classification = "SUPERIOR"),

                    // Sarah Connor
                    TestResultEntity(eventId = "event_benchmark_1", individualId = "athlete_sarah", testId = "test_pushup", rawScore = 48.0, ageAtTime = 17f, percentile = 78, classification = "HEALTHY"),
                    TestResultEntity(eventId = "event_benchmark_1", individualId = "athlete_sarah", testId = "test_pro_agility", rawScore = 5.25, ageAtTime = 17f, percentile = 65, classification = "HEALTHY"),
                    TestResultEntity(eventId = "event_benchmark_1", individualId = "athlete_sarah", testId = "test_pacer", rawScore = 41.0, ageAtTime = 17f, percentile = 82, classification = "SUPERIOR"),
                    TestResultEntity(eventId = "event_benchmark_1", individualId = "athlete_sarah", testId = "test_1rm_squat", rawScore = 95.0, ageAtTime = 17f, percentile = 72, classification = "HEALTHY"),
                    TestResultEntity(eventId = "event_benchmark_1", individualId = "athlete_sarah", testId = "test_t_test", rawScore = 9.9, ageAtTime = 17f, percentile = 74, classification = "HEALTHY"),

                    // Marcus Fenix
                    TestResultEntity(eventId = "event_benchmark_1", individualId = "athlete_marcus", testId = "test_pushup", rawScore = 32.0, ageAtTime = 19f, percentile = 25, classification = "NEEDS_IMPROVEMENT"),
                    TestResultEntity(eventId = "event_benchmark_1", individualId = "athlete_marcus", testId = "test_pro_agility", rawScore = 5.95, ageAtTime = 19f, percentile = 20, classification = "NEEDS_IMPROVEMENT"),
                    TestResultEntity(eventId = "event_benchmark_1", individualId = "athlete_marcus", testId = "test_pacer", rawScore = 26.0, ageAtTime = 19f, percentile = 28, classification = "NEEDS_IMPROVEMENT"),
                    TestResultEntity(eventId = "event_benchmark_1", individualId = "athlete_marcus", testId = "test_1rm_squat", rawScore = 165.0, ageAtTime = 19f, percentile = 95, classification = "SUPERIOR"),
                    TestResultEntity(eventId = "event_benchmark_1", individualId = "athlete_marcus", testId = "test_t_test", rawScore = 11.1, ageAtTime = 19f, percentile = 22, classification = "NEEDS_IMPROVEMENT"),

                    // Lara Croft
                    TestResultEntity(eventId = "event_benchmark_1", individualId = "athlete_lara", testId = "test_pushup", rawScore = 58.0, ageAtTime = 17f, percentile = 94, classification = "SUPERIOR"),
                    TestResultEntity(eventId = "event_benchmark_1", individualId = "athlete_lara", testId = "test_pro_agility", rawScore = 4.98, ageAtTime = 17f, percentile = 92, classification = "SUPERIOR"),
                    TestResultEntity(eventId = "event_benchmark_1", individualId = "athlete_lara", testId = "test_pacer", rawScore = 47.0, ageAtTime = 17f, percentile = 96, classification = "SUPERIOR"),
                    TestResultEntity(eventId = "event_benchmark_1", individualId = "athlete_lara", testId = "test_1rm_squat", rawScore = 110.0, ageAtTime = 17f, percentile = 90, classification = "SUPERIOR"),
                    TestResultEntity(eventId = "event_benchmark_1", individualId = "athlete_lara", testId = "test_t_test", rawScore = 8.9, ageAtTime = 17f, percentile = 95, classification = "SUPERIOR")
                )
                for (res in results) {
                    testingDao.insertResult(res)
                }
                android.util.Log.d("SeedDataManager", "Successfully seeded dummy athletes, groups, events, and results!")
            }
        } catch (e: Exception) {
            android.util.Log.e("SeedDataManager", "Error seeding from CSV", e)
            e.printStackTrace()
        }

        prefs.edit().putBoolean(KEY_DATA_SEEDED, true).apply()
        android.util.Log.d("SeedDataManager", "Seeding complete")
    }
}
