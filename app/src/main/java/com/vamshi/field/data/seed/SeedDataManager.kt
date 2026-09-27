package com.vamshi.field.data.seed

import android.content.Context
import android.util.Log
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
        private const val TAG = "SeedDataManager"
        private const val PREFS_NAME = "alearning_prefs"

        // Bumping this key re-runs seedIfNeeded() on next launch.
        // This is non-destructive for user data: catalog tests/categories are upserted in place,
        // and norm_references and recommendation tables are safely updated.
        // v30: norms re-encoded so "Needs Improvement" carries a percentile below the Healthy
        // threshold (was 40, which classified as Healthy), top bracket extended to 115, and
        // duplicate legacy age bands removed from wall-sit and shoulder-flexibility.
        private const val KEY_SEEDED_VERSION = "data_seeded_version_v30"

        // One-shot, and deliberately NOT versioned like [KEY_SEEDED_VERSION]. The demo roster
        // is a first-run convenience, not catalog data: it must be offered exactly once per
        // install and never again, including across catalog revisions that re-run the seeder.
        // An empty `individuals` table is not a substitute for this flag -- it is equally the
        // state of a coach who deliberately deleted the demo athletes.
        private const val KEY_DEMO_DATA_SEEDED = "demo_data_seeded"
        private const val SEED_SOURCE = "SEED"
    }

    suspend fun seedIfNeeded() {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val isAlreadySeeded = try {
                prefs.getBoolean(KEY_SEEDED_VERSION, false)
            } catch (e: Exception) {
                false
            }

            val testCount = try { database.standardsDao().getAllTestsOnce().size } catch (_: Exception) { 0 }
            val normCount = try { database.standardsDao().getNormCount() } catch (_: Exception) { 0 }
            val athleteCount = try { database.peopleDao().getIndividualCount() } catch (_: Exception) { 0 }

            Log.d(TAG, "seedIfNeeded: isAlreadySeeded=$isAlreadySeeded, testCount=$testCount, normCount=$normCount, athleteCount=$athleteCount")

            // Fast path: this build's catalog has already been applied, so the CSV import
            // would rebuild byte-identical data. Two distinct questions, deliberately not
            // conflated:
            //
            //  - "Is this catalog version already applied?" -> [KEY_SEEDED_VERSION] alone can
            //    answer it. A row count cannot: a catalog revision that corrects a percentile
            //    without adding or removing rows leaves every count unchanged.
            //  - "Is a catalog present at all?" -> a presence check (> 0), never a completeness
            //    threshold. The previous `normCount >= 2500` asserted a specific size, and the
            //    v30 de-duplication dropped norms.csv from 2519 to 2453 rows, so the condition
            //    became unsatisfiable and this fast path was dead: every cold start re-parsed
            //    three CSVs and rewrote ~2.4k norm rows to reach identical data.
            //
            // athleteCount is deliberately NOT a condition here. Athlete deletion is a hard
            // @Delete and getIndividualCount() counts unfiltered, so a coach who clears the
            // preloaded demo roster before adding their own team drops it to 0 -- which used to
            // fail this guard, fall through to step 3, and resurrect all five demo athletes plus
            // their groups, events and results on the next launch. Step 3 carries its own
            // `currentAthleteCount == 0` check, so nothing here needs to repeat it.
            if (isAlreadySeeded && normCount > 0 && testCount > 0) {
                Log.d(TAG, "Catalog already seeded for this version: $testCount tests, $normCount norms. Skipping runtime CSV seeding.")
                return
            }

            Log.d(TAG, "Starting standards and recommendations seeding from CSV...")

            // 1. Seed Test Library from CSV
            try {
                val categoryMaps = com.vamshi.field.util.CsvParser.parse(context.assets.open("test_categories.csv"))
                val testMaps = com.vamshi.field.util.CsvParser.parse(context.assets.open("tests.csv"))
                val normsStream = try {
                    context.assets.open("norms.csv")
                } catch (_: Exception) {
                    context.assets.open("norms_v2.csv")
                }
                val normMaps = com.vamshi.field.util.CsvParser.parse(normsStream)


                    Log.d(TAG, "Parsed ${categoryMaps.size} categories, ${testMaps.size} tests, ${normMaps.size} norms")

                    val categories = categoryMaps.map { row ->
                        TestCategoryEntity(
                            id = row["id"]!!,
                            name = row["name"]!!,
                            description = row["description"]?.trim()?.ifEmpty { null },
                            sortOrder = row["sortOrder"]?.toIntOrNull() ?: 0,
                            radarAxis = row["radarAxis"],
                            source = SEED_SOURCE
                        )
                    }

                    val tests = testMaps.map { row ->
                        FitnessTestEntity(
                            id = row["id"]!!,
                            categoryId = row["categoryId"]!!,
                            name = row["name"]!!,
                            unit = row["unit"]?.trim()?.ifEmpty { "units" } ?: "units",
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
                            youtubeId = row["youtube_id"]?.trim()?.takeIf { it.length == 11 },
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
                    Log.d(TAG, "Successfully seeded ${categories.size} categories, ${tests.size} tests, and ${norms.size} norms")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to seed standards catalog", e)
                }

                // 2. Seed Recommendations
                try {
                    Log.d(TAG, "Seeding recommendations...")
                    val recCategoryMaps = com.vamshi.field.util.CsvParser.parse(context.assets.open("recommendation_categories.csv"))
                    val recTestMaps = com.vamshi.field.util.CsvParser.parse(context.assets.open("recommendation_tests.csv"))

                    val recCategories = recCategoryMaps.map { row ->
                        RecommendationCategory(
                            id = row["id"]!!,
                            name = row["name"]!!,
                            description = row["description"]?.trim()?.ifEmpty { null },
                            icon = row["icon"]?.trim()?.ifEmpty { null },
                            scope = try { RecommendationScope.valueOf(row["scope"]?.uppercase() ?: "POPULATION") } catch (_: Exception) { RecommendationScope.POPULATION },
                            sortOrder = row["sortOrder"]?.toIntOrNull() ?: 0
                        )
                    }

                    val recTestLinks = recTestMaps.map { row ->
                        RecommendationTestLink(
                            recommendationCategoryId = row["recommendationCategoryId"]!!,
                            testId = row["testId"]!!,
                            sortOrder = row["sortOrder"]?.toIntOrNull() ?: 0,
                            required = row["required"]?.uppercase() == "TRUE"
                        )
                    }

                    importRecommendationsUseCase(
                        categories = recCategories,
                        links = recTestLinks,
                        clearExisting = true
                    )
                    Log.d(TAG, "Successfully seeded ${recCategories.size} recommendation categories and ${recTestLinks.size} test links")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to seed recommendations", e)
                }

                prefs.edit().putBoolean(KEY_SEEDED_VERSION, true).apply()

            // 3. Seed the demo roster -- first run only, never again.
            //
            // Gated on [KEY_DEMO_DATA_SEEDED] rather than on the roster being empty. Athlete
            // deletion is a hard @Delete, so "no athletes" is ambiguous: it is the state of a
            // fresh install AND the state of a coach who cleared the demo roster to make room
            // for their real team. Re-seeding on that signal resurrects five demo athletes, two
            // groups, ten testing events and two hundred results under them.
            //
            // The flag is set once this has been settled either way, so an install that already
            // carries the prepackaged demo roster records it as done without re-inserting, and
            // an existing install upgrading into this build settles on its first launch.
            try {
                val demoDataSettled = try {
                    prefs.getBoolean(KEY_DEMO_DATA_SEEDED, false)
                } catch (e: Exception) {
                    false
                }
                val currentAthleteCount = database.peopleDao().getIndividualCount()
                if (!demoDataSettled && currentAthleteCount == 0) {
                    Log.d(TAG, "Seeding preloaded athletes, groups, events, and results...")
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

                    // 4. Events & Test Results across 10 benchmark testing rounds
                    val testIds = listOf("test_push_up", "test_5_10_5_shuttle_run", "test_shuttle_run", "test_1rm_squat", "test_t")
                    val baseScores = mapOf(
                        "athlete_alex" to mapOf("test_push_up" to 40.0, "test_5_10_5_shuttle_run" to 5.5, "test_shuttle_run" to 30.0, "test_1rm_squat" to 100.0, "test_t" to 11.0),
                        "athlete_sarah" to mapOf("test_push_up" to 25.0, "test_5_10_5_shuttle_run" to 6.0, "test_shuttle_run" to 25.0, "test_1rm_squat" to 60.0, "test_t" to 12.0),
                        "athlete_marcus" to mapOf("test_push_up" to 15.0, "test_5_10_5_shuttle_run" to 6.5, "test_shuttle_run" to 15.0, "test_1rm_squat" to 120.0, "test_t" to 13.0),
                        "athlete_lara" to mapOf("test_push_up" to 35.0, "test_5_10_5_shuttle_run" to 5.8, "test_shuttle_run" to 32.0, "test_1rm_squat" to 80.0, "test_t" to 10.5)
                    )

                    val improvementFactor = mapOf(
                        "test_push_up" to 2.5,
                        "test_5_10_5_shuttle_run" to -0.08,
                        "test_shuttle_run" to 2.0,
                        "test_1rm_squat" to 4.0,
                        "test_t" to -0.2
                    )

                    val currentTime = System.currentTimeMillis()
                    val oneYearMs = 31536000000L
                    val msPerEvent = oneYearMs / 10

                    val resultsToInsert = mutableListOf<TestResultEntity>()
                    for (i in 0 until 10) {
                        val eventId = "event_benchmark_$i"
                        val eventDate = currentTime - oneYearMs + (i * msPerEvent)

                        val event = TestingEventEntity(
                            id = eventId,
                            groupId = "group_varsity_id",
                            name = "Benchmark Testing Round ${i + 1}",
                            date = eventDate,
                            location = "Main Field",
                            createdAt = eventDate
                        )
                        testingDao.insertEvent(event)

                        for (testId in testIds) {
                            testingDao.addTestToEvent(EventTestCrossRef(eventId, testId))
                        }

                        for ((athleteId, scores) in baseScores) {
                            for (testId in testIds) {
                                val baseScore = scores[testId] ?: continue
                                val factor = improvementFactor[testId] ?: 0.0

                                val noise = (Math.random() - 0.5) * Math.abs(factor)
                                val finalScore = baseScore + (factor * i) + noise

                                val isHigherBetter = factor > 0
                                val pctBase = if (isHigherBetter) 30 + (i * 5) else 30 + (i * 5)
                                val percentile = (pctBase + (Math.random() * 10).toInt()).coerceIn(1, 99)

                                val classification = when {
                                    percentile >= com.vamshi.field.domain.model.reports.PerformanceThresholds.SUPERIOR_MIN -> "SUPERIOR"
                                    percentile >= com.vamshi.field.domain.model.reports.PerformanceThresholds.HEALTHY_MIN -> "HEALTHY"
                                    else -> "NEEDS_IMPROVEMENT"
                                }

                                resultsToInsert += TestResultEntity(
                                    eventId = eventId,
                                    individualId = athleteId,
                                    testId = testId,
                                    rawScore = (finalScore * 100.0).toLong() / 100.0,
                                    ageAtTime = 18f,
                                    percentile = percentile,
                                    classification = classification,
                                    createdAt = eventDate
                                )
                            }
                        }
                    }
                    testingDao.insertResults(resultsToInsert)
                    Log.d(TAG, "Successfully seeded preloaded athletes, groups, events, and testing results!")
                }
                if (!demoDataSettled) {
                    // Set whether or not rows were inserted: both branches mean "the demo roster
                    // question is answered for this install".
                    prefs.edit().putBoolean(KEY_DEMO_DATA_SEEDED, true).apply()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to seed dummy athletes and results", e)
            }

            Log.d(TAG, "Seeding pipeline finished.")
        } catch (e: Throwable) {
            Log.e(TAG, "Unexpected error in seedIfNeeded", e)
        }
    }
}
