package com.vamshi.field.di

import com.vamshi.field.data.repository.PeopleRepositoryImpl
import com.vamshi.field.data.repository.RecommendationRepositoryImpl
import com.vamshi.field.data.repository.ReportsRepositoryImpl
import com.vamshi.field.data.repository.StandardsRepositoryImpl
import com.vamshi.field.data.repository.TestingRepositoryImpl
import com.vamshi.field.domain.repository.PeopleRepository
import com.vamshi.field.domain.repository.RecommendationRepository
import com.vamshi.field.domain.repository.ReportsRepository
import com.vamshi.field.domain.repository.StandardsRepository
import com.vamshi.field.domain.repository.TestingRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindPeopleRepository(
        peopleRepositoryImpl: PeopleRepositoryImpl
    ): PeopleRepository

    @Binds
    @Singleton
    abstract fun bindStandardsRepository(
        standardsRepositoryImpl: StandardsRepositoryImpl
    ): StandardsRepository

    @Binds
    @Singleton
    abstract fun bindTestingRepository(
        testingRepositoryImpl: TestingRepositoryImpl
    ): TestingRepository

    @Binds
    @Singleton
    abstract fun bindReportsRepository(
        reportsRepositoryImpl: ReportsRepositoryImpl
    ): ReportsRepository

    @Binds
    @Singleton
    abstract fun bindRecommendationRepository(
        recommendationRepositoryImpl: RecommendationRepositoryImpl
    ): RecommendationRepository

}
