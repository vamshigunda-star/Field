package com.vamshi.field.ui.testing

import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.vamshi.field.data.storage.CustomPresetsStore
import com.vamshi.field.domain.model.people.Group
import com.vamshi.field.domain.model.people.GroupCategory
import com.vamshi.field.domain.model.standards.FitnessTest
import com.vamshi.field.domain.model.standards.RecommendationCategory
import com.vamshi.field.domain.model.standards.RecommendationTestLink
import com.vamshi.field.domain.repository.RecommendationRepository
import com.vamshi.field.domain.usecase.standards.FakeStandardsRepository
import com.vamshi.field.domain.usecase.standards.GetRecommendationsUseCase
import com.vamshi.field.domain.usecase.standards.GetTestLibraryUseCase
import com.vamshi.field.domain.usecase.testing.CreateEventUseCase
import com.vamshi.field.domain.usecase.testing.FakePeopleRepository
import com.vamshi.field.domain.usecase.testing.FakeTestingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Create Testing Event: the "what's missing" hint and the jump to the missing field.
 * Spec: specs/2026-11-26-create-event-collapsing-details.md. Robolectric for CustomPresetsStore's Context.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CreateEventMissingRequirementTest {

    private val dispatcher = StandardTestDispatcher()
    private val standards = FakeStandardsRepository()
    private val people = FakePeopleRepository()
    private val testing = FakeTestingRepository()
    private val sprint = FitnessTest(id = "t1", categoryId = "cat_speed", name = "Sprint", unit = "seconds", isHigherBetter = false)
    private val group = Group(id = "g1", name = "Grade 8A", location = null, cycle = null, category = GroupCategory.TEAM)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        standards.givenTests(sprint)
        people.givenGroupWithAthletes(group, emptyList())
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `missing requirement follows the on-screen order`() {
        assertEquals(MissingRequirement.GROUP, missingRequirement(null, "", emptySet()))
        assertEquals(MissingRequirement.EVENT_NAME, missingRequirement("g1", "   ", emptySet()))
        assertEquals(MissingRequirement.TESTS, missingRequirement("g1", "Spring", emptySet()))
        assertNull(missingRequirement("g1", "Spring", setOf("t1")))
    }

    @Test
    fun `the hint tracks the coach's choices`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(MissingRequirement.GROUP, vm.uiState.value.missingRequirement)

        vm.onAction(CreateEventAction.SelectGroup("g1"))
        assertEquals(MissingRequirement.EVENT_NAME, vm.uiState.value.missingRequirement)
        assertEquals("Grade 8A", vm.uiState.value.selectedGroupName)

        vm.onAction(CreateEventAction.SetEventName("Spring"))
        vm.onAction(CreateEventAction.ToggleTest("t1"))
        assertNull(vm.uiState.value.missingRequirement)
    }

    @Test
    fun `start with no name jumps to the name and closes the open category`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onAction(CreateEventAction.SelectGroup("g1"))
        vm.onAction(CreateEventAction.ToggleCategoryExpanded("cat_speed"))
        vm.onAction(CreateEventAction.ToggleTest("t1"))

        vm.onAction(CreateEventAction.CreateEvent)

        val state = vm.uiState.value
        assertEquals(MissingRequirement.EVENT_NAME, state.jumpTo)
        assertNull("the fields live above the categories, so the category closes", state.expandedCategoryId)
        assertNull("the hint replaces the snackbar", state.errorMessage)

        vm.onAction(CreateEventAction.JumpConsumed)
        assertNull(vm.uiState.value.jumpTo)
    }

    @Test
    fun `tapping the summary bar closes the open category`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onAction(CreateEventAction.ToggleCategoryExpanded("cat_speed"))

        vm.onAction(CreateEventAction.CollapseCategories)

        assertNull(vm.uiState.value.expandedCategoryId)
    }

    @Test
    fun `recommended tests clear the tests hint`() = runTest(dispatcher) {
        val vm = viewModel(recommendationId = "reco")
        advanceUntilIdle()
        vm.onAction(CreateEventAction.SelectGroup("g1"))
        vm.onAction(CreateEventAction.SetEventName("Spring"))

        assertNull(vm.uiState.value.missingRequirement)
    }

    private fun viewModel(recommendationId: String? = null) = CreateEventViewModel(
        peopleRepository = people,
        getTestLibrary = GetTestLibraryUseCase(standards),
        getRecommendations = GetRecommendationsUseCase(object : RecommendationRepository {
            override fun getRecommendationCategories(): Flow<List<RecommendationCategory>> = flowOf(emptyList())
            override fun getRecommendedTests(categoryId: String): Flow<List<FitnessTest>> = flowOf(listOf(sprint))
            override suspend fun importRecommendations(
                categories: List<RecommendationCategory>,
                links: List<RecommendationTestLink>
            ) = Unit
            override suspend fun clearAllRecommendations() = Unit
        }),
        createEventUseCase = CreateEventUseCase(testing),
        customPresetsStore = CustomPresetsStore(ApplicationProvider.getApplicationContext()),
        savedStateHandle = SavedStateHandle(
            if (recommendationId != null) mapOf("recommendationId" to recommendationId) else emptyMap()
        )
    )
}
