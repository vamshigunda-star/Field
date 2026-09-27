package com.vamshi.field.di

import com.vamshi.field.data.logging.AndroidAppLogger
import com.vamshi.field.domain.logging.AppLogger
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LoggingModule {

    @Binds
    @Singleton
    abstract fun bindAppLogger(
        androidAppLogger: AndroidAppLogger
    ): AppLogger
}
