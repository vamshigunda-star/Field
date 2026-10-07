# Spec: Passwordless onboarding — name + optional email only

## 1. Objective & scope

- **As a coach**, I want to set Field up with just my name (and optionally my email for Drive
  backup) so that I can never be locked out of my own athletes' data by a forgotten password.
- **In scope:**
  - Onboarding form drops the password field: **Coach name** (required) + **Email (optional)**.
  - Remove the only path that ever asks for the password again: the Dashboard **Sign out**
    button → **Unlock** screen. Both go.
  - App launch: if any coach account exists on the device, open it straight to Dashboard. This
    also rescues any device that is currently sitting on the Unlock screen (signed out).
  - Delete the now-dead password code across domain/data/ui and its tests.
- **Out of scope:**
  - **No schema change.** `users.passwordHash` / `passwordSalt` stay as vestigial `NOT NULL`
    BLOB columns, written as empty byte arrays (see §5, step 2 for why).
  - Backup payload shape (`BackupUser` keeps both keys; `BackupJsonContractTest` stays green).
  - Drive backup/restore behaviour — unchanged. Email stays exactly as it is now.
  - Lowering `minSdk` (raised to 26 for the PBKDF2 hasher in `0c7778e`; the hasher goes away
    here, but lowering minSdk is a separate decision).
  - An "edit coach name/email" screen in Settings.

## 2. Verified context

- Read:
  - `ui/auth/onboarding/OnboardingScreen.kt`, `…ViewModel.kt`, `…UiState.kt`, `…Action.kt` — form
    is Coach name + Password + Email (optional) + "Get Started" + "Restore existing data".
  - `domain/usecase/auth/OnboardingUseCase.kt` — validates name, then password, generates a
    username, calls `AuthRepository.signUp(firstName, lastName = "", username, password, email)`.
  - `domain/repository/AuthRepository.kt`, `data/repository/AuthRepositoryImpl.kt` — `signUp`
    hashes via `PasswordHasher`; `unlock(userId, password)` verifies; `signOut()` only nulls the
    session; `establishSessionAfterRestore()` sets the session to the most-recent account.
  - `ui/auth/AuthGateViewModel.kt` — session → Dashboard; no session + users → **Unlock**;
    no users → Onboarding. Injects `SessionManager` + `AuthRepository` directly.
  - `ui/navigation/NavGraph.kt:84-164` — routes for Unlock/Onboarding/RestoreBackup; Dashboard's
    `onNavigateToSignIn` navigates to `Screen.Unlock`.
  - `ui/dashboard/DashboardViewModel.kt:53,63,81-89` + `DashboardScreen.kt:70-78,380-384` —
    the Sign-out icon button, `SignOutUseCase`, `navigateToSignIn` flag.
  - `ui/auth/unlock/*` — password-only screen with an account switcher.
  - `data/local/entities/auth/UserEntity.kt` — `passwordHash: ByteArray`, `passwordSalt: ByteArray`, non-null.
  - `domain/model/backup/BackupPayload.kt:165-174` + `BackupRepositoryImpl.kt:102-111,221-231` —
    hash/salt Base64-encoded into `BackupUser`, decoded on restore.
  - **No athlete/group/event table is scoped by user id** (grep for `userId|coachId|ownerId`
    in `data/local/entities/` is empty). Accounts carry only a display name + email; every coach
    account on a device sees the same data. The password gated nothing but the device itself.
- Versions in play: none new. Room is `androidx.room3`, DB stays at **17**.
- **Drift noticed:**
  - `AuthGateViewModel` and `DashboardViewModel` inject repositories directly (and Dashboard
    injects `PeopleRepository`/`TestingRepository`). The gate is rewritten here and moves to a
    use case; Dashboard's other repository injections are left alone.
  - `AuthGateViewModel` KDoc still says "go to SignIn / SignUp" — routes are Unlock/Onboarding.
    Fixed by the rewrite.
  - `README.md` and `DEVELOPMENT_CONTEXT.md` mention password/Unlock (2 + 6 hits). Update in
    this change so the docs don't describe a screen that no longer exists.

## 3. Files

| Action | Path (under `app/src/main/java/com/vamshi/field/` unless noted) | Purpose |
|---|---|---|
| MODIFY | `ui/auth/onboarding/OnboardingScreen.kt` | Remove password field; email `ImeAction.Done` submits |
| MODIFY | `ui/auth/onboarding/OnboardingUiState.kt` | Drop `password`, `passwordVisible`, `passwordError` |
| MODIFY | `ui/auth/onboarding/OnboardingAction.kt` | Drop `PasswordChanged`, `TogglePasswordVisibility` |
| MODIFY | `ui/auth/onboarding/OnboardingViewModel.kt` | Drop `ValidatePasswordUseCase`; submit name + email only |
| MODIFY | `domain/usecase/auth/OnboardingUseCase.kt` | `invoke(coachName, email)` |
| MODIFY | `domain/repository/AuthRepository.kt` | `signUp` loses `password`; remove `signOut`, `unlock`, `listAccounts`; rename `establishSessionAfterRestore` → `establishPrimarySession` |
| MODIFY | `data/repository/AuthRepositoryImpl.kt` | Same; write empty hash/salt; drop `PasswordHasher` |
| MODIFY | `domain/model/auth/AuthResult.kt` | Remove `InvalidCredentials`, `WeakPassword` |
| MODIFY | `domain/usecase/auth/CompleteRestoreUseCase.kt` | Call renamed repository method |
| NEW | `domain/usecase/auth/ResumeSessionUseCase.kt` | Startup: keep or re-establish the coach session |
| MODIFY | `ui/auth/AuthGateViewModel.kt` | Two outcomes: `Authenticated` / `NoAccount`; inject `ResumeSessionUseCase` |
| MODIFY | `ui/navigation/NavGraph.kt` | Remove Unlock destination + Dashboard `onNavigateToSignIn` |
| MODIFY | `ui/navigation/Screen.kt` | Remove `Unlock`; fix `RestoreBackup` KDoc ("reachable from Onboarding") |
| MODIFY | `ui/dashboard/DashboardViewModel.kt` | Remove `OnSignOutClick`, `NavigationConsumed`, `navigateToSignIn`, `SignOutUseCase` |
| MODIFY | `ui/dashboard/DashboardScreen.kt` | Remove Sign-out icon button, `onNavigateToSignIn`, its `LaunchedEffect` |
| MODIFY | `di/AuthModule.kt` | Remove `providePasswordHasher` (companion object goes if empty) |
| MODIFY | `data/local/entities/auth/UserEntity.kt` | KDoc: hash/salt columns are vestigial, always empty |
| MODIFY | `domain/model/backup/BackupPayload.kt` | KDoc on `BackupUser.passwordHash/Salt`: vestigial, kept for format compatibility |
| DELETE | `ui/auth/unlock/` (`UnlockScreen.kt`, `UnlockViewModel.kt`, `UnlockUiState.kt`, `UnlockAction.kt`) | Screen no longer reachable |
| DELETE | `domain/usecase/auth/UnlockUseCase.kt`, `ListAccountsUseCase.kt`, `SignOutUseCase.kt`, `ValidatePasswordUseCase.kt` | Only callers were Unlock/Onboarding-password/Sign-out |
| DELETE | `data/auth/PasswordHasher.kt` | No callers |
| KEEP | `domain/usecase/auth/GetPrimaryAccountUseCase.kt` | Only if a caller remains after deletion — check with grep; delete if not |
| MODIFY | `app/src/test/.../domain/usecase/auth/FakeAuthRepository.kt` | Match new interface |
| MODIFY | `app/src/test/.../domain/usecase/auth/OnboardingUseCaseTest.kt` | Drop password cases |
| MODIFY | `app/src/test/.../domain/usecase/auth/CompleteRestoreUseCaseTest.kt`, `GenerateUsernameUseCaseTest.kt`, `GetPrimaryAccountUseCaseTest.kt` | `signUp` call sites lose the password arg |
| MODIFY | `app/src/test/.../ui/auth/AuthGateViewModelTest.kt` | New states + "accounts exist, no session → Authenticated" |
| NEW | `app/src/test/.../domain/usecase/auth/ResumeSessionUseCaseTest.kt` | Pins the lockout fix |
| DELETE | `app/src/test/.../domain/usecase/auth/UnlockUseCaseTest.kt`, `ListAccountsUseCaseTest.kt` | Subjects deleted |
| MODIFY | `README.md`, `DEVELOPMENT_CONTEXT.md` | Remove password/Unlock references |

## 4. Contracts

```kotlin
// domain/repository/AuthRepository.kt — resulting interface
interface AuthRepository {
    suspend fun signUp(
        firstName: String,
        lastName: String,
        username: String,
        email: String? = null
    ): AuthResult
    fun observeCurrentUser(): Flow<User?>
    suspend fun isUsernameTaken(username: String): Boolean
    suspend fun userCount(): Int
    suspend fun getPrimaryAccount(): User?
    /**
     * Sets the session to the most recently created account (same rule as
     * [getPrimaryAccount]). Used after a Drive restore and at app startup when an account
     * exists but no session does. Failure([AuthError.Unknown]) when there are no accounts.
     */
    suspend fun establishPrimarySession(): AuthResult
}

// domain/model/auth/AuthResult.kt
enum class AuthError { UsernameTaken, InvalidName, Unknown }

// domain/usecase/auth/OnboardingUseCase.kt
suspend operator fun invoke(coachName: String, email: String?): AuthResult

// domain/usecase/auth/ResumeSessionUseCase.kt  (NEW)
/**
 * Decides at app startup whether a coach is already set up on this device. Field has no
 * password and no sign-out, so "an account exists" means "open the app": an existing
 * session is kept, and a missing one (a device that was signed out under the old
 * password flow) is re-established on the primary account.
 *
 * @return true if a coach session is active after the call, false if the device has no account.
 */
class ResumeSessionUseCase @Inject constructor(
    private val sessionManager: SessionManager,
    private val repository: AuthRepository
) {
    suspend operator fun invoke(): Boolean
}

// ui/auth/AuthGateViewModel.kt
sealed interface AuthGateState {
    data object Loading : AuthGateState
    data object Authenticated : AuthGateState
    data object NoAccount : AuthGateState
}

@HiltViewModel
class AuthGateViewModel @Inject constructor(
    private val resumeSession: ResumeSessionUseCase
) : ViewModel()

// ui/auth/onboarding/OnboardingUiState.kt
data class OnboardingUiState(
    val coachName: String = "",
    val email: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val onboardingSuccess: Boolean = false,
    val coachNameError: String? = null
)

// ui/auth/onboarding/OnboardingAction.kt
sealed interface OnboardingAction {
    data class CoachNameChanged(val value: String) : OnboardingAction
    data class EmailChanged(val value: String) : OnboardingAction
    data object Submit : OnboardingAction
    data object DismissError : OnboardingAction
    data object NavigationConsumed : OnboardingAction
}
```

## 5. Implementation, layer by layer

1. **Domain contracts.** Apply the `AuthRepository` and `AuthError` changes above. Update the
   interface KDoc: drop the "Plain-text password; hashed inside the data layer" param doc and
   the `signOut`/`unlock`/`listAccounts` entries.

2. **Data — `AuthRepositoryImpl`.**
   - Remove the `hasher` constructor param and `import …PasswordHasher`.
   - `signUp`: build `UserEntity` with `passwordHash = ByteArray(0)`, `passwordSalt = ByteArray(0)`.
     Add a one-line comment pointing at `UserEntity`'s KDoc.
   - Delete `signOut`, `unlock`, `listAccounts`. Rename `establishSessionAfterRestore` →
     `establishPrimarySession` (body unchanged).
   - Update the class KDoc (no hashing, no `InvalidCredentials`).
   - **Why empty arrays rather than dropping the columns:** dropping them means DB v18, a
     table-rebuild migration, a `MigrationTest` case, a new `ROOM_IDENTITY_HASH` in
     `tools/build_prepackaged_db.py`, a regenerated asset, and a backup-format change — all to
     remove two unused columns. Empty arrays satisfy `NOT NULL`, Base64-encode to `""` and decode
     back to an empty array, so backup and restore are untouched. Fold the column drop into the
     next schema change that happens for its own reasons.
   - **Why not keep hashing a random secret:** it would keep `PasswordHasher` (and its API-26
     dependency) alive for no reader. Empty is honest about what the column holds.
   - Delete `data/auth/PasswordHasher.kt`; remove `providePasswordHasher` from `di/AuthModule.kt`.
   - Update `UserEntity` KDoc: the two columns are vestigial since this change, always empty for
     new accounts, may hold old PBKDF2 bytes on devices/backups created before it, and are never read.
   - Same note on `BackupUser.passwordHash`/`passwordSalt` in `BackupPayload.kt`. **Do not make
     them nullable or remove them** — `BackupJsonContractTest` pins the keys, and old backups in
     Drive carry real values that must still deserialize.

3. **Domain — use cases.**
   - `OnboardingUseCase`: drop `validatePassword` and the `password` param; call
     `repository.signUp(firstName = coachName.trim(), lastName = "", username, email = …)`.
     Rewrite the KDoc ("name + optional email; no password — Field has no lock screen; recovery
     is Drive restore"). Remove the stale reference to `[SignUpUseCase]` if that class no longer exists.
   - `CompleteRestoreUseCase`: call `establishPrimarySession()`.
   - NEW `ResumeSessionUseCase`:
     ```kotlin
     if (sessionManager.currentUserIdOnce() != null) return true
     if (repository.userCount() == 0) return false
     return repository.establishPrimarySession() is AuthResult.Success
     ```
   - Delete `UnlockUseCase`, `ListAccountsUseCase`, `SignOutUseCase`, `ValidatePasswordUseCase`.
     Then `grep -rn "GetPrimaryAccountUseCase" app/src/main` — its only known caller was
     `UnlockViewModel`; if nothing remains, delete it and `GetPrimaryAccountUseCaseTest`.
   - Fix `ValidationResult.kt` KDoc, which lists `ValidatePasswordUseCase` among its users.

4. **Presentation — auth gate.** Rewrite `AuthGateViewModel` around `ResumeSessionUseCase`,
   keeping the timeout + catch-all structure exactly (both fall back to `NoAccount`):
   `withTimeoutOrNull(RESOLUTION_TIMEOUT_MS) { if (resumeSession()) Authenticated else NoAccount }`.
   Update the KDoc decision list.

5. **Presentation — onboarding.**
   - `OnboardingViewModel`: remove `validatePassword` injection, the two password action
     branches, and the password check in `submit()`; call `onboardingUseCase(state.coachName, state.email)`.
     Drop `AuthError.WeakPassword` from `toUserMessage()` (it no longer exists).
   - `OnboardingScreen`: delete the Password `OutlinedTextField` block (lines 135-162) and the now-
     unused imports (`Icons`, `Visibility`, `VisibilityOff`, `Icon`, `IconButton`,
     `PasswordVisualTransformation`, `VisualTransformation`, `KeyboardType.Password` usage).
     Coach name keeps `ImeAction.Next` → focus moves to Email; Email keeps `ImeAction.Done` → submit.
     Keep the Email supporting text as-is.
   - `OnboardingUiState` KDoc: "Coach Name + optional Email".

6. **Presentation — remove sign-out and Unlock.**
   - `DashboardViewModel`: remove `SignOutUseCase` injection, `OnSignOutClick`,
     `NavigationConsumed`, `navigateToSignIn`.
   - `DashboardScreen`: remove the `onNavigateToSignIn` param, its `LaunchedEffect`, the
     `onSignOutClick` param of `DashboardHeader`, and the Logout `DashboardHeaderIconButton`
     (plus `Icons.AutoMirrored.Filled.Logout` import). Settings stays as the header's only action.
   - `NavGraph`: remove the `composable(Screen.Unlock.route)` block, the `onNavigateToSignIn`
     argument, and change the start-destination `when` to
     `Authenticated -> Dashboard`, `NoAccount -> Onboarding`, `Loading -> Onboarding // unreachable`.
   - `Screen.kt`: remove `Unlock`.
   - Delete `ui/auth/unlock/`.

7. **Tests** — see §7.

8. **Docs.** Remove password/Unlock references from `README.md` and `DEVELOPMENT_CONTEXT.md`;
   one sentence in `DEVELOPMENT_CONTEXT.md`'s auth section: "Field has no password or lock
   screen by design — a forgotten password must never lock a coach out of their athletes' data.
   The device's own screen lock is the security boundary."

## 6. Constraints for this change

- **Restore must keep working for backups that contain real hashes.** `restoreEntities` decodes
  whatever Base64 is there into the vestigial columns and nothing reads them. Do not add
  validation that rejects non-empty or empty values.
- **The gate must still never strand the app on `Loading`.** Keep both exits (timeout, catch)
  and the three tests that pin them.
- **Multiple accounts on one device** (possible from an old backup or a pre-onboarding-redesign
  install) resolve silently to the most recent one. Because no data is scoped per account, the
  only visible effect is the Dashboard greeting name. That's acceptable; don't build a picker.
- **`ui/` imports nothing from `data/`.** `ResumeSessionUseCase` uses the domain `SessionManager`
  interface, not `SessionManagerImpl`.

## 7. Verification

- **Unit (new, the lockout regression):** `ResumeSessionUseCaseTest`
  - `whenSessionExists_returnsTrue_withoutChangingIt`
  - `whenAccountExistsButNoSession_establishesPrimarySession` — this is the case that today
    lands on the Unlock screen; assert the fake's session is the most-recent account's id.
  - `whenNoAccounts_returnsFalse`
- **Unit (updated):** `AuthGateViewModelTest` — rename/flip
  `authGate_whenNoSessionButAccountsExist_resolvesToUnlock` → `…_resolvesToAuthenticated`;
  hang/throw/no-account tests now expect `NoAccount`. `HangingAuthRepository` /
  `ThrowingAuthRepository` keep overriding `userCount()`. Construct the VM with
  `AuthGateViewModel(ResumeSessionUseCase(FakeSessionManager(…), repo))`.
- **Unit (updated):** `OnboardingUseCaseTest` — remove weak-password cases; keep blank-name →
  `InvalidName`; add "name only, no email → Success, email null".
- **Unit (unchanged, must stay green):** `BackupJsonContractTest`, backup fixture round-trip
  tests (`backup_v1_fixture.json` carries real hash/salt values — proves old backups still restore).
- **Build:** `gradlew assembleDebug`, then `gradlew test`. Run them one at a time.
  Then `gradlew assembleRelease` — R8 must not choke on the removed classes (no keep rules
  reference them; confirm with a grep of `app/proguard-rules.pro` for `PasswordHasher`/`Unlock`).
- **Manual:**
  1. Fresh install → Onboarding shows Coach name + Email (optional) only. Submit with a blank
     name → inline error. Enter a name, leave email blank → Get Started → Dashboard greets by name.
  2. Dashboard header shows Settings only — no sign-out icon.
  3. Kill and relaunch → straight to Dashboard.
  4. **Upgrade path:** on a device/emulator with the *current* build, onboard with a password,
     tap Sign out (lands on Unlock), then install this build over it → launches straight to
     Dashboard with the same coach name and all athletes intact.
  5. Settings → Back up to Drive → clear app data → Onboarding → "Restore existing data" →
     restore → Dashboard. Repeat on a release APK (debug can't see release backups).
