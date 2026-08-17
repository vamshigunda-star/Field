package com.vamshi.field.data.repository

import android.util.Log
import com.vamshi.field.data.local.daos.standards.RecommendationDao
import com.vamshi.field.data.mapper.standards.toDomain
import com.vamshi.field.data.mapper.standards.toEntity
import com.vamshi.field.domain.model.standards.FitnessTest
import com.vamshi.field.domain.model.standards.RecommendationCategory
import com.vamshi.field.domain.model.standards.RecommendationTestLink
import com.vamshi.field.domain.repository.RecommendationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RecommendationRepositoryImpl @Inject constructor(
    private val dao: RecommendationDao
) : RecommendationRepository {

    override fun getRecommendationCategories(): Flow<List<RecommendationCategory>> {
        return dao.getAllCategories().map { list -> list.map { it.toDomain() } }
    }

    override fun getRecommendedTests(categoryId: String): Flow<List<FitnessTest>> {
        return dao.getTestsForCategory(categoryId).map { list -> list.map { it.toDomain() } }
    }

    override suspend fun importRecommendations(
        categories: List<RecommendationCategory>,
        links: List<RecommendationTestLink>
    ) {
        categories.forEach { category ->
            try {
                dao.insertCategory(category.toEntity())
            } catch (e: Exception) {
                Log.e("RecommendationRepositoryImpl", "Failed to insert recommendation category ${category.id}: ${e.message}")
            }
        }
        // Insert each link independently: a single row referencing a testId that isn't in
        // fitness_tests (an FK violation) must not abort every category listed after it.
        var inserted = 0
        links.forEach { link ->
            try {
                dao.addTestToCategory(link.toEntity())
                inserted++
            } catch (e: Exception) {
                Log.e(
                    "RecommendationRepositoryImpl",
                    "Skipping recommendation link ${link.recommendationCategoryId} -> ${link.testId}: ${e.message}"
                )
            }
        }
        // Summary line so partial imports are visible in one greppable place: a count
        // mismatch means recommendation_tests.csv references testIds missing from tests.csv.
        if (inserted < links.size) {
            Log.w(
                "RecommendationRepositoryImpl",
                "Imported only $inserted of ${links.size} recommendation links — check recommendation_tests.csv against tests.csv"
            )
        } else {
            Log.d("RecommendationRepositoryImpl", "Imported all $inserted recommendation links")
        }
    }

    override suspend fun clearAllRecommendations() {
        dao.deleteAllCrossRefs()
        dao.deleteAllCategories()
    }
}
