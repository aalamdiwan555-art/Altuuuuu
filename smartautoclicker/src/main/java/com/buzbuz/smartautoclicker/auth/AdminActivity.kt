/*
 * Copyright (C) 2026 Altuuuuu contributors
 */
package com.buzbuz.smartautoclicker.auth

import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.buzbuz.smartautoclicker.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class AdminActivity : AppCompatActivity() {
    private lateinit var repository: SupabaseAuthRepository
    private lateinit var usersContainer: LinearLayout
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = SupabaseAuthRepository(this)
        buildShell()
        loadUsers()
    }
    private fun buildShell() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(28, 40, 28, 24) }
        root.addView(TextView(this).apply { text = getString(R.string.auth_admin_title); textSize = 28f; gravity = Gravity.CENTER }, params(16))
        val actions = LinearLayout(this).apply { gravity = Gravity.CENTER }
        actions.addView(Button(this).apply { text = getString(R.string.auth_refresh); setOnClickListener { loadUsers() } }, LinearLayout.LayoutParams(0, -2, 1f))
        actions.addView(Button(this).apply { text = getString(R.string.auth_logout); setOnClickListener { lifecycleScope.launch { suspendRunCatching { repository.signOut() }; finish() } } }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(actions, params(12))
        usersContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(usersContainer) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }
    private fun loadUsers() {
        usersContainer.removeAllViews()
        usersContainer.addView(TextView(this).apply { text = "Loading..." }, params(12))
        lifecycleScope.launch {
            suspendRunCatching { repository.loadUsersForAdmin() }
               .onSuccess { users ->
                    usersContainer.removeAllViews()
                    if (users.isEmpty()) usersContainer.addView(TextView(this@AdminActivity).apply { text = getString(R.string.auth_no_pending_users); textSize = 16f }, params(12))
                    else users.forEach(::addUserRow)
                }
               .onFailure { usersContainer.removeAllViews(); usersContainer.addView(TextView(this@AdminActivity).apply { text = it.message?: getString(R.string.auth_generic_error) }, params(12)) }
        }
    }
    private fun addUserRow(profile: UserProfile) {
        val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 16, 20, 16) }
        card.addView(TextView(this).apply { text = profile.email; textSize = 17f })
        card.addView(TextView(this).apply { text = profile.approvalStatus.name }, params(8))
        val actions = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        actions.addView(Button(this).apply { text = getString(R.string.auth_approve); setOnClickListener { showPlanPicker(profile) } })
        actions.addView(Button(this).apply { text = getString(R.string.auth_decline); setOnClickListener { decline(profile) } })
        card.addView(actions)
        usersContainer.addView(card, params(16))
    }
    private fun showPlanPicker(profile: UserProfile) {
        val plans = arrayOf(getString(R.string.auth_plan_one_day), getString(R.string.auth_plan_two_days), getString(R.string.auth_plan_three_days), getString(R.string.auth_plan_lifetime))
        val values = arrayOf(SubscriptionPlan.ONE_DAY, SubscriptionPlan.TWO_DAYS, SubscriptionPlan.THREE_DAYS, SubscriptionPlan.LIFETIME)
        MaterialAlertDialogBuilder(this).setTitle(getString(R.string.auth_choose_subscription)).setItems(plans) { _, i -> lifecycleScope.launch { suspendRunCatching { repository.approveUser(profile.id, values[i]) }.onSuccess { loadUsers() }.onFailure { showError(it) } } }.show()
    }
    private fun decline(profile: UserProfile) { lifecycleScope.launch { suspendRunCatching { repository.declineUser(profile.id) }.onSuccess { loadUsers() }.onFailure { showError(it) } } }
    private fun showError(error: Throwable) { MaterialAlertDialogBuilder(this).setTitle(getString(R.string.auth_generic_error)).setMessage(error.message?: getString(R.string.auth_generic_error)).setPositiveButton(android.R.string.ok, null).show() }
    private fun params(bottom: Int) = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = bottom }
}
private suspend fun <T> suspendRunCatching(block: suspend () -> T): Result<T> = try { Result.success(block()) } catch (e: Throwable) { Result.failure(e) }
