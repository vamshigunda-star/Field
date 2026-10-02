package com.vamshi.field.domain.usecase.auth

import com.vamshi.field.domain.model.auth.AuthError
import com.vamshi.field.domain.model.auth.AuthResult
import com.vamshi.field.domain.repository.AuthRepository
import javax.inject.Inject

/**
 * Creates a coach account from the onboarding form: Coach Name, plus an optional Email
 * for Drive backup.
 *
 * There is deliberately no password. Field has no lock screen, so there is nothing a coach
 * could forget that would lock them out of their athletes' data; recovery after a lost or
 * replaced device is via Google Drive restore ([RestoreDataUseCase] + [CompleteRestoreUseCase]).
 *
 * Validation: the coach name must be non-blank and ≤50 chars (same rule as any name field).
 * Username generation never fails — [GenerateUsernameUseCase] always produces a syntactically
 * valid, unique candidate.
 *
 * The full "Coach Name" string is stored as [com.vamshi.field.domain.model.auth.User.firstName]
 * with [com.vamshi.field.domain.model.auth.User.lastName] left blank — Dashboard's existing
 * greeting logic already renders `listOf(firstName, lastName).filter{it.isNotBlank()}.joinToString(" ")`,
 * so a blank last name degrades correctly without any Dashboard changes.
 */
class OnboardingUseCase @Inject constructor(
    private val repository: AuthRepository,
    private val validateName: ValidateNameUseCase,
    private val generateUsername: GenerateUsernameUseCase
) {
    suspend operator fun invoke(
        coachName: String,
        email: String?
    ): AuthResult {
        val nameResult = validateName(coachName)
        if (nameResult is ValidationResult.Invalid) {
            return AuthResult.Failure(AuthError.InvalidName)
        }

        val username = generateUsername(coachName)

        return repository.signUp(
            firstName = coachName.trim(),
            lastName = "",
            username = username,
            email = email?.trim()?.ifBlank { null }
        )
    }
}
