/*
 * Copyright (C) 2026 Altuuuuu contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation.
 */
package com.buzbuz.smartautoclicker.auth

import android.content.Context
import com.buzbuz.smartautoclicker.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

internal class SupabaseAuthRepository(context: Context) {

    private val store = AuthStore(context.applicationContext)

    val isConfigured: Boolean
        get() = BuildConfig.SUPABASE_URL.startsWith("https://") &&
            BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

    suspend fun signUp(email: String, password: String): String = withContext(Dispatchers.IO) {
        val response = request(
            path = "/auth/v1/signup",
            method = "POST",
            body = JSONObject()
                .put("email", email)
                .put("password", password)
                .toString(),
        )
        saveSessionIfPresent(response)
        response.optJSONObject("user")?.optString("email")
            ?: email
    }

    suspend fun signIn(email: String, password: String): UserProfile = withContext(Dispatchers.IO) {
        val response = request(
            path = "/auth/v1/token?grant_type=password",
            method = "POST",
            body = JSONObject()
                .put("email", email)
                .put("password", password)
                .toString(),
        )
        saveSessionIfPresent(response)
        loadCurrentProfile()
            ?: throw AuthException("The account profile has not been created yet.")
    }

    suspend fun loadCurrentProfile(): UserProfile? = withContext(Dispatchers.IO) {
        val token = store.accessToken ?: return@withContext null
        val user = runCatching {
            request("/auth/v1/user", token = token)
        }.getOrElse {
            store.clear()
            throw it
        }
        val userId = user.optString("id")
        if (userId.isBlank()) {
            store.clear()
            return@withContext null
        }

        val encodedId = URLEncoder.encode(userId, Charsets.UTF_8.name())
        val profiles = request(
            path = "/rest/v1/profiles?select=id,email,approval_status,subscription_plan,subscription_expires_at,is_admin&id=eq.$encodedId&limit=1",
            token = token,
        )
        val profile = profiles.optJSONArray("data")?.optJSONObject(0)
            ?: throw AuthException("Your account profile is not available yet.")
        profile.toUserProfile(userId = userId, fallbackEmail = user.optString("email"))
    }

    suspend fun hasActiveSubscription(): Boolean =
        loadCurrentProfile()?.hasActiveSubscription() == true

    suspend fun approveUser(userId: String, plan: SubscriptionPlan) {
        withContext(Dispatchers.IO) {
            request(
                path = "/rest/v1/rpc/admin_set_subscription",
                method = "POST",
                token = store.accessToken,
                body = JSONObject()
                    .put("target_user_id", userId)
                    .put("plan", plan.name)
                    .toString(),
            )
        }
    }

    suspend fun declineUser(userId: String) {
        withContext(Dispatchers.IO) {
            request(
                path = "/rest/v1/rpc/admin_decline_user",
                method = "POST",
                token = store.accessToken,
                body = JSONObject().put("target_user_id", userId).toString(),
            )
        }
    }

    suspend fun loadUsersForAdmin(): List<UserProfile> = withContext(Dispatchers.IO) {
        val profiles = request(
            path = "/rest/v1/profiles?select=id,email,approval_status,subscription_plan,subscription_expires_at,is_admin&is_admin=eq.false&order=created_at.desc",
            token = store.accessToken,
        )
        val rows = profiles.optJSONArray("data") ?: JSONArray()
        buildList {
            for (index in 0 until rows.length()) {
                rows.optJSONObject(index)?.let {
                    add(it.toUserProfile(fallbackEmail = it.optString("email")))
                }
            }
        }
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        store.accessToken?.let { token ->
            runCatching { request("/auth/v1/logout", method = "POST", token = token) }
        }
        store.clear()
    }

    private fun saveSessionIfPresent(response: JSONObject) {
        val accessToken = response.optString("access_token").takeIf { it.isNotBlank() } ?: return
        store.saveSession(accessToken, response.optString("refresh_token").takeIf { it.isNotBlank() })
    }

    private fun request(
        path: String,
        method: String = "GET",
        token: String? = store.accessToken,
        body: String? = null,
    ): JSONObject {
        if (!isConfigured) throw AuthException("Supabase is not configured.")

        val connection = (URL("${BuildConfig.SUPABASE_URL.trimEnd('/')}$path")
            .openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
            setRequestProperty("Accept", "application/json")
            if (token != null) setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }

        try {
            if (body != null) connection.outputStream.use { it.write(body.toByteArray()) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val message = runCatching {
                    JSONObject(responseText).optString("msg")
                        .ifBlank { JSONObject(responseText).optString("message") }
                        .ifBlank { JSONObject(responseText).optString("error_description") }
                }.getOrNull().orEmpty()
                if (status == 401) store.clear()
                throw AuthException(message.ifBlank { "Request failed with status $status." })
            }

            return if (responseText.isBlank()) JSONObject()
            else if (responseText.trimStart().startsWith("[")) JSONObject().put("data", JSONArray(responseText))
            else JSONObject(responseText)
        } catch (error: IOException) {
            throw AuthException(error.message ?: "Network request failed.")
        } finally {
            connection.disconnect()
        }
    }
}

internal class AuthException(message: String) : Exception(message)

private fun JSONObject.toUserProfile(
    userId: String = optString("id"),
    fallbackEmail: String = optString("email"),
): UserProfile =
    UserProfile(
        id = userId,
        email = optString("email").ifBlank { fallbackEmail },
        approvalStatus = ApprovalStatus.fromValue(optString("approval_status")),
        subscriptionPlan = SubscriptionPlan.fromValue(optString("subscription_plan")),
        subscriptionExpiresAt = parseIsoTimestamp(optString("subscription_expires_at")),
        isAdmin = optBoolean("is_admin", false),
    )