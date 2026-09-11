/*
 * Copyright (C) 2023 Kevin Buzeau
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.buzbuz.smartautoclicker.scenarios

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.text.format.DateFormat
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope

import com.buzbuz.smartautoclicker.R
import com.buzbuz.smartautoclicker.auth.AdminActivity
import com.buzbuz.smartautoclicker.auth.AuthException
import com.buzbuz.smartautoclicker.auth.AuthActivity
import com.buzbuz.smartautoclicker.auth.SubscriptionPlan
import com.buzbuz.smartautoclicker.auth.SupabaseAuthRepository
import com.buzbuz.smartautoclicker.auth.UserProfile
import com.buzbuz.smartautoclicker.scenarios.list.ScenarioListFragment
import com.buzbuz.smartautoclicker.scenarios.list.model.ScenarioListUiState
import com.buzbuz.smartautoclicker.core.base.extensions.delayDrawUntil
import com.buzbuz.smartautoclicker.core.display.recorder.MediaProjectionRequest
import com.buzbuz.smartautoclicker.core.domain.model.scenario.Scenario
import com.buzbuz.smartautoclicker.core.dumb.domain.model.DumbScenario
import com.buzbuz.smartautoclicker.core.ui.errors.createNoMediaProjectionDialog
import com.buzbuz.smartautoclicker.feature.revenue.UserConsentState
import com.buzbuz.smartautoclicker.scenarios.viewmodel.ScenarioViewModel

import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Date

/**
 * Entry point activity for the application.
 * Shown when the user clicks on the launcher icon for the application, this activity will displays the list of
 * available scenarios, if any.
 */
@AndroidEntryPoint
class ScenarioActivity : AppCompatActivity(), ScenarioListFragment.Listener {

    /** ViewModel providing the click scenarios data to the UI. */
    private val scenarioViewModel: ScenarioViewModel by viewModels()

    /** The result launcher for the projection permission dialog. */
    private val mediaProjectionRequest: MediaProjectionRequest = MediaProjectionRequest()

    /** Scenario clicked by the user. */
    private var requestedItem: ScenarioListUiState.Item.ScenarioItem? = null
    private lateinit var authRepository: SupabaseAuthRepository
    private var accessGranted = false
    private var lastKnownProfile: UserProfile? = null
    private var subscriptionCountdown: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        mediaProjectionRequest.registerForActivityResult(this@ScenarioActivity)

        authRepository = SupabaseAuthRepository(this)
        setContentView(R.layout.auth_loading)

        if (authRepository.consumeRecentSessionValidation()) {
            lastKnownProfile = authRepository.cachedProfile()
            initializeScenario()
            return
        }

        lifecycleScope.launch {
            val profile = try {
                authRepository.loadCurrentProfile().also { loadedProfile ->
                    if (loadedProfile != null) lastKnownProfile = loadedProfile
                }
            } catch (error: Throwable) {
                // Keep the stored session on connectivity/server failures. A
                // transient Supabase error is not a reason to send the user
                // back through login.
                val canKeepSession = authRepository.hasStoredSession() &&
                    (error !is AuthException || !error.isSessionInvalid())
                if (canKeepSession) {
                    lastKnownProfile = authRepository.cachedProfile()
                    initializeScenario()
                    return@launch
                }
                null
            }
            if (profile?.isAdmin == true) {
                startActivity(Intent(this@ScenarioActivity, AdminActivity::class.java))
                finish()
                return@launch
            }
            if (profile?.hasActiveSubscription() != true) {
                startActivity(Intent(this@ScenarioActivity, AuthActivity::class.java))
                finish()
                return@launch
            }

            initializeScenario()
        }
    }

    private fun initializeScenario() {
        lastKnownProfile = lastKnownProfile ?: authRepository.cachedProfile()
        accessGranted = true
        setContentView(R.layout.activity_scenario)
        scenarioViewModel.stopScenario()
        scenarioViewModel.requestUserConsentIfNeeded(this@ScenarioActivity)
        refreshSubscriptionStatus()

        // Splash screen is dismissed on first frame drawn, delay it until we have a user consent status
        findViewById<View>(android.R.id.content).delayDrawUntil {
            scenarioViewModel.userConsentState.value != UserConsentState.UNKNOWN
        }
    }

    override fun onResume() {
        super.onResume()
        if (accessGranted) {
            scenarioViewModel.refreshPurchaseState()
            refreshSubscriptionStatus()
        }
    }

    override fun onDestroy() {
        subscriptionCountdown?.cancel()
        super.onDestroy()
    }

    override fun startScenario(item: ScenarioListUiState.Item.ScenarioItem) {
        lifecycleScope.launch {
            val profile = try {
                authRepository.loadCurrentProfile().also { loadedProfile ->
                    if (loadedProfile != null) lastKnownProfile = loadedProfile
                }
            } catch (error: Throwable) {
                if (authRepository.hasStoredSession() &&
                    (error !is AuthException || !error.isSessionInvalid())
                ) {
                    // Keep the last verified subscription during a transient
                    // network/server failure so returning to the app does not
                    // make every scenario appear unusable.
                    lastKnownProfile ?: authRepository.cachedProfile()
                } else {
                    null
                }
            }
            if (profile?.hasActiveSubscription() != true) {
                Toast.makeText(this@ScenarioActivity, R.string.auth_expired_title, Toast.LENGTH_LONG).show()
                startActivity(Intent(this@ScenarioActivity, AuthActivity::class.java))
                finish()
                return@launch
            }

            requestedItem = item
            scenarioViewModel.startPermissionFlowIfNeeded(
                activity = this@ScenarioActivity,
                onAllGranted = ::onMandatoryPermissionsGranted,
            )
        }
    }

    private fun refreshSubscriptionStatus() {
        subscriptionCountdown?.cancel()
        val statusValue = findViewById<TextView>(R.id.subscription_status_value)
        statusValue.text = getString(R.string.subscription_status_checking)

        lifecycleScope.launch {
            val profile = try {
                authRepository.loadCurrentProfile().also { loadedProfile ->
                    if (loadedProfile != null) lastKnownProfile = loadedProfile
                }
            } catch (_: Throwable) {
                // Do not replace a valid subscription with “Unavailable” just
                // because the first resume request hit a temporary outage.
                lastKnownProfile ?: authRepository.cachedProfile()
            }

            if (!isFinishing && accessGranted) {
                if (profile == null) {
                    statusValue.text = getString(R.string.subscription_status_unavailable)
                } else {
                    startSubscriptionCountdown(profile, statusValue)
                }
            }
        }
    }

    private fun startSubscriptionCountdown(profile: UserProfile, statusValue: TextView) {
        if (profile.subscriptionPlan == SubscriptionPlan.LIFETIME) {
            statusValue.text = getString(R.string.subscription_status_lifetime)
            return
        }

        val expiryAt = profile.subscriptionExpiresAt
        if (expiryAt == null) {
            statusValue.text = subscriptionStatusText(profile, 0L)
            return
        }

        fun render(remainingMillis: Long) {
            statusValue.text = subscriptionStatusText(profile, remainingMillis)
        }

        val remainingMillis = expiryAt - System.currentTimeMillis()
        render(remainingMillis)
        if (remainingMillis <= 0L) return

        subscriptionCountdown = object : CountDownTimer(remainingMillis, 1_000L) {
            override fun onTick(millisUntilFinished: Long) {
                render(millisUntilFinished)
            }

            override fun onFinish() {
                render(0L)
            }
        }.start()
    }

    private fun subscriptionStatusText(profile: UserProfile, remainingMillis: Long): String {
          val plan = when (profile.subscriptionPlan) {
              SubscriptionPlan.LIFETIME ->
                  getString(R.string.subscription_status_lifetime)
              SubscriptionPlan.CUSTOM ->
                  getString(
                      R.string.auth_plan_custom_format,
                      profile.subscriptionDays ?: 0,
                  )
              SubscriptionPlan.ONE_DAY ->
                  getString(R.string.auth_plan_one_day)
              SubscriptionPlan.TWO_DAYS ->
                  getString(R.string.auth_plan_two_days)
              SubscriptionPlan.THREE_DAYS ->
                  getString(R.string.auth_plan_three_days)
              SubscriptionPlan.NONE ->
                  getString(R.string.subscription_status_unavailable)
        }

        val expiry = profile.subscriptionExpiresAt?.let {
            val date = Date(it)
            "${DateFormat.getDateFormat(this).format(date)} ${DateFormat.getTimeFormat(this).format(date)}"
        }

        if (expiry.isNullOrBlank()) return plan
        if (remainingMillis <= 0L) {
            return getString(R.string.subscription_status_expired, plan, expiry)
        }

        return getString(
            R.string.subscription_status_expires,
            plan,
            expiry,
            formatRemainingTime(remainingMillis),
        )
    }

    private fun formatRemainingTime(remainingMillis: Long): String {
        val totalSeconds = (remainingMillis / 1_000L).coerceAtLeast(0L)
        val days = totalSeconds / 86_400L
        val hours = (totalSeconds % 86_400L) / 3_600L
        val minutes = (totalSeconds % 3_600L) / 60L
        val seconds = totalSeconds % 60L

        return when {
            days > 0L -> getString(R.string.subscription_remaining_days, days, hours, minutes)
            hours > 0L -> getString(R.string.subscription_remaining_hours, hours, minutes, seconds)
            else -> getString(R.string.subscription_remaining_minutes, minutes, seconds)
        }
    }

      private fun onMandatoryPermissionsGranted() {
        scenarioViewModel.startTroubleshootingFlowIfNeeded(this) {
            when (val scenario = requestedItem?.scenario) {
                is DumbScenario -> startDumbScenario(scenario)
                is Scenario -> mediaProjectionRequest.showMediaProjectionWarning(
                    context = this,
                    forceEntireScreen = scenarioViewModel.isEntireScreenCaptureForced(),
                    onSuccess = { resultCode, data -> startSmartScenario(resultCode, data, scenario) },
                    onFailure = { showProjectionDeniedToast() },
                    onError = { showUnsupportedDeviceDialog() },
                )
            }
        }
    }

    /**
     * Some devices messes up too much with Android.
     * Display a dialog in those cases and stop the application.
     */
    private fun showUnsupportedDeviceDialog() {
        createNoMediaProjectionDialog { finish() }.show()
    }

    private fun startDumbScenario(scenario: DumbScenario) {
        handleScenarioStartResult(scenarioViewModel.loadDumbScenario(
            context = this,
            scenario = scenario,
        ))
    }

    private fun startSmartScenario(resultCode: Int, data: Intent, scenario: Scenario) {
        handleScenarioStartResult(scenarioViewModel.loadSmartScenario(
            context = this,
            resultCode = resultCode,
            data = data,
            scenario = scenario,
        ))
    }

    private fun handleScenarioStartResult(result: Boolean) {
        if (result) finish()
        else Toast.makeText(this, R.string.toast_denied_foreground_permission, Toast.LENGTH_SHORT).show()
    }

    private fun showProjectionDeniedToast() {
        Toast.makeText(this, R.string.toast_denied_screen_sharing_permission, Toast.LENGTH_SHORT).show()
    }
}
