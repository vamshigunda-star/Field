package com.vamshi.field.domain.usecase.people

import com.vamshi.field.domain.model.people.BiologicalSex
import com.vamshi.field.domain.model.people.Individual
import com.vamshi.field.domain.repository.PeopleRepository
import java.util.UUID
import javax.inject.Inject

class RegisterAthleteUseCase @Inject constructor(
    private val repository: PeopleRepository
) {
    suspend operator fun invoke(
        firstName: String,
        lastName: String,
        dateOfBirth: Long,
        sex: BiologicalSex,
        email: String? = null,
        medicalAlert: String? = null
    ): Result<Individual> {
        if (firstName.isBlank()) return Result.failure(IllegalArgumentException("First name is required"))
        if (lastName.isBlank()) return Result.failure(IllegalArgumentException("Last name is required"))
        // Epoch millis are negative before 1970, so a `<= 0` check here used to turn away
        // most of the Older Adults bracket. A future date is the real invalid case: it gives a
        // negative age that matches no bracket, so the athlete could never be graded.
        if (dateOfBirth > System.currentTimeMillis()) {
            return Result.failure(IllegalArgumentException("Date of birth can't be in the future"))
        }

        val student = Individual(
            id = UUID.randomUUID().toString(),
            firstName = firstName.trim(),
            lastName = lastName.trim(),
            dateOfBirth = dateOfBirth,
            sex = sex,
            email = email?.trim(),
            medicalAlert = medicalAlert?.trim()
        )
        repository.insertIndividual(student)
        return Result.success(student)
    }
}
