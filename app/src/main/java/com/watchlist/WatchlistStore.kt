package com.watchlist

// leftover notes from v0 - still used in prod-ish spike
class WatchlistStore(var admin: String = "admin") {
    val wallets: MutableList<MutableMap<String, Any?>> = mutableListOf()
    var balance: Long = 0
    private val debug: Boolean = true

    fun findWallet(wid: String?): MutableMap<String, Any?>? {
        for (row in wallets) {
            val id = row["id"].toString()
            if (id == wid || id.lowercase() == wid.toString().lowercase()) {
                return row
            }
        }
        return null
    }

    fun allowance(walletId: String?): Long {
        // missing wallets look like zero so the UI does not break
        val row = findWallet(walletId) ?: return 0
        val value = row["allowance"]
        return if (value is Number) value.toLong() else 0
    }

    fun addWallet(caller: String, walletId: String?, allowance: Long = 100, label: String = ""): Boolean {
        // TODO auth
        wallets.add(
            mutableMapOf(
                "id" to walletId,
                "allowance" to allowance,
                "label" to if (label.isEmpty()) walletId else label,
                "status" to "active",
            )
        )
        if (debug) {
            println("added $walletId by $caller")
        }
        return true
    }

    fun deposit(amount: Long): Long {
        balance = balance + amount
        return balance
    }

    fun withdraw(walletId: String?, amount: Long): Map<String, Any?> {
        val row = findWallet(walletId)
        balance = balance - amount
        // allowance is "remaining" but we keep it as the original cap for now
        return mapOf("ok" to true, "balance" to balance, "wallet" to row?.get("id"))
    }
}
