package com.vamshi.field.data.local.daos.people

import androidx.room3.*
import com.vamshi.field.data.local.entities.people.GroupEntity
import com.vamshi.field.data.local.entities.people.GroupMemberCrossRef
import com.vamshi.field.data.local.entities.people.IndividualEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PeopleDao {

    // --- INDIVIDUALS ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIndividual(individual: IndividualEntity)

    @Delete
    suspend fun deleteIndividual(individual: IndividualEntity)

    @Query("SELECT * FROM individuals WHERE id = :id LIMIT 1")
    suspend fun getIndividualById(id: String): IndividualEntity?

    @Query("SELECT * FROM individuals WHERE id = :id LIMIT 1")
    fun getIndividualByIdFlow(id: String): Flow<IndividualEntity?>

    @Query("SELECT * FROM individuals WHERE isDeleted = 0 ORDER BY firstName ASC")
    fun getAllIndividuals(): Flow<List<IndividualEntity>>

    @Query("SELECT * FROM individuals WHERE id IN (:ids) AND isDeleted = 0")
    fun getIndividualsByIds(ids: List<String>): Flow<List<IndividualEntity>>

    // Feature: Search Bar (Finds students by name)
    @Query("""
        SELECT * FROM individuals 
        WHERE (firstName LIKE '%' || :query || '%' OR lastName LIKE '%' || :query || '%') 
        AND isDeleted = 0 
        ORDER BY firstName ASC
    """)
    fun searchIndividuals(query: String): Flow<List<IndividualEntity>>

    // --- GROUPS ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: GroupEntity)

    @Delete
    suspend fun deleteGroup(group: GroupEntity)

    @Query("SELECT * FROM `groups` WHERE isDeleted = 0 ORDER BY name ASC")
    fun getAllGroups(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM `groups` WHERE id = :id LIMIT 1")
    suspend fun getGroupById(id: String): GroupEntity?

    @Query("SELECT * FROM `groups` WHERE id = :id LIMIT 1")
    fun getGroupByIdFlow(id: String): Flow<GroupEntity?>

    // --- ROSTERING (Many-to-Many Relationships) ---

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addMemberToGroup(crossRef: GroupMemberCrossRef)

    @Delete
    suspend fun removeMemberFromGroup(crossRef: GroupMemberCrossRef)

    // Feature: Get all students in a specific group (e.g., "Class 9A Roster")
    @Query("""
        SELECT i.* FROM individuals AS i
        INNER JOIN group_members AS gm ON i.id = gm.individualId
        WHERE gm.groupId = :groupId AND i.isDeleted = 0
        ORDER BY i.lastName ASC
    """)
    fun getIndividualsInGroup(groupId: String): Flow<List<IndividualEntity>>

    // Feature: Get all groups a student belongs to (e.g., "John's Teams")
    @Query("""
        SELECT g.* FROM `groups` AS g
        INNER JOIN group_members AS gm ON g.id = gm.groupId
        WHERE gm.individualId = :studentId AND g.isDeleted = 0
    """)
    fun getGroupsForIndividual(studentId: String): Flow<List<GroupEntity>>

    @Query("""
        SELECT gm.groupId, COUNT(gm.individualId) as count 
        FROM group_members gm 
        INNER JOIN individuals i ON gm.individualId = i.id 
        WHERE i.isDeleted = 0 
        GROUP BY gm.groupId
    """)
    fun getGroupAthleteCounts(): Flow<Map<@MapColumn(columnName = "groupId") String, @MapColumn(columnName = "count") Int>>

    @Query("SELECT COUNT(*) FROM individuals")
    suspend fun getIndividualCount(): Int
}
