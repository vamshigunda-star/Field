package com.vamshi.field.domain.usecase.standards

import com.vamshi.field.domain.model.standards.FitnessTest
import com.vamshi.field.domain.model.standards.TestSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DeleteCustomTestUseCaseTest {

    private lateinit var repository: FakeStandardsRepository
    private lateinit var delete: DeleteCustomTestUseCase

    @Before
    fun setUp() {
        repository = FakeStandardsRepository()
        delete = DeleteCustomTestUseCase(repository)
    }

    private fun test(id: String, source: TestSource) = FitnessTest(
        id = id,
        categoryId = "cat_agility",
        name = "Sled Push 20m",
        unit = "sec",
        isHigherBetter = false,
        source = source
    )

    @Test
    fun `a custom test with no results is deleted outright`() = runTest {
        repository.givenTests(test("custom_1", TestSource.USER))

        assertEquals(DeleteCustomTestResult.Deleted, delete("custom_1"))
        assertEquals(null, repository.storedTest("custom_1"))
    }

    @Test
    fun `a custom test with results is archived rather than deleted`() = runTest {
        repository.givenTests(test("custom_1", TestSource.USER))
        repository.resultCounts["custom_1"] = 3

        assertEquals(DeleteCustomTestResult.Archived(resultCount = 3), delete("custom_1"))
        assertTrue(repository.isArchived("custom_1"))
    }

    @Test
    fun `an archived test disappears from browsing but still resolves by id for reports`() = runTest {
        repository.givenTests(test("custom_1", TestSource.USER))
        repository.resultCounts["custom_1"] = 1

        delete("custom_1")

        assertEquals(
            "archived tests must not appear in the library or event setup",
            emptyList<FitnessTest>(),
            repository.getAllTests().first()
        )
        assertNotNull(
            "an old session report must still be able to name the test",
            repository.getTestById("custom_1")
        )
    }

    @Test
    fun `a seeded test cannot be deleted or archived`() = runTest {
        repository.givenTests(test("test_pacer", TestSource.SEED))
        repository.resultCounts["test_pacer"] = 0

        assertEquals(DeleteCustomTestResult.NotAllowed, delete("test_pacer"))
        assertNotNull(repository.storedTest("test_pacer"))
    }

    @Test
    fun `an unknown id is reported as not allowed rather than throwing`() = runTest {
        assertEquals(DeleteCustomTestResult.NotAllowed, delete("does-not-exist"))
    }

    // --- preview: same decision, no side effects ---

    @Test
    fun `preview reports what confirming would do without doing it`() = runTest {
        repository.givenTests(test("custom_1", TestSource.USER), test("custom_2", TestSource.USER))
        repository.resultCounts["custom_2"] = 3

        assertEquals(DeleteCustomTestPreview.WillDelete, delete.preview("custom_1"))
        assertEquals(DeleteCustomTestPreview.WillArchive(3), delete.preview("custom_2"))

        // Nothing was deleted or archived by previewing.
        assertNotNull(repository.storedTest("custom_1"))
        assertNotNull(repository.storedTest("custom_2"))
        assertEquals(false, repository.isArchived("custom_2"))
    }

    @Test
    fun `preview refuses seeded and unknown tests`() = runTest {
        repository.givenTests(test("test_pacer", TestSource.SEED))

        assertEquals(DeleteCustomTestPreview.NotAllowed, delete.preview("test_pacer"))
        assertEquals(DeleteCustomTestPreview.NotAllowed, delete.preview("nope"))
    }
}
