/*
 * Copyright (C) 2026 Altuuuuu contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation.
 */
package com.buzbuz.smartautoclicker.auth

import android.content.Context

internal class AuthStore(context: Context) {

    private val preferences = context.getSharedPreferences("altuuuuu_auth", Context.MODE_PRIVATE)

    val accessToken: String?
        get() = preferences.getString(KEY_ACCESS_TOKEN, null)

    val refreshToken: String?
        get() = preferences.getString(KEY_REFRESH_TOKEN, null)

    /**
     * An access token is enough to keep using a session while it is valid.
     * The refresh token is only needed after the short-lived access token
     * expires, so its absence should not make the app immediately route the
     * user back to the login screen.
     */
    fun hasSession(): Boolean = !accessToken.isNullOrBlank()

    fun saveSession(accessToken: String, refreshToken: String?) {
        preferences.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .apply {
                if (refreshToken == null) remove(KEY_REFRESH_TOKEN)
                else putString(KEY_REFRESH_TOKEN, refreshToken)
            }
            // Auth tokens must be on disk before the activity can finish. Using
            // apply() here can lose a freshly-created session if Android kills
            // the process before the asynchronous write completes.
            .commit()
    }

    fun saveProfile(profile: UserProfile) {
        preferences.edit()
            .putString(KEY_PROFILE_ID, profile.id)
            .putString(KEY_PROFILE_EMAIL, profile.email)
            .putString(KEY_PROFILE_APPROVAL_STATUS, profile.approvalStatus.name)
            .putString(KEY_PROFILE_SUBSCRIPTION_PLAN, profile.subscriptionPlan.name)
            .putBoolean(KEY_PROFILE_IS_ADMIN, profile.isAdmin)
            .apply {
                if (profile.subscriptionDays == null) remove(KEY_PROFILE_SUBSCRIPTION_DAYS)
                else putInt(KEY_PROFILE_SUBSCRIPTION_DAYS, profile.subscriptionDays)
                if (profile.subscriptionExpiresAt == null) remove(KEY_PROFILE_SUBSCRIPTION_EXPIRES_AT)
                else putLong(KEY_PROFILE_SUBSCRIPTION_EXPIRES_AT, profile.subscriptionExpiresAt)
            }
            .commit()
    }

    fun cachedProfile(): UserProfile? {
        val id = preferences.getString(KEY_PROFILE_ID, null)?.takeIf { it.isNotBlank() }
            ?: return null
        val subscriptionExpiresAt = if (preferences.contains(KEY_PROFILE_SUBSCRIPTION_EXPIRES_AT)) {
            preferences.getLong(KEY_PROFILE_SUBSCRIPTION_EXPIRES_AT, 0L)
        } else {
            null
        }
        val subscriptionDays = if (preferences.contains(KEY_PROFILE_SUBSCRIPTION_DAYS)) {
            preferences.getInt(KEY_PROFILE_SUBSCRIPTION_DAYS, 0)
        } else {
            null
        }
        return UserProfile(
            id = id,
            email = preferences.getString(KEY_PROFILE_EMAIL, "").orEmpty(),
            approvalStatus = ApprovalStatus.fromValue(
                preferences.getString(KEY_PROFILE_APPROVAL_STATUS, null),
            ),
            subscriptionPlan = SubscriptionPlan.fromValue(
                preferences.getString(KEY_PROFILE_SUBSCRIPTION_PLAN, null),
            ),
            subscriptionDays = subscriptionDays,
            subscriptionExpiresAt = subscriptionExpiresAt,
            isAdmin = preferences.getBoolean(KEY_PROFILE_IS_ADMIN, false),
        )
    }

    fun clear() {
        preferences.edit().clear().commit()
    }

    fun markSessionValidated() {
        preferences.edit()
            .putLong(KEY_LAST_VALIDATED_AT, System.currentTimeMillis())
            .apply()
    }

    fun consumeRecentSessionValidation(maxAgeMs: Long = 15_000): Boolean {
        val validatedAt = preferences.getLong(KEY_LAST_VALIDATED_AT, 0L)
        preferences.edit().remove(KEY_LAST_VALIDATED_AT).apply()
        val age = System.currentTimeMillis() - validatedAt
        return validatedAt > 0L && age in 0..maxAgeMs
    }

    private companion object {
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_LAST_VALIDATED_AT = "last_validated_at"
        const val KEY_PROFILE_ID = "profile_id"
        const val KEY_PROFILE_EMAIL = "profile_email"
        const val KEY_PROFILE_APPROVAL_STATUS = "profile_approval_status"
        const val KEY_PROFILE_SUBSCRIPTION_PLAN = "profile_subscription_plan"
        const val KEY_PROFILE_SUBSCRIPTION_DAYS = "profile_subscription_days"
        const val KEY_PROFILE_SUBSCRIPTION_EXPIRES_AT = "profile_subscription_expires_at"
        const val KEY_PROFILE_IS_ADMIN = "profile_is_admin"
    }
}