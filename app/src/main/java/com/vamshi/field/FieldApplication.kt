package com.vamshi.field

import android.app.Application
import android.util.Log
import com.vamshi.field.data.seed.SeedDataManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class FieldApplication : Application() {

    @Inject lateinit var seedDataManager: SeedDataManager

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        Log.e("FieldApplication", "onCreate started")
        super.onCreate()
        Log.e("FieldApplication", "super.onCreate finished")
        applicationScope.launch {
            seedDataManager.seedIfNeeded()
        }
    }
}
