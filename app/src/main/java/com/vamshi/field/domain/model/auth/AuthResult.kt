package com.vamshi.field.domain.model.auth

/**
 * Typed result wrapper for auth operations.
 *
 * Using a sealed class instead of Kotlin [Result] lets callers pattern-match
 * on specific [AuthError] variants without going through exception unwrapping.
 */
sealed class AuthResult {
    data class Success(val user: User) : AuthResult()
    data class Failure(val error: AuthError) : AuthResult()
}

/** Enumeration of all possible auth failure modes. */
enum class AuthError {
    /** The requested username is already registered on this device. */
    UsernameTaken,

    /** First or last name is blank or exceeds the max length. */
    InvalidName,

    /** Catch-all for unexpected data-layer failures. */
    Unknown
}
