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
    }
}