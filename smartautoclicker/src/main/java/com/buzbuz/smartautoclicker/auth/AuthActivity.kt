/*
 * Copyright (C) 2026 Altuuuuu contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation.
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
    private lateinit var root: LinearLayout
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = SupabaseAuthRepository(this)
        showAdminPanel()
    }

    private fun showAdminPanel() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 64, 40, 40)
        }

        val title = TextView(this).apply {
            text = "Admin Panel"
            textSize = 28f
            gravity = Gravity.CENTER
        }
        root.addView(title, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 24 })

        statusText = TextView(this).apply {
            text = "Loading users..."
            textSize = 16f
            gravity = Gravity.CENTER
        }
        root.addView(statusText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 20 })

        val refreshBtn = Button(this).apply {
            text = "Refresh Pending Users"
            setOnClickListener { loadPendingUsers() }
        }
        root.addView(refreshBtn)

        val logoutBtn = Button(this).apply {
            text = getString(R.string.auth_logout)
            setOnClickListener {
                lifecycleScope.launch {
                    suspendRunCatching { repository.signOut() }
                    finish()
                }
            }
        }
        root.addView(logoutBtn)

        setContentView(ScrollView(this).apply { addView(root) })
        loadPendingUsers()
    }

    private fun loadPendingUsers() {
        statusText.text = "Loading..."
        lifecycleScope.launch {
            suspendRunCatching { repository.getPendingUsers() }
                .onSuccess { users ->
                    if (users.isEmpty()) {
                        statusText.text = "No pending users"
                        return@onSuccess
                    }
                    statusText.text = "Found ${users.size} pending users:"
                    
                    // Remove old user views
                    while (root.childCount > 3) {
                        root.removeViewAt(3)
                    }

                    users.forEach { profile ->
                        val userLayout = LinearLayout(this@AdminActivity).apply {
                            orientation = LinearLayout.VERTICAL
                            setPadding(20, 20, 20, 20)
                        }

                        val emailText = TextView(this@AdminActivity).apply {
                            text = "${profile.email}\nStatus: ${profile.approvalStatus}"
                            textSize = 14f
                        }
                        userLayout.addView(emailText)

                        val buttonRow = LinearLayout(this@AdminActivity).apply {
                            orientation = LinearLayout.HORIZONTAL
                        }

                        val approveBtn = Button(this@AdminActivity).apply {
                            text = "Approve"
                            setOnClickListener {
                                lifecycleScope.launch {
                                    suspendRunCatching { repository.approveUser(profile.id) }
                                        .onSuccess {
                                            showMessage("Approved ${profile.email}")
                                            loadPendingUsers()
                                        }
                                        .onFailure { showMessage("Failed: ${it.message}") }
                                }
                            }
                        }
                        buttonRow.addView(approveBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

                        val declineBtn = Button(this@AdminActivity).apply {
                            text = "Decline"
                            setOnClickListener {
                                lifecycleScope.launch {
                                    suspendRunCatching { repository.declineUser(profile.id) }
                                        .onSuccess {
                                            showMessage("Declined ${profile.email}")
                                            loadPendingUsers()
                                        }
                                        .onFailure { showMessage("Failed: ${it.message}") }
                                }
                            }
                        }
                        buttonRow.addView(declineBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

                        userLayout.addView(buttonRow)
                        root.addView(userLayout)
                    }
                }
                .onFailure {
                    statusText.text = "Error: ${it.message}"
                }
        }
    }

    private fun showMessage(msg: String) {
        MaterialAlertDialogBuilder(this)
            .setMessage(msg)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
}

// FIX for Kotlin 2.2 / Gradle 9.5.1 - suspendRunCatching is now private in stdlib
// We define our own local version, same as in AuthActivity.kt
private suspend fun <T> suspendRunCatching(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (error: Throwable) {
        Result.failure(error)
    }
