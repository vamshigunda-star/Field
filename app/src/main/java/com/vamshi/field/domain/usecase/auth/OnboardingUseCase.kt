package com.vamshi.field.domain.usecase.auth

import com.vamshi.field.domain.model.auth.AuthError
import com.vamshi.field.domain.model.auth.AuthResult
import com.vamshi.field.domain.repository.AuthRepository
import javax.inject.Inject

/**
 * Creates a coach account from the redesigned two-field onboarding form
 * (Coach Name + Password, with an optional Email for Drive backup).
 *
 * This is the entry point the Onboarding screen calls — it replaces the old
 * 6-field [SignUpUseCase] as the user-facing flow, but still delegates the
 * actual account creation to [AuthRepository.signUp] under the hood.
 *
 * Validation order:
 *  1. Coach name (non-blank, ≤50 chars — same rule as any name field).
 *  2. Password strength.
 *  3. Username generation never fails validation — [GenerateUsernameUseCase]
 *     always produces a syntactically valid, unique candidate.
 *
 * The full "Coach Name" string is stored as [com.vamshi.field.domain.model.auth.User.firstName]
 * with [com.vamshi.field.domain.model.auth.User.lastName] left blank — Dashboard's existing
 * greeting logic already renders `listOf(firstName, lastName).filter{it.isNotBlank()}.joinToString(" ")`,
 * so a blank last name degrades correctly without any Dashboard changes.
 *
 * No security question/answer is collected — this account has no legacy password-reset
 * path; recovery is via Google Drive restore only ([RestoreDataUseCase] + [CompleteRestoreUseCase]).
 */
class OnboardingUseCase @Inject constructor(
    private val repository: AuthRepository,
    private val validateName: ValidateNameUseCase,
    private val validatePassword: ValidatePasswordUseCase,
    private val generateUsername: GenerateUsernameUseCase
) {
    suspend operator fun invoke(
        coachName: String,
        password: String,
        email: String?
    ): AuthResult {
        val nameResult = validateName(coachName)
        if (nameResult is ValidationResult.Invalid) {
            return AuthResult.Failure(AuthError.InvalidName)
        }
        val passwordResult = validatePassword(password)
        if (passwordResult is ValidationResult.Invalid) {
            return AuthResult.Failure(AuthError.WeakPassword)
        }

        val username = generateUsername(coachName)

        return repository.signUp(
            firstName = coachName.trim(),
            lastName = "",
            username = username,
            password = password,
            email = email?.trim()?.ifBlank { null }
        )
    }
}
