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

    fun saveSession(accessToken: String, refreshToken: String?) {
        preferences.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .apply {
                if (refreshToken == null) remove(KEY_REFRESH_TOKEN)
                else putString(KEY_REFRESH_TOKEN, refreshToken)
            }
            .apply()
    }

    fun clear() {
        preferences.edit().clear().apply()
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