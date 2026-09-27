package com.vamshi.field.data.storage

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SharedPreferences-backed reactive store for onboarding checklist state.
 */
@Singleton
class OnboardingPreferencesStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "alearning_prefs"
        private const val KEY_GETTING_STARTED_DISMISSED = "getting_started_dismissed"
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun observeGettingStartedDismissed(): Flow<Boolean> = callbackFlow {
        trySend(prefs.getBoolean(KEY_GETTING_STARTED_DISMISSED, false))
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
            if (changedKey == KEY_GETTING_STARTED_DISMISSED) {
                trySend(prefs.getBoolean(KEY_GETTING_STARTED_DISMISSED, false))
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    suspend fun setGettingStartedDismissed(dismissed: Boolean) = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean(KEY_GETTING_STARTED_DISMISSED, dismissed).apply()
    }
}
