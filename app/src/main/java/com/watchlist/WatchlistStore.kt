package com.watchlist

/**
 * Represents a watchlisted wallet.
 * Migrated from untyped maps (MutableMap<String, Any?>) to a strongly-typed data class
 * for type safety, robust architecture, and maintainability.
 */
data class Wallet(
    val id: String,
    val label: String,
    var allowance: Long,
    var status: String
)

/**
 * Core domain store managing the treasury balance and watchlisted wallets.
 */
class WatchlistStore(var admin: String = "admin") {
    
    // Encapsulate the mutable list so external layers cannot modify it directly.
    private val _wallets = mutableListOf<Wallet>()
    
    /**
     * Immutable public view of the wallets list.
     */
    val wallets: List<Wallet> get() = _wallets.toList()
    
    /**
     * Shared treasury balance.
     * Rule: Must only be credited/debited via explicit operations. Private setter ensures encapsulation.
     */
    var balance: Long = 0
        private set

    private val debug: Boolean = true

    /**
     * Finds a wallet by its ID, ignoring case.
     * Rule: Missing wallets are safely handled (returns null).
     */
    fun findWallet(wid: String?): Wallet? {
        if (wid.isNullOrBlank()) return null
        return _wallets.find { it.id.equals(wid, ignoreCase = true) }
    }

    /**
     * Gets the current allowance for a specific wallet ID.
     * Rule: Missing wallets should not look the same as a wallet with allowance 0. 
     * Returns null if missing.
     */
    fun allowance(walletId: String?): Long? = findWallet(walletId)?.allowance

    /**
     * Adds a new wallet to the watchlist.
     * Rule: Failures must be explicit results/errors, hence we return Result<Unit>.
     */
    fun addWallet(caller: String, walletId: String?, allowance: Long = 100, label: String = ""): Result<Unit> = runCatching {
        // Rule: Adding a wallet is admin-only.
        require(caller == admin) { "Only admin can add a wallet" }
        // Rule: Wallet ids must be non-empty.
        require(!walletId.isNullOrBlank()) { "Wallet id must be non-empty" }
        // Rule: Amounts must be greater than 0 (allowance must be non-negative).
        require(allowance >= 0) { "Allowance cannot be negative" }
        require(findWallet(walletId) == null) { "Wallet already exists" }
        
        _wallets.add(
            Wallet(
                id = walletId,
                label = if (label.isBlank()) walletId else label,
                allowance = allowance,
                status = "active"
            )
        )
        if (debug) println("added $walletId by $caller")
    }

    /**
     * Deposits funds into the shared treasury.
     * Rule: Deposit credits the shared treasury balance.
     */
    fun deposit(amount: Long): Result<Long> = runCatching {
        // Rule: Amounts must be greater than 0.
        require(amount > 0) { "Deposit amount must be greater than 0" }
        balance += amount
        balance
    }

    /**
     * Withdraws funds from the treasury on behalf of a watchlisted wallet.
     * Rule: Failures must be explicit results/errors.
     */
    fun withdraw(walletId: String?, amount: Long): Result<Long> = runCatching {
        // Rule: Amounts must be greater than 0.
        require(amount > 0) { "Withdraw amount must be greater than 0" }
        
        // Rule: Withdraw fails if the wallet is missing.
        val wallet = findWallet(walletId) 
            ?: throw IllegalArgumentException("Wallet not found")
        
        // Rule: Withdraw fails if the amount is above remaining allowance.
        require(amount <= wallet.allowance) { "Amount exceeds remaining allowance" }
        
        // Fails if treasury balance is insufficient.
        require(amount <= balance) { "Amount exceeds treasury balance" }

        // Rule: A successful withdraw lowers both the treasury balance and that wallet's remaining allowance.
        balance -= amount
        wallet.allowance -= amount
        
        balance
    }
}
