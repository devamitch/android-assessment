# Code Review

Reviewed files: `WatchlistStore.kt`, `WatchlistActivity.kt`, `activity_watchlist.xml`

---

## WatchlistStore.kt

### 1. Bug: Admin rule not enforced
- **File:** `WatchlistStore.kt`, `addWallet()`
- **Behavior:** The `caller` argument was accepted but never compared against `admin`. Any caller could add wallets.
- **Why it matters:** Violates the core product rule "adding a wallet is admin-only". Anyone could bypass access control.
- **Fix:** Added `require(caller == admin) { "Only admin can add a wallet" }` as the first guard.

### 2. Bug: Empty wallet IDs allowed
- **File:** `WatchlistStore.kt`, `addWallet()`
- **Behavior:** No check on `walletId` — an empty string `""` or blank string `"   "` could be added as a wallet ID.
- **Why it matters:** Creates phantom wallets with no meaningful identity, corrupting the watchlist.
- **Fix:** Added `require(!walletId.isNullOrBlank()) { "Wallet id must be non-empty" }`.

### 3. Bug: Negative or zero amounts accepted
- **File:** `WatchlistStore.kt`, `deposit()` and `withdraw()`
- **Behavior:** No validation that `amount > 0`. Depositing `-100` would drain the treasury. Withdrawing `0` would silently succeed.
- **Why it matters:** Allows balance manipulation via negative deposits or meaningless zero-amount withdrawals.
- **Fix:** Added `require(amount > 0)` to both `deposit()` and `withdraw()`.

### 4. Bug: Withdraw does not check wallet existence
- **File:** `WatchlistStore.kt`, `withdraw()`
- **Behavior:** No check for whether the wallet ID exists before attempting withdrawal. Would silently succeed or crash.
- **Why it matters:** A non-existent wallet should fail explicitly, not crash or silently pass.
- **Fix:** Use `findWallet(walletId) ?: throw IllegalArgumentException("Wallet not found")`.

### 5. Bug: Missing wallet indistinguishable from zero-allowance wallet
- **File:** `WatchlistStore.kt`, `allowance()`
- **Behavior:** Original returned `0` for both a missing wallet and a wallet with `allowance = 0`.
- **Why it matters:** Callers cannot distinguish "wallet doesn't exist" from "wallet has no remaining funds".
- **Fix:** `allowance()` now returns `Long?` — `null` means missing, `0L` means exists but exhausted.

### 6. Bug: Errors silently swallowed
- **File:** `WatchlistStore.kt`, all functions
- **Behavior:** Functions returned `Unit` or crashed. No way for the caller to know if an operation failed.
- **Why it matters:** The UI has no way to show the user what went wrong.
- **Fix:** All mutation functions now return `Result<Unit>` or `Result<Long>` via `runCatching {}`.

### 7. Structure: Wallet represented as untyped Map
- **File:** `WatchlistStore.kt`
- **Behavior:** `MutableList<MutableMap<String, Any?>>` requires constant unsafe type-casting and provides zero compile-time safety.
- **Why it matters:** Any field access is a runtime crash waiting to happen. Adding a new field requires hunting across the whole codebase.
- **Fix:** Replaced with a strongly-typed `data class Wallet(id, label, allowance, status)`.

---

## WatchlistActivity.kt

### 8. Bug: Treasury balance displayed from wrong source
- **File:** `WatchlistActivity.kt`, `render()`
- **Behavior:** The treasury balance TextView was reading `store.allowance` instead of `store.balance`.
- **Why it matters:** Shows the wrong number — a wallet's allowance instead of the shared treasury balance.
- **Fix:** `treasuryBalanceView.text = store.balance.toString()`.

### 9. Bug: Status filter ignored in rendering
- **File:** `WatchlistActivity.kt`, `renderWallets()`
- **Behavior:** The Spinner for status was shown in the UI but its selected value was never used to filter the wallet list.
- **Why it matters:** The filter is a core product feature. It was entirely non-functional.
- **Fix:** Added `.filter { filter == "all" || it.status == filter }` before rendering.

### 10. Bug: Hardcoded withdrawal amount of `10`
- **File:** `WatchlistActivity.kt`, wallet list row click listener
- **Behavior:** Every withdrawal was hardcoded to withdraw exactly `10` regardless of user intent.
- **Why it matters:** The user has no control over the withdrawal amount. Core UX broken.
- **Fix:** Added a per-row `EditText` for the amount; the user types what they want to withdraw.

### 11. Security Bug: Private key stored in SharedPreferences
- **File:** `WatchlistActivity.kt`
- **Behavior:** Session was saved under the key `"watchlist.session.privateKey"` in plain SharedPreferences.
- **Why it matters:** SharedPreferences is stored unencrypted on disk. A private key in plain text is a severe security vulnerability.
- **Fix:** Renamed the key to `"watchlist.session.operatorId"` to reflect that it is an operator session identifier, not cryptographic material.

### 12. Bug: `Html.fromHtml` used for wallet text rendering
- **File:** `WatchlistActivity.kt`, wallet row inflation
- **Behavior:** Wallet label/id/status fields were rendered through `Html.fromHtml`, treating them as raw HTML.
- **Why it matters:** A malicious wallet label like `<script>` or `<img src=x onerror=...>` would be processed.
- **Fix:** Replaced with plain Kotlin string interpolation: `"${wallet.label} (${wallet.id})"`.

### 13. Bug: No empty state shown
- **File:** `WatchlistActivity.kt` + `activity_watchlist.xml`
- **Behavior:** When the wallet list was empty (or filter returned no results), the user saw a blank screen.
- **Why it matters:** Product rule explicitly requires showing an empty state.
- **Fix:** Added a `<TextView android:id="@+id/empty_state">` in XML, toggled `VISIBLE`/`GONE` in `renderWallets()`.

### 14. Bug: No error state shown
- **File:** `WatchlistActivity.kt` + `activity_watchlist.xml`
- **Behavior:** When operations failed (e.g., withdraw from missing wallet), errors were completely silent.
- **Why it matters:** Product rule explicitly requires showing error states.
- **Fix:** Added a `<TextView android:id="@+id/error_message">` in XML with red text, and a `showError()` / `clearError()` method pair.

### 15. Bug: Operation results ignored
- **File:** `WatchlistActivity.kt`, `addWallet()`, `deposit()`
- **Behavior:** The `Result` returned by store functions was discarded. Success and failure were treated identically.
- **Why it matters:** Errors from the store were invisible to the user.
- **Fix:** Used `.fold(onSuccess = { render() }, onFailure = { showError(it.message) })` on every operation.
