package com.vamshi.field.domain.usecase.testing

import com.vamshi.field.domain.model.people.Group
import com.vamshi.field.domain.model.people.Individual
import com.vamshi.field.domain.repository.PeopleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory [PeopleRepository] test double, in the same hand-written style as
 * [FakeTestingRepository].
 *
 * Membership is a real join rather than a shortcut — athletes are stored once and groups hold
 * ids — so a test that drops an athlete from a group sees the roster change everywhere the app
 * would.
 */
class FakePeopleRepository : PeopleRepository {

    private val individuals = MutableStateFlow<List<Individual>>(emptyList())
    private val groups = MutableStateFlow<List<Group>>(emptyList())

    /** groupId -> member ids, insertion-ordered. */
    private val membership = MutableStateFlow<Map<String, List<String>>>(emptyMap())

    fun givenGroupWithAthletes(group: Group, athletes: List<Individual>) {
        groups.value = groups.value.filterNot { it.id == group.id } + group
        individuals.value = individuals.value.filterNot { existing ->
            athletes.any { it.id == existing.id }
        } + athletes
        membership.value = membership.value + (group.id to athletes.map { it.id })
    }

    // --- Individuals ---

    override fun getAllIndividuals(): Flow<List<Individual>> = individuals

    override fun getIndividualFlow(id: String): Flow<Individual?> =
        individuals.map { list -> list.find { it.id == id } }

    override fun searchIndividuals(query: String): Flow<List<Individual>> =
        individuals.map { list -> list.filter { it.fullName.contains(query, ignoreCase = true) } }

    override suspend fun getIndividualById(id: String): Individual? =
        individuals.value.find { it.id == id }

    override suspend fun insertIndividual(individual: Individual) {
        individuals.value = individuals.value.filterNot { it.id == individual.id } + individual
    }

    override suspend fun deleteIndividual(individual: Individual) {
        individuals.value = individuals.value.filterNot { it.id == individual.id }
        membership.value = membership.value.mapValues { (_, ids) -> ids - individual.id }
    }

    // --- Groups ---

    override fun getAllGroups(): Flow<List<Group>> = groups

    override fun getGroupFlow(id: String): Flow<Group?> =
        groups.map { list -> list.find { it.id == id } }

    override suspend fun getGroupById(id: String): Group? = groups.value.find { it.id == id }

    override suspend fun insertGroup(group: Group) {
        groups.value = groups.value.filterNot { it.id == group.id } + group
    }

    override suspend fun deleteGroup(group: Group) {
        groups.value = groups.value.filterNot { it.id == group.id }
        membership.value = membership.value - group.id
    }

    // --- Rostering ---

    override fun getIndividualsInGroup(groupId: String): Flow<List<Individual>> =
        membership.map { map ->
            map[groupId].orEmpty().mapNotNull { id -> individuals.value.find { it.id == id } }
        }

    override fun getGroupsForIndividual(individualId: String): Flow<List<Group>> =
        membership.map { map ->
            map.filterValues { individualId in it }.keys
                .mapNotNull { id -> groups.value.find { it.id == id } }
        }

    override suspend fun addMemberToGroup(groupId: String, individualId: String) {
        val current = membership.value[groupId].orEmpty()
        if (individualId !in current) {
            membership.value = membership.value + (groupId to current + individualId)
        }
    }

    override suspend fun removeMemberFromGroup(groupId: String, individualId: String) {
        membership.value = membership.value +
            (groupId to membership.value[groupId].orEmpty().filterNot { it == individualId })
    }

    override fun getIndividualsByIds(ids: List<String>): Flow<List<Individual>> =
        individuals.map { list -> list.filter { it.id in ids } }

    override fun getGroupAthleteCounts(): Flow<Map<String, Int>> =
        membership.map { map -> map.mapValues { (_, ids) -> ids.size } }
}
