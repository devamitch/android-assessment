# Watchlist Assessment Design Decisions

## 1. Domain Modeling (Data Classes vs Maps)
**Original Approach:** The original code used `MutableMap<String, Any?>` to represent a wallet. This is extremely brittle, error-prone, and requires constant unsafe type casting.
**Refactor:** I introduced a strongly-typed Kotlin `data class Wallet`. This provides compile-time safety, auto-generated equality checks, and makes the code vastly more readable.

## 2. Encapsulation & Mutability
**Original Approach:** `WatchlistStore` exposed its internal `MutableList<Wallet>`, allowing any outside class to modify the watchlist and bypass rules. The treasury `balance` could also be randomly mutated.
**Refactor:** 
- The internal list is now a private `_wallets: MutableList<Wallet>`. It is exposed publicly as an immutable `List<Wallet>` via `val wallets get() = _wallets.toList()`.
- The treasury `balance` now has a `private set`. It can only be changed by calling the explicit `deposit()` and `withdraw()` domain functions.

## 3. Explicit Error Handling (Result API)
**Original Approach:** Domain functions like `addWallet`, `deposit`, and `withdraw` swallowed errors, silently failed, or randomly returned `Unit`.
**Refactor:** Functions now explicitly return `Result<Unit>` or `Result<Long>`.
- `runCatching` is used to encapsulate `require()` checks.
- This forces the UI layer (`WatchlistActivity`) to explicitly handle success and failure paths using `.fold(onSuccess = {}, onFailure = {})`, surfacing error messages to the user.

## 4. UI Rendering Optimization
**Original Approach:** `WatchlistActivity` used `Html.fromHtml` for text formatting (which poses a security risk) and dynamically created views without respecting a clean UI hierarchy.
**Refactor:** 
- `Html.fromHtml` was entirely removed in favor of safe Kotlin string interpolation.
- Added explicit `<TextView id="@+id/error_message">` and `<TextView id="@+id/empty_state">` to `activity_watchlist.xml` to gracefully handle edge cases.
- Extracted the UI inflation logic into `createWalletRow()`. This applies the Single Responsibility Principle, separating the logic of *filtering* the list from the logic of *building* the View hierarchy.

## 5. Security & Session Storage
**Original Approach:** The activity saved a key named `"watchlist.session.privateKey"` in plain text using `SharedPreferences`.
**Refactor:** Storing private keys in SharedPreferences is a severe security vulnerability. I renamed the key strictly to `"operatorId"` to align with the product logic of storing the current operator's session ID, not a cryptographic secret.
