/*
 * Copyright (C) 2026 Altuuuuu contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation.
 */
package com.buzbuz.smartautoclicker.auth

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.buzbuz.smartautoclicker.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class AuthActivity : AppCompatActivity() {

    private lateinit var repository: SupabaseAuthRepository
    private lateinit var root: LinearLayout
    private var isSignUp = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = SupabaseAuthRepository(this)
        showLoading()
        refreshSession()
    }

    private fun refreshSession() {
        showLoading()
        lifecycleScope.launch {
            if (!repository.isConfigured) {
                showMessage(
                    title = getString(R.string.auth_configuration_missing_title),
                    message = getString(R.string.auth_configuration_missing),
                    returnToAuthForm = false,
                )
                return@launch
            }

            suspendRunCatching { repository.loadCurrentProfile() }
                .onSuccess { profile ->
                    if (profile?.hasActiveSubscription() == true) {
                        repository.markSessionValidated()
                    }
                    routeProfile(profile)
                }
                .onFailure { showMessage(getString(R.string.auth_generic_error), it.message ?: "") }
        }
    }

    private fun routeProfile(profile: UserProfile?) {
        when {
            profile == null -> showAuthForm()
            profile.isAdmin -> {
                startActivity(Intent(this, AdminActivity::class.java))
                finish()
            }
            profile.hasActiveSubscription() -> {
                startActivity(Intent(this, com.buzbuz.smartautoclicker.scenarios.ScenarioActivity::class.java))
                finish()
            }
            profile.approvalStatus == ApprovalStatus.PENDING -> showMessage(
                getString(R.string.auth_pending_title),
                getString(R.string.auth_pending_message),
            )
            profile.approvalStatus == ApprovalStatus.DECLINED -> showMessage(
                getString(R.string.auth_declined_title),
                getString(R.string.auth_declined_message),
            )
            else -> showMessage(
                getString(R.string.auth_expired_title),
                getString(R.string.auth_expired_message),
            )
        }
    }

    private fun showAuthForm() {
        root = baseLayout().apply {
            setBackgroundColor(Color.rgb(247, 244, 238))
        }
        root.addView(TextView(this).apply {
            text = "ALTUUUUU"
            textSize = 12f
            letterSpacing = 0.28f
            setTextColor(Color.rgb(45, 117, 91))
        }, marginParams(bottom = 18))
        val title = TextView(this).apply {
            text = if (isSignUp) getString(R.string.auth_sign_up) else getString(R.string.auth_sign_in)
            textSize = 30f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(31, 54, 62))
        }
        root.addView(title, marginParams(bottom = 24))

        root.addView(TextView(this).apply {
            text = if (isSignUp) {
                getString(R.string.auth_sign_up_subtitle)
            } else {
                getString(R.string.auth_sign_in_subtitle)
            }
            textSize = 16f
            gravity = Gravity.CENTER
        }, marginParams(bottom = 24))

        val email = TextInputEditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setSingleLine(true)
        }
        val emailLayout = TextInputLayout(this).apply {
            hint = getString(R.string.auth_email)
            addView(email)
        }
        root.addView(emailLayout, marginParams(bottom = 12))

        val password = TextInputEditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
        }
        val passwordLayout = TextInputLayout(this).apply {
            hint = getString(R.string.auth_password)
            endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
            addView(password)
        }
        root.addView(passwordLayout, marginParams(bottom = 20))

        val submit = Button(this).apply {
            text = if (isSignUp) getString(R.string.auth_submit_sign_up) else getString(R.string.auth_submit_sign_in)
            isAllCaps = false
            textSize = 15f
            minHeight = 54.dp()
        }
        
        submit.setOnClickListener { view ->
            val emailValue = email.text?.toString()?.trim().orEmpty()
            val passwordValue = password.text?.toString().orEmpty()
            emailLayout.error = when {
                emailValue.isBlank() -> getString(R.string.auth_email_required)
                !android.util.Patterns.EMAIL_ADDRESS.matcher(emailValue).matches() ->
                    getString(R.string.auth_email_invalid)
                else -> null
            }
            passwordLayout.error = if (passwordValue.length < 6) {
                getString(R.string.auth_password_too_short)
            } else {
                null
            }
            if (emailLayout.error != null || passwordLayout.error != null) {
                return@setOnClickListener
            }

            view.isEnabled = false
            
            lifecycleScope.launch {
                if (isSignUp) {
                    suspendRunCatching { repository.signUp(emailValue, passwordValue) }
                        .onSuccess {
                            isSignUp = false
                            showMessage(
                                getString(R.string.auth_pending_title),
                                getString(R.string.auth_pending_message),
                            )
                        }
                        .onFailure {
                            view.isEnabled = true
                            submit.isEnabled = true
                            showError(it)
                        }
                } else {
                    suspendRunCatching { repository.signIn(emailValue, passwordValue) }
                        .onSuccess { profile ->
                            // Route using the profile returned by sign-in. This avoids a
                            // second session request while the activity is transitioning.
                            if (profile.hasActiveSubscription()) {
                                repository.markSessionValidated()
                            }
                            routeProfile(profile)
                        }
                        .onFailure {
                            view.isEnabled = true
                            submit.isEnabled = true
                            showError(it)
                        }
                }
            }
        }
        root.addView(submit, fullWidthParams())

        val switchMode = Button(this).apply {
            text = if (isSignUp) getString(R.string.auth_switch_to_sign_in) else getString(R.string.auth_switch_to_sign_up)
            isAllCaps = false
            minHeight = 48.dp()
            setOnClickListener {
                isSignUp = !isSignUp
                showAuthForm()
            }
        }
        root.addView(switchMode, fullWidthParams())
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(Color.rgb(247, 244, 238))
            addView(root)
        })
    }

    private fun showMessage(title: String, message: String, returnToAuthForm: Boolean = true) {
        root = baseLayout().apply {
            setBackgroundColor(Color.rgb(247, 244, 238))
        }
        root.addView(TextView(this).apply {
            text = title
            textSize = 26f
            gravity = Gravity.CENTER
        }, marginParams(bottom = 18))
        root.addView(TextView(this).apply {
            text = message
            textSize = 16f
            gravity = Gravity.CENTER
        }, marginParams(bottom = 24))

        val refresh = Button(this).apply {
            text = getString(R.string.auth_refresh)
            setOnClickListener {
                showLoading()
                refreshSession()
            }
        }
        root.addView(refresh, fullWidthParams())

        val logout = Button(this).apply {
            text = getString(R.string.auth_logout)
            setOnClickListener {
                lifecycleScope.launch {
                    suspendRunCatching { repository.signOut() }
                    if (returnToAuthForm) showAuthForm() else finish()
                }
            }
        }
        root.addView(logout, fullWidthParams())
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(Color.rgb(247, 244, 238))
            addView(root)
        })
    }

    private fun showLoading() {
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(247, 244, 238))
            addView(ProgressBar(this@AuthActivity).apply {
                isIndeterminate = true
            }, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ).apply { gravity = Gravity.CENTER })
            addView(TextView(this@AuthActivity).apply {
                text = getString(R.string.auth_loading_session)
                textSize = 14f
                setTextColor(Color.rgb(89, 105, 103))
            }, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.CENTER
                topMargin = 72.dp()
            })
        })
    }

    private fun baseLayout(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(24.dp(), 32.dp(), 24.dp(), 24.dp())
    }

    private fun showError(error: Throwable) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.auth_generic_error))
            .setMessage(error.message ?: getString(R.string.auth_generic_error))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun marginParams(bottom: Int): LinearLayout.LayoutParams =
        fullWidthParams().apply { bottomMargin = bottom.dp() }

    private fun fullWidthParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

    private fun Int.dp(): Int =
        (this * resources.displayMetrics.density).roundToInt()
}

private suspend fun <T> suspendRunCatching(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (error: Throwable) {
        Result.failure(error)
    }
