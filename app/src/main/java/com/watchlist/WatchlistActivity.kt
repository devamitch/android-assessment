package com.watchlist

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView

/**
 * Main UI Activity for the Treasury Watchlist.
 * Refactored to bind views once and programmatically construct the wallet list.
 */
class WatchlistActivity : Activity() {
    private val store = WatchlistStore("ops")

    // Rule: Do not store private keys or seed material in preferences.
    // Changed key from "watchlist.session.privateKey" to a safer "operatorId" designation.
    private val sessionKey = "watchlist.session.operatorId"

    private lateinit var errorView: TextView
    private lateinit var emptyStateView: TextView
    private lateinit var walletListContainer: LinearLayout
    private lateinit var treasuryBalanceView: TextView
    private lateinit var statusFilterSpinner: Spinner
    private lateinit var operatorInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_watchlist)

        bindViews()
        setupListeners()
        loadSession()
        render()
    }

    /**
     * Binds all static UI elements once to avoid repetitive findViewById calls during render loops.
     */
    private fun bindViews() {
        errorView = findViewById(R.id.error_message)
        emptyStateView = findViewById(R.id.empty_state)
        walletListContainer = findViewById(R.id.wallet_list)
        treasuryBalanceView = findViewById(R.id.treasury_balance)
        statusFilterSpinner = findViewById(R.id.status_filter)
        operatorInput = findViewById(R.id.operator_id)

        val statuses = arrayOf("all", "active", "archived")
        statusFilterSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, statuses)
    }

    private fun setupListeners() {
        findViewById<Button>(R.id.save_operator).setOnClickListener { saveSession() }
        findViewById<Button>(R.id.add_wallet).setOnClickListener { addWallet() }
        findViewById<Button>(R.id.deposit).setOnClickListener { deposit() }

        statusFilterSpinner.setOnItemSelectedListener(
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) = renderWallets()
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }
        )
    }

    private fun loadSession() {
        val prefs = getSharedPreferences("watchlist", MODE_PRIVATE)
        operatorInput.setText(prefs.getString(sessionKey, "ops"))
    }

    private fun saveSession() {
        getSharedPreferences("watchlist", MODE_PRIVATE)
            .edit()
            .putString(sessionKey, operatorInput.text.toString())
            .apply()
        clearError()
    }

    private fun caller(): String = operatorInput.text.toString().ifEmpty { "ops" }

    /**
     * Rule: Show empty and error states clearly.
     * Displays an explicit error message on the screen.
     */
    private fun showError(message: String?) {
        errorView.text = message ?: "An unknown error occurred"
        errorView.visibility = View.VISIBLE
    }

    private fun clearError() {
        errorView.visibility = View.GONE
        errorView.text = ""
    }

    /**
     * Rule: Handle failed withdraw/add/deposit instead of ignoring the response.
     */
    private fun addWallet() {
        clearError()
        val id = findViewById<EditText>(R.id.wallet_id).text.toString()
        val label = findViewById<EditText>(R.id.wallet_label).text.toString()
        val allowance = findViewById<EditText>(R.id.wallet_allowance).text.toString().toLongOrNull() ?: 100

        // Uses Kotlin Result fold to cleanly handle success and explicit error showing.
        store.addWallet(caller(), id, allowance, label)
            .onSuccess { render() }
            .onFailure { showError("Failed to add: ${it.message}") }
    }

    /**
     * Rule: Handle failed withdraw/add/deposit instead of ignoring the response.
     */
    private fun deposit() {
        clearError()
        val amount = findViewById<EditText>(R.id.deposit_amount).text.toString().toLongOrNull() ?: 0
        store.deposit(amount)
            .onSuccess { render() }
            .onFailure { showError("Deposit failed: ${it.message}") }
    }

    private fun render() {
        // Rule: Treasury balance on the screen must come from the treasury balance, not from an allowance lookup.
        treasuryBalanceView.text = store.balance.toString()
        renderWallets()
    }

    /**
     * Renders the dynamic list of wallets based on the selected filter.
     */
    private fun renderWallets() {
        val filter = statusFilterSpinner.selectedItem?.toString() ?: "all"
        walletListContainer.removeAllViews()

        // Rule: Status filter must actually filter the list.
        val filteredWallets = store.wallets.filter {
            filter == "all" || it.status == filter
        }

        // Rule: Show empty states clearly when the filtered list is empty.
        emptyStateView.visibility = if (filteredWallets.isEmpty()) View.VISIBLE else View.GONE

        for (wallet in filteredWallets) {
            walletListContainer.addView(createWalletRow(wallet))
        }
    }

    /**
     * Creates a single wallet row containing its info, an amount input, and a withdraw button.
     */
    private fun createWalletRow(wallet: Wallet): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 16, 0, 16)
        }

        // Rule: Do not render wallet fields as raw HTML.
        // We use simple string interpolation here instead of Html.fromHtml.
        val info = TextView(this).apply {
            text = "${wallet.label} (${wallet.id})\nAllowance: ${wallet.allowance} | Status: ${wallet.status}"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        // Rule: Withdraw amount should be chosen by the user, not a hard-coded tap of `10`.
        val amountInput = EditText(this).apply {
            hint = "Amount"
            inputType = InputType.TYPE_CLASS_NUMBER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val withdrawBtn = Button(this).apply {
            text = "Withdraw"
            setOnClickListener {
                clearError()
                val amount = amountInput.text.toString().toLongOrNull() ?: 0
                // Rule: Handle failed withdraw instead of ignoring.
                store.withdraw(wallet.id, amount)
                    .onSuccess { render() }
                    .onFailure { showError("Withdraw failed: ${it.message}") }
            }
        }

        row.addView(info)
        row.addView(amountInput)
        row.addView(withdrawBtn)
        return row
    }
}
