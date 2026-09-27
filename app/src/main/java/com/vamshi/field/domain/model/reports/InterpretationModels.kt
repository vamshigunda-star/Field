package com.vamshi.field.domain.model.reports

enum class Classification { SUPERIOR, HEALTHY, NEEDS_IMPROVEMENT, NO_DATA }

data class Distribution(
    val superior: Int,
    val healthy: Int,
    val needsImprovement: Int,
    val noData: Int
) {
    val total: Int get() = superior + healthy + needsImprovement + noData
}

data class AthleteFlag(
    val individualId: String,
    val athleteName: String,
    val groupId: String,
    val groupName: String,
    val type: FlagType,
    val message: String,
    val testId: String? = null,
    val testName: String? = null,
    val testIds: List<String> = emptyList(),
    val testNames: List<String> = emptyList()
)

enum class FlagType { BELOW_HEALTHY, REGRESSION, ABSENT, MISSING_DATA }
