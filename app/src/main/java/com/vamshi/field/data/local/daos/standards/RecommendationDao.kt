package com.vamshi.field.data.local.daos.standards

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Upsert
import com.vamshi.field.data.local.entities.standards.FitnessTestEntity
import com.vamshi.field.data.local.entities.standards.RecommendationCategoryEntity
import com.vamshi.field.data.local.entities.standards.RecommendationTestCrossRef
import kotlinx.coroutines.flow.Flow

@Dao
interface RecommendationDao {

    @Query("SELECT * FROM recommendation_categories ORDER BY sortOrder ASC")
    fun getAllCategories(): Flow<List<RecommendationCategoryEntity>>

    @Query(
        """
        SELECT fitness_tests.* FROM fitness_tests
        INNER JOIN recommendation_test_cross_ref ON fitness_tests.id = recommendation_test_cross_ref.testId
        WHERE recommendation_test_cross_ref.recommendationCategoryId = :categoryId
        ORDER BY recommendation_test_cross_ref.sortOrder ASC
        """
    )
    fun getTestsForCategory(categoryId: String): Flow<List<FitnessTestEntity>>

    @Upsert
    suspend fun insertCategory(category: RecommendationCategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addTestToCategory(crossRef: RecommendationTestCrossRef)

    @Query("DELETE FROM recommendation_test_cross_ref")
    suspend fun deleteAllCrossRefs()

    @Query("DELETE FROM recommendation_categories")
    suspend fun deleteAllCategories()
}
