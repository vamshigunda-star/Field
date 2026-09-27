package com.vamshi.field.data.local.entities.auth

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * Room entity for coach accounts.
 *
 * Password storage format:
 *  - [passwordSalt]: 16 random bytes from [java.security.SecureRandom].
 *  - [passwordHash]: 32-byte PBKDF2WithHmacSHA256 derived key (120,000 iterations).
 *  Salt and hash are stored separately (both as BLOB) so the verifier can
 *  reconstruct the same KDF invocation without encoding overhead.
 *
 * The [username] column has a unique index; the DB layer enforces uniqueness
 * at insert time (SQLiteConstraintException), and the repository catches it
 * and maps it to [com.vamshi.field.domain.model.auth.AuthError.UsernameTaken].
 */
@Entity(
    tableName = "users",
    indices = [Index(value = ["username"], unique = true)]
)
data class UserEntity(
    @PrimaryKey
    val id: String,

    val firstName: String,
    val lastName: String,

    /** Stored as lowercase+trimmed; never contains mixed-case. */
    val username: String,

    /**
     * Optional email — added in schema v11 ([com.vamshi.field.data.AppDatabase.MIGRATION_10_11]).
     * Purely for enabling Google Drive backup/restore later; never used as a login field
     * and never required at account-creation time.
     */
    val email: String? = null,

    /** PBKDF2 derived key bytes (32 bytes / 256 bits). */
    val passwordHash: ByteArray,

    /** 16-byte random salt used when deriving [passwordHash]. */
    val passwordSalt: ByteArray,

    val createdAt: Long
) {
    // ByteArray-aware equals/hashCode so data class comparisons work correctly.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as UserEntity
        return id == other.id &&
            firstName == other.firstName &&
            lastName == other.lastName &&
            username == other.username &&
            email == other.email &&
            passwordHash.contentEquals(other.passwordHash) &&
            passwordSalt.contentEquals(other.passwordSalt) &&
            createdAt == other.createdAt
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + firstName.hashCode()
        result = 31 * result + lastName.hashCode()
        result = 31 * result + username.hashCode()
        result = 31 * result + (email?.hashCode() ?: 0)
        result = 31 * result + passwordHash.contentHashCode()
        result = 31 * result + passwordSalt.contentHashCode()
        result = 31 * result + createdAt.hashCode()
        return result
    }
}
