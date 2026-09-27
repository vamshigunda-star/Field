package com.vamshi.field.data.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class OnboardingPreferencesStoreTest {

    private lateinit var store: OnboardingPreferencesStore

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Clear prefs before each test
        context.getSharedPreferences("alearning_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        store = OnboardingPreferencesStore(context)
    }

    @Test
    fun gettingStartedDismissed_defaultsToFalse_andUpdatesCorrectly() = runTest {
        assertFalse(store.observeGettingStartedDismissed().first())
        store.setGettingStartedDismissed(true)
        assertTrue(store.observeGettingStartedDismissed().first())
    }
}
