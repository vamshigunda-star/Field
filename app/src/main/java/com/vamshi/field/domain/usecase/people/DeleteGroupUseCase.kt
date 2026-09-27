package com.vamshi.field.domain.usecase.people

import com.vamshi.field.domain.model.people.Group
import com.vamshi.field.domain.repository.PeopleRepository
import javax.inject.Inject

class DeleteGroupUseCase @Inject constructor(
    private val repository: PeopleRepository
) {
    suspend operator fun invoke(group: Group): Result<Unit> {
        return runCatching {
            repository.deleteGroup(group)
        }
    }
}
