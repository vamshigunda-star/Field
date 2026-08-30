package com.vamshi.field.data.local.daos.testing

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Upsert
import com.vamshi.field.data.local.entities.people.IndividualEntity
import com.vamshi.field.data.local.entities.standards.FitnessTestEntity
import com.vamshi.field.data.local.entities.testing.EventTestCrossRef
import com.vamshi.field.data.local.entities.testing.TestResultEntity
import com.vamshi.field.data.local.entities.testing.TestingEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TestingDao {

    // --- EVENTS ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: TestingEventEntity)

    @Query("SELECT * FROM testing_events ORDER BY date DESC")
    fun getAllEvents(): Flow<List<TestingEventEntity>>

    @Query("SELECT * FROM testing_events WHERE id = :eventId LIMIT 1")
    suspend fun getEventById(eventId: String): TestingEventEntity?

    @Query("SELECT * FROM testing_events WHERE id = :eventId LIMIT 1")
    fun getEventByIdFlow(eventId: String): Flow<TestingEventEntity?>

    // Feature: Show only events for a specific group (e.g., "Varsity History")
    @Query("SELECT * FROM testing_events WHERE groupId = :groupId ORDER BY date DESC")
    fun getEventsForGroup(groupId: String): Flow<List<TestingEventEntity>>

    @Query("DELETE FROM testing_events WHERE id = :eventId")
    suspend fun deleteEventById(eventId: String)

    // --- MENU BUILDING (Linking Tests to Events) ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addTestToEvent(crossRef: EventTestCrossRef)

    /**
     * Feature: "What tests are we doing today?"
     * Joins the CrossRef with FitnessTest table to give you the actual Test Objects for the UI.
     */
    @Query("""
        SELECT fitness_tests.* FROM fitness_tests
        INNER JOIN event_test_cross_ref ON fitness_tests.id = event_test_cross_ref.testId
        WHERE event_test_cross_ref.eventId = :eventId
        ORDER BY event_test_cross_ref.sortOrder ASC
    """)
    fun getTestsForEvent(eventId: String): Flow<List<FitnessTestEntity>>

    // --- RESULTS (Scoring) ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResult(result: TestResultEntity)

    // Feature: History Chart (Progress over time for one person on one test)
    @Query("""
        SELECT * FROM test_results 
        WHERE individualId = :individualId AND testId = :testId 
        ORDER BY createdAt ASC
    """)
    fun getHistoryForTest(individualId: String, testId: String): Flow<List<TestResultEntity>>

    // Feature: Leaderboard (All results for a specific event)
    @Query("SELECT * FROM test_results WHERE eventId = :eventId ORDER BY createdAt DESC")
    fun getEventResults(eventId: String): Flow<List<TestResultEntity>>

    @Query("SELECT * FROM test_results")
    fun getAllResults(): Flow<List<TestResultEntity>>

    @Query("""
    SELECT * FROM test_results 
    WHERE individualId = :individualId 
    ORDER BY createdAt DESC
""")
    fun getAllResultsForIndividual(individualId: String): Flow<List<TestResultEntity>>


    @Query("""
    SELECT * FROM test_results
    WHERE individualId = :individualId
    AND createdAt = (
        SELECT MAX(createdAt) 
        FROM test_results AS inner_results
        WHERE inner_results.individualId = :individualId
        AND inner_results.testId = test_results.testId)""")
    suspend fun getLatestResultPerTestForIndividual(individualId: String): List<TestResultEntity>

    @Query("""
    SELECT r.* FROM test_results r
    INNER JOIN (
        SELECT individualId, testId, MAX(createdAt) AS maxCreated
        FROM test_results
        GROUP BY individualId, testId
    ) latest ON r.individualId = latest.individualId 
            AND r.testId = latest.testId 
            AND r.createdAt = latest.maxCreated
    """)
    fun getAllLatestResults(): Flow<List<TestResultEntity>>

    @Query("""
    SELECT r.* FROM test_results r
    INNER JOIN (
        SELECT individualId, testId, MAX(createdAt) AS maxCreated
        FROM test_results
        GROUP BY individualId, testId
    ) latest ON r.individualId = latest.individualId 
            AND r.testId = latest.testId 
            AND r.createdAt = latest.maxCreated
    """)
    suspend fun getAllLatestResultsOnce(): List<TestResultEntity>

    @Query("""
    SELECT test_results.* FROM test_results
    INNER JOIN group_members ON test_results.individualId = group_members.individualId
    WHERE group_members.groupId = :groupId 
    AND test_results.testId = :testId
    ORDER BY test_results.createdAt DESC
""")
    fun getGroupResultsForTest(groupId: String, testId: String): Flow<List<TestResultEntity>>

    // --- STOPWATCH SUPPORT ---

    @Query(
        """
        SELECT i.* FROM individuals i
        INNER JOIN group_members gm ON i.id = gm.individualId
        WHERE gm.groupId = :groupId AND i.isActive = 1
        ORDER BY i.lastName ASC, i.firstName ASC
    """
    )
    suspend fun getAthletesInGroupOrdered(groupId: String): List<IndividualEntity>

    @Query("""
        SELECT COUNT(*) FROM test_results
        WHERE eventId = :eventId AND individualId = :individualId AND testId = :testId
    """)
    suspend fun getTrialCountForAthlete(eventId: String, individualId: String, testId: String): Int

    @Upsert
    suspend fun insertResults(results: List<TestResultEntity>)

    @Query("DELETE FROM test_results WHERE id = :resultId")
    suspend fun deleteResultById(resultId: String)
}
