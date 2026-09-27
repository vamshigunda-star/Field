# Spec: Athlete cards on the Roster screen read maroon-red in dark mode instead of matching the Groups tab

## 1. Objective & scope

- **Symptom (coach's words):** "The roster screen in dark mode has red coloured cards where the athlete info is. That needs to be a uniform colour like the Groups tab."
- **In scope:** the resting-state background of every athlete card in the Athletes tab of the Roster screen, dark theme only. After the fix, an athlete card at rest renders the same slate as a group card; the red swipe-to-delete affordance still appears while the coach swipes.
- **Out of scope:**
  - The `errorContainer` avatar tile and `error`-tinted warning icon shown for a restricted athlete (`isRestricted || medicalAlert != null`). Those are deliberate safety signalling and must stay red — see Constraints.
  - Any change to light theme, which is unaffected.
  - Any change to the swipe-to-delete interaction, its threshold, or the confirmation dialog.

## 2. Verified context

Files read, with what each confirmed:

| File | Confirmed |
|---|---|
| `app/src/main/java/com/vamshi/field/ui/roster/RosterComponents.kt` | `ModernAthleteCard` (:201) and `ModernGroupCard` (:358) already declare the **identical** surface colour. The athlete card is the only one wrapped in a `SwipeToDismissBox` (:173). |
| `app/src/main/java/com/vamshi/field/ui/theme/Theme.kt` | Dark scheme: `background = #111827`, `surfaceVariant = #374151`, `errorContainer = #4C0519`. Light scheme: `surface = SurfaceWhite` (opaque). |
| `gradle/libs.versions.toml` | `composeBom = "2024.11.00"` — Material3 1.3.x, where `SwipeToDismissBoxState.targetValue` and `SwipeToDismissBoxValue.Settled` are stable public API. |

Grep confirmed `SwipeToDismissBox` appears exactly once in `ui/`, and `ModernAthleteCard` has exactly one call site. There is no second (tablet/adaptive) rendering of the athlete list to keep in sync.

**No schema change is implied. The database stays at 15; no migration, no `MigrationTest` addition, no `tools/build_prepackaged_db.py` rerun.**

**Drift noticed:**
- None between docs and code for this change. The colour tokens are already centralised and the two cards already agree — this defect is a compositing accident, not a divergent hard-coded colour.
- Adjacent, not in scope: `RosterComponents.kt` calls `isSystemInDarkTheme()` directly in six places rather than reading a theme token, so a future in-app dark-mode toggle would not reach these cards. Worth scheduling separately.

## 3. Root cause

The athlete card's background is **translucent**, and Material3 draws the swipe-to-dismiss background *underneath it at all times* — not only during a swipe.

`SwipeableAthleteCard` (`RosterComponents.kt:173`) paints its `backgroundContent` slot with the full-bleed error colour:

```kotlin
backgroundContent = {
    val color = MaterialTheme.colorScheme.errorContainer   // :177
    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(14.dp))
            .background(color)
            …
```

`SwipeToDismissBox` composes and draws `backgroundContent` behind the content slot unconditionally; there is no built-in gating on swipe state. The card that sits on top of it is `ModernAthleteCard`, whose surface in dark mode is only 35% opaque (`RosterComponents.kt:213`):

```kotlin
color = if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        else MaterialTheme.colorScheme.surface
```

So in dark mode the card is a 35% slate wash over a solid `errorContainer` (`#4C0519`), and the composite the coach actually sees is roughly **`#451A2D`** — a muted maroon, on every card, permanently.

`ModernGroupCard` (`RosterComponents.kt:373`) uses the *same* `surfaceVariant.copy(alpha = 0.35f)` expression, but has no `SwipeToDismissBox`, so it composites over `background` (`#111827`) and lands at roughly **`#1E2636`** — the slate the coach is pointing at as correct. The two cards were written to match; only the layer behind them differs.

This is dark-mode-only because the light scheme's card colour is opaque `surface`, which hides the layer beneath entirely. Verified by reading the two colour schemes, not inferred.

One consequence worth naming: because the red is always present, the swipe-to-delete gesture currently has **no visual reveal** — there is nothing to uncover, since the red is already showing. Fixing the tint also restores the affordance.

## 4. Files

| Action | Path | Purpose |
|---|---|---|
| MODIFY | `app/src/main/java/com/vamshi/field/ui/roster/RosterComponents.kt` | Gate the swipe background so it is transparent at rest and fades in during the swipe. |

## 5. Fix

Single change, inside `SwipeableAthleteCard`'s `backgroundContent` lambda (`RosterComponents.kt:176-186`).

**Step 1 — derive whether a swipe is in progress and drive the background colour from it.**

```kotlin
backgroundContent = {
    val isSwiping = dismissState.targetValue != SwipeToDismissBoxValue.Settled
    val color by animateColorAsState(
        targetValue = if (isSwiping) MaterialTheme.colorScheme.errorContainer else Color.Transparent,
        label = "swipeBackground"
    )
    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(14.dp))
            .background(color)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        if (isSwiping) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}
```

**Step 2 — add the one missing import.**

```kotlin
import androidx.compose.animation.core.animateColorAsState
```

`androidx.compose.animation.*` is already imported at `RosterComponents.kt:3`, which covers `animateColorAsState` in most Compose versions; add the explicit import anyway so resolution does not depend on a wildcard. `SwipeToDismissBoxValue`, `Color`, and `Icon` are already in scope via the existing `androidx.compose.material3.*` and `androidx.compose.ui.graphics.Color` imports.

**Why this and not the obvious alternatives:**

- *Why not make the athlete card opaque in dark mode?* It would fix the colour, but it also silently makes the athlete card's colour expression diverge from `ModernGroupCard`'s — the next person to change one would not know to change the other. Worse, the red would still be sitting behind a now-opaque card, so the swipe reveal would remain broken in a way nobody could see.
- *Why not delete `backgroundContent` entirely?* That removes the delete affordance the "Swipe left to delete" hint at `RosterComponents.kt:141` promises.
- *Why `targetValue` and not `requireOffset()`?* `requireOffset()` throws before the first layout pass, which is exactly when a `LazyColumn` composes new rows during a fling. `targetValue` is a plain snapshot-backed state read and is safe in composition. The trade-off is that the red reveals once the drag crosses the positional threshold rather than on the first pixel; `animateColorAsState` smooths that into a fade so it reads as intentional.

## 6. Constraints for this change

- **`confirmValueChange` must keep returning `false` for `EndToStart`** (`RosterComponents.kt:166-170`). The card is deliberately not dismissed on swipe — deletion is confirmed in a dialog, and the row must snap back. Do not "fix" the state machine while in here.
- **Medical-alert signalling stays red.** `isRestricted` drives the `errorContainer` avatar tile (`:252-258`) and the `error` warning icon (`:290`). Those are the safety-critical surfaces from `Individual.medicalAlert` / `isRestricted` and must remain visually distinct on a card for an athlete who is about to be tested. Only the *card-wide* red goes away.
- **Do not change `ModernAthleteCard`'s `color` expression.** It is already correct and already matches `ModernGroupCard`; the uniformity the coach asked for is achieved by removing what is behind it.
- Both cards use `key = { it.id }` on their `LazyColumn` items (`:127`, `:338`). The new `animateColorAsState` is per-row state — keep the keys so a fling does not carry one row's animation into another's slot.

## 7. Verification

- **Manual (walks the original symptom) — this is the primary check, since the defect is purely visual:**
  1. Put the device in dark mode, launch Field, open **Roster → Athletes**.
  2. Every athlete card sits on the same slate as a group card. Switch to the **Groups** tab and back; the two card backgrounds are indistinguishable.
  3. Swipe an athlete card left. The red delete background fades in behind it with the trash icon, and the delete-confirmation dialog appears.
  4. Dismiss the dialog. The card snaps back and the red fades out completely — no residual tint.
  5. Confirm an athlete **with a medical alert** still shows the red avatar tile and the warning icon next to their name.
  6. Switch to light mode and confirm the Athletes tab is unchanged.
- **Build:** `gradlew assembleDebug`, then `gradlew test`.
- **No unit or instrumented test is proposed.** A Compose UI test can assert that a composable is present, but not the colour a translucent surface composites to against the layer beneath it — that is precisely what this defect consisted of, and a test asserting `ModernAthleteCard`'s declared colour would have passed happily throughout. Adding one would give false confidence.

**Why this wasn't caught:** the bug lives in the *interaction between* two composables that are each individually correct — a translucent card and an always-drawn swipe background. Nothing in either declaration looks wrong in review, and it is invisible in light mode, so a light-mode Compose preview or screenshot test shows nothing. The practical guard is a dark-mode `@Preview` on `SwipeableAthleteCard` (not `ModernAthleteCard`), so the wrapper is rendered in review rather than the card alone. That is a cheap follow-up, not part of this fix.
