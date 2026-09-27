package com.vamshi.field.domain.usecase.people

import com.vamshi.field.domain.model.people.Group
import com.vamshi.field.domain.model.people.Individual
import com.vamshi.field.domain.repository.PeopleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DeleteGroupUseCaseTest {

    private lateinit var fakeRepository: TestFakePeopleRepository
    private lateinit var deleteGroupUseCase: DeleteGroupUseCase

    @Before
    fun setUp() {
        fakeRepository = TestFakePeopleRepository()
        deleteGroupUseCase = DeleteGroupUseCase(fakeRepository)
    }

    @Test
    fun `invoke deletes group and returns success`() = runTest {
        val group = Group(
            id = "group-1",
            name = "Sprint Squad",
            location = "Track",
            cycle = "Spring 2026",
            category = null
        )
        fakeRepository.groups.add(group)

        val result = deleteGroupUseCase(group)

        assertTrue(result.isSuccess)
        assertTrue(fakeRepository.deletedGroups.contains(group))
        assertEquals(0, fakeRepository.groups.size)
    }

    @Test
    fun `invoke returns failure when repository throws exception`() = runTest {
        val group = Group(
            id = "group-2",
            name = "Powerlifting",
            location = "Gym",
            cycle = "Winter 2026",
            category = null
        )
        fakeRepository.shouldThrow = true

        val result = deleteGroupUseCase(group)

        assertTrue(result.isFailure)
        assertEquals("Database delete failed", result.exceptionOrNull()?.message)
    }

    private class TestFakePeopleRepository : PeopleRepository {
        val groups = mutableListOf<Group>()
        val deletedGroups = mutableListOf<Group>()
        var shouldThrow = false

        override suspend fun deleteGroup(group: Group) {
            if (shouldThrow) throw IllegalStateException("Database delete failed")
            groups.remove(group)
            deletedGroups.add(group)
        }

        override fun getAllGroups(): Flow<List<Group>> = emptyFlow()
        override fun getGroupFlow(id: String): Flow<Group?> = emptyFlow()
        override suspend fun getGroupById(id: String): Group? = groups.find { it.id == id }
        override suspend fun insertGroup(group: Group) { groups.add(group) }
        override fun getAllIndividuals(): Flow<List<Individual>> = emptyFlow()
        override fun getIndividualFlow(id: String): Flow<Individual?> = emptyFlow()
        override fun searchIndividuals(query: String): Flow<List<Individual>> = emptyFlow()
        override suspend fun getIndividualById(id: String): Individual? = null
        override suspend fun insertIndividual(individual: Individual) {}
        override suspend fun deleteIndividual(individual: Individual) {}
        override fun getIndividualsInGroup(groupId: String): Flow<List<Individual>> = emptyFlow()
        override fun getGroupsForIndividual(individualId: String): Flow<List<Group>> = emptyFlow()
        override suspend fun addMemberToGroup(groupId: String, individualId: String) {}
        override suspend fun removeMemberFromGroup(groupId: String, individualId: String) {}
        override fun getIndividualsByIds(ids: List<String>): Flow<List<Individual>> = emptyFlow()
        override fun getGroupAthleteCounts(): Flow<Map<String, Int>> = emptyFlow()
    }
}
