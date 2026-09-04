/*
 * Copyright (C) 2026 Altuuuuu contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation.
 */
package com.buzbuz.smartautoclicker.auth

import java.text.SimpleDateFormat
import java.util.Locale

enum class ApprovalStatus {
    PENDING,
    APPROVED,
    DECLINED;

    companion object {
        fun fromValue(value: String?): ApprovalStatus =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: PENDING
    }
}

enum class SubscriptionPlan {
    NONE,
    ONE_DAY,
    TWO_DAYS,
    THREE_DAYS,
    LIFETIME;

    companion object {
        fun fromValue(value: String?): SubscriptionPlan =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: NONE
    }
}

data class UserProfile(
    val id: String,
    val email: String,
    val approvalStatus: ApprovalStatus,
    val subscriptionPlan: SubscriptionPlan,
    val subscriptionExpiresAt: Long?,
    val isAdmin: Boolean,
) {
    fun hasActiveSubscription(now: Long = System.currentTimeMillis()): Boolean =
        approvalStatus == ApprovalStatus.APPROVED &&
            (subscriptionPlan == SubscriptionPlan.LIFETIME ||
                (subscriptionExpiresAt != null && subscriptionExpiresAt > now))
}

internal fun parseIsoTimestamp(value: String?): Long? {
    if (value.isNullOrBlank()) return null

    val formats = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
    )
    return formats.firstNotNullOfOrNull { pattern ->
        runCatching {
            SimpleDateFormat(pattern, Locale.US).parse(value)?.time
        }.getOrNull()
    }
}