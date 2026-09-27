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

/**
 * Enumeration of all possible auth failure modes.
 *
 * Security note: [InvalidCredentials] is intentionally generic — it covers both
 * "no such account" and "wrong password" so the presentation layer cannot be used
 * to enumerate usernames.
 */
enum class AuthError {
    /** The requested username is already registered on this device. */
    UsernameTaken,

    /** Username/password combination does not match any stored account. */
    InvalidCredentials,

    /** Password does not satisfy the complexity rules. */
    WeakPassword,

    /** First or last name is blank or exceeds the max length. */
    InvalidName,

    /** Catch-all for unexpected data-layer failures. */
    Unknown
}
