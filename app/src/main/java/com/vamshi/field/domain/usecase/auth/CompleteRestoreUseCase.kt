package com.vamshi.field.domain.usecase.auth

import com.vamshi.field.domain.model.auth.AuthResult
import com.vamshi.field.domain.repository.AuthRepository
import javax.inject.Inject

/**
 * Establishes a session immediately after a successful Google Drive restore.
 *
 * Call this only after [com.vamshi.field.domain.usecase.backup.RestoreDataUseCase]
 * (the pre-existing wrapper around [com.vamshi.field.domain.repository.BackupRepository.restoreFromDrive])
 * has completed — the restored payload already contains the coach's account. Thin wrapper
 * around [AuthRepository.establishPrimarySession].
 */
class CompleteRestoreUseCase @Inject constructor(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(): AuthResult = repository.establishPrimarySession()
}
