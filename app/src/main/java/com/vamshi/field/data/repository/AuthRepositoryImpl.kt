package com.vamshi.field.data.repository

import android.database.sqlite.SQLiteConstraintException
import com.vamshi.field.data.auth.PasswordHasher
import com.vamshi.field.data.local.daos.auth.UserDao
import com.vamshi.field.data.local.entities.auth.UserEntity
import com.vamshi.field.data.mapper.auth.toDomain
import com.vamshi.field.domain.model.auth.AuthError
import com.vamshi.field.domain.model.auth.AuthResult
import com.vamshi.field.domain.model.auth.User
import com.vamshi.field.domain.repository.AuthRepository
import com.vamshi.field.domain.repository.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Concrete implementation of [AuthRepository].
 *
 * Responsibilities:
 *  - Normalize usernames (trim + lowercase) before every DB operation.
 *  - Hash passwords and security answers via [PasswordHasher] (PBKDF2-SHA256).
 *  - Manage session via [SessionManager] (SharedPreferences).
 *  - Map [SQLiteConstraintException] to [AuthError.UsernameTaken].
 *  - Never expose raw entities — always map through [toDomain] before returning.
 */
@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val userDao: UserDao,
    private val sessionManager: SessionManager,
    private val hasher: PasswordHasher
) : AuthRepository {

    override suspend fun signUp(
        firstName: String,
        lastName: String,
        username: String,
        password: String,
        email: String?
    ): AuthResult = withContext(Dispatchers.IO) {
        val normalizedUsername = username.trim().lowercase()

        val (pwdSalt, pwdHash) = hasher.hash(password)

        val entity = UserEntity(
            id = UUID.randomUUID().toString(),
            firstName = firstName.trim(),
            lastName = lastName.trim(),
            username = normalizedUsername,
            email = email?.trim()?.ifBlank { null },
            passwordHash = pwdHash,
            passwordSalt = pwdSalt,
            createdAt = System.currentTimeMillis()
        )

        return@withContext try {
            userDao.insert(entity)
            // Auto sign-in: set the session immediately after account creation.
            sessionManager.setCurrentUserId(entity.id)
            AuthResult.Success(entity.toDomain())
        } catch (e: SQLiteConstraintException) {
            AuthResult.Failure(AuthError.UsernameTaken)
        } catch (e: Exception) {
            AuthResult.Failure(AuthError.Unknown)
        }
    }

    override suspend fun signOut() {
        sessionManager.setCurrentUserId(null)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeCurrentUser(): Flow<User?> =
        sessionManager.observeCurrentUserId().flatMapLatest { id ->
            if (id == null) {
                flowOf(null)
            } else {
                userDao.observeById(id).map { it?.toDomain() }
            }
        }

    override suspend fun isUsernameTaken(username: String): Boolean =
        withContext(Dispatchers.IO) {
            userDao.getByUsername(username.trim().lowercase()) != null
        }

    override suspend fun userCount(): Int = withContext(Dispatchers.IO) {
        userDao.count()
    }

    override suspend fun getPrimaryAccount(): User? = withContext(Dispatchers.IO) {
        // userDao.getAll() is already ordered by createdAt DESC, so the head of the
        // list satisfies both the "single account" and "most recent" cases.
        userDao.getAll().firstOrNull()?.toDomain()
    }

    override suspend fun listAccounts(): List<User> = withContext(Dispatchers.IO) {
        userDao.getAll().map { it.toDomain() }
    }

    override suspend fun unlock(userId: String, password: String): AuthResult =
        withContext(Dispatchers.IO) {
            val entity = userDao.getById(userId)
                ?: return@withContext AuthResult.Failure(AuthError.InvalidCredentials)

            val valid = hasher.verify(password, entity.passwordSalt, entity.passwordHash)
            if (!valid) {
                return@withContext AuthResult.Failure(AuthError.InvalidCredentials)
            }

            sessionManager.setCurrentUserId(entity.id)
            AuthResult.Success(entity.toDomain())
        }

    override suspend fun establishSessionAfterRestore(): AuthResult =
        withContext(Dispatchers.IO) {
            val user = userDao.getAll().firstOrNull()
                ?: return@withContext AuthResult.Failure(AuthError.Unknown)
            sessionManager.setCurrentUserId(user.id)
            AuthResult.Success(user.toDomain())
        }
}
