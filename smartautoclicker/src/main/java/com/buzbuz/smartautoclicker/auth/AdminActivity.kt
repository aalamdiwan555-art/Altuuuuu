/*
 * Copyright (C) 2026 Altuuuuu contributors
 */
package com.buzbuz.smartautoclicker.auth

import android.os.Bundle
import android.text.format.DateFormat
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.buzbuz.smartautoclicker.R
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.util.Date
import kotlin.math.roundToInt

class AdminActivity : AppCompatActivity() {

    private lateinit var repository: SupabaseAuthRepository
    private lateinit var usersContainer: LinearLayout
    private lateinit var refreshButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = SupabaseAuthRepository(this)
        buildShell()
        verifyAdminAndLoadUsers()
    }

    private fun buildShell() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dp(), 32.dp(), 24.dp(), 24.dp())
        }
        root.addView(TextView(this).apply {
            text = getString(R.string.auth_admin_title)
            textSize = 28f
            gravity = Gravity.CENTER
        }, params(8))
        root.addView(TextView(this).apply {
            text = getString(R.string.auth_admin_subtitle)
            textSize = 15f
            gravity = Gravity.CENTER
        }, params(20))

        val actions = LinearLayout(this).apply { gravity = Gravity.CENTER }
        refreshButton = Button(this).apply {
            text = getString(R.string.auth_refresh)
            setOnClickListener { verifyAdminAndLoadUsers() }
        }
        actions.addView(refreshButton, weightedButtonParams())
        actions.addView(Button(this).apply {
            text = getString(R.string.auth_logout)
            setOnClickListener {
                lifecycleScope.launch {
                    suspendRunCatching { repository.signOut() }
                    finish()
                }
            }
        }, weightedButtonParams())
        root.addView(actions, params(16))

        usersContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(ScrollView(this).apply {
            isFillViewport = true
            addView(usersContainer)
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun verifyAdminAndLoadUsers() {
        showState(getString(R.string.auth_loading_users))
        refreshButton.isEnabled = false
        lifecycleScope.launch {
            if (!repository.isConfigured) {
                showState(getString(R.string.auth_configuration_missing))
                refreshButton.isEnabled = true
                return@launch
            }

            suspendRunCatching { repository.loadCurrentProfile() }
                .onSuccess { profile ->
                    if (profile?.isAdmin == true) {
                        loadUsers()
                    } else {
                        redirectToAuth()
                    }
                }
                .onFailure {
                    if (it is AuthException && it.statusCode == 401) {
                        redirectToAuth()
                    } else {
                        showState(it.message ?: getString(R.string.auth_generic_error))
                        refreshButton.isEnabled = true
                    }
                }
        }
    }

    private fun loadUsers() {
        showState(getString(R.string.auth_loading_users))
        lifecycleScope.launch {
            suspendRunCatching { repository.loadUsersForAdmin() }
                .onSuccess { users ->
                    refreshButton.isEnabled = true
                    usersContainer.removeAllViews()
                    if (users.isEmpty()) {
                        usersContainer.addView(TextView(this@AdminActivity).apply {
                            text = getString(R.string.auth_no_users)
                            textSize = 16f
                            gravity = Gravity.CENTER
                        }, params(24))
                    } else {
                        users.forEach(::addUserRow)
                    }
                }
                .onFailure {
                    if (it is AuthException && it.statusCode == 401) {
                        redirectToAuth()
                    } else {
                        showState(it.message ?: getString(R.string.auth_generic_error))
                        refreshButton.isEnabled = true
                    }
                }
        }
    }

    private fun addUserRow(profile: UserProfile) {
        val cardContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp(), 16.dp(), 20.dp(), 16.dp())
        }
        cardContent.addView(TextView(this).apply {
            text = profile.email.ifBlank { getString(R.string.auth_unknown_email) }
            textSize = 17f
        })
        cardContent.addView(TextView(this).apply {
            text = getString(R.string.auth_user_status, profile.approvalStatus.name)
        }, params(6))
        cardContent.addView(TextView(this).apply {
            text = getString(R.string.auth_user_subscription, subscriptionText(profile))
        }, params(4))

        val actions = LinearLayout(this).apply { gravity = Gravity.END }
        actions.addView(Button(this).apply {
            text = getString(R.string.auth_approve)
            setOnClickListener { showPlanPicker(profile, this) }
        }, weightedButtonParams())
        actions.addView(Button(this).apply {
            text = getString(R.string.auth_decline)
            setOnClickListener { confirmDecline(profile, this) }
        }, weightedButtonParams())
        cardContent.addView(actions, params(12))

        val card = MaterialCardView(this).apply {
            addView(cardContent)
            contentDescription = profile.email
        }
        usersContainer.addView(card, params(12))
    }

    private fun showPlanPicker(profile: UserProfile, actionButton: Button) {
        val plans = arrayOf(
            getString(R.string.auth_plan_one_day),
            getString(R.string.auth_plan_two_days),
            getString(R.string.auth_plan_three_days),
            getString(R.string.auth_plan_lifetime),
        )
        val values = arrayOf(
            SubscriptionPlan.ONE_DAY,
            SubscriptionPlan.TWO_DAYS,
            SubscriptionPlan.THREE_DAYS,
            SubscriptionPlan.LIFETIME,
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.auth_choose_subscription))
            .setItems(plans) { _, index ->
                actionButton.isEnabled = false
                lifecycleScope.launch {
                    suspendRunCatching { repository.approveUser(profile.id, values[index]) }
                        .onSuccess { loadUsers() }
                        .onFailure { actionButton.isEnabled = true; showError(it) }
                }
            }
            .show()
    }

    private fun confirmDecline(profile: UserProfile, actionButton: Button) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.auth_decline))
            .setMessage(getString(R.string.auth_decline_confirmation, profile.email))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(getString(R.string.auth_decline)) { _, _ ->
                actionButton.isEnabled = false
                lifecycleScope.launch {
                    suspendRunCatching { repository.declineUser(profile.id) }
                        .onSuccess { loadUsers() }
                        .onFailure { actionButton.isEnabled = true; showError(it) }
                }
            }
            .show()
    }

    private fun redirectToAuth() {
        startActivity(android.content.Intent(this, AuthActivity::class.java))
        finish()
    }

    private fun showState(message: String) {
        usersContainer.removeAllViews()
        usersContainer.addView(TextView(this).apply {
            text = message
            textSize = 16f
            gravity = Gravity.CENTER
        }, params(24))
    }

    private fun showError(error: Throwable) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.auth_generic_error))
            .setMessage(error.message ?: getString(R.string.auth_generic_error))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun subscriptionText(profile: UserProfile): String =
        if (profile.subscriptionPlan == SubscriptionPlan.LIFETIME) {
            getString(R.string.auth_plan_lifetime)
        } else {
            profile.subscriptionPlan.name + profile.subscriptionExpiresAt?.let {
                " · " + DateFormat.getDateFormat(this).format(Date(it))
            }.orEmpty()
        }

    private fun params(bottom: Int) =
        LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = bottom.dp() }

    private fun weightedButtonParams() =
        LinearLayout.LayoutParams(0, -2, 1f).apply {
            marginStart = 4.dp()
            marginEnd = 4.dp()
        }

    private fun Int.dp(): Int =
        (this * resources.displayMetrics.density).roundToInt()
}

private suspend fun <T> suspendRunCatching(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (error: Throwable) {
        Result.failure(error)
    }
