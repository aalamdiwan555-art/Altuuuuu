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
import com.buzbuz.smartautoclicker.auth.SupabaseAuthRepository
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

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        mediaProjectionRequest.registerForActivityResult(this@ScenarioActivity)

        authRepository = SupabaseAuthRepository(this)
        setContentView(R.layout.auth_loading)

        if (authRepository.consumeRecentSessionValidation()) {
            initializeScenario()
            return
        }

        lifecycleScope.launch {
            val profile = try {
                authRepository.loadCurrentProfile()
            } catch (error: Throwable) {
                // Keep the stored session on connectivity/server failures. A
                // transient Supabase error is not a reason to send the user
                // back through login.
                val canKeepSession = authRepository.hasStoredSession() &&
                    (error !is AuthException || !error.isSessionInvalid())
                if (canKeepSession) {
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

    override fun startScenario(item: ScenarioListUiState.Item.ScenarioItem) {
        lifecycleScope.launch {
            val profile = try {
                authRepository.loadCurrentProfile()
            } catch (error: Throwable) {
                if (authRepository.hasStoredSession() &&
                    (error !is AuthException || !error.isSessionInvalid())
                ) {
                    Toast.makeText(
                        this@ScenarioActivity,
                        R.string.auth_network_error,
                        Toast.LENGTH_LONG,
                    ).show()
                    return@launch
                }
                null
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
          val statusValue = findViewById<TextView>(R.id.subscription_status_value)
          statusValue.text = getString(R.string.subscription_status_checking)

          lifecycleScope.launch {
              val profile = try {
                  authRepository.loadCurrentProfile()
              } catch (_: Throwable) {
                  null
              }

              if (!isFinishing && accessGranted) {
                  statusValue.text = profile?.let(::subscriptionStatusText)
                      ?: getString(R.string.subscription_status_unavailable)
              }
          }
      }

      private fun subscriptionStatusText(profile: com.buzbuz.smartautoclicker.auth.UserProfile): String {
          val plan = when (profile.subscriptionPlan) {
              com.buzbuz.smartautoclicker.auth.SubscriptionPlan.LIFETIME ->
                  getString(R.string.subscription_status_lifetime)
              com.buzbuz.smartautoclicker.auth.SubscriptionPlan.CUSTOM ->
                  getString(
                      R.string.auth_plan_custom_format,
                      profile.subscriptionDays ?: 0,
                  )
              com.buzbuz.smartautoclicker.auth.SubscriptionPlan.ONE_DAY ->
                  getString(R.string.auth_plan_one_day)
              com.buzbuz.smartautoclicker.auth.SubscriptionPlan.TWO_DAYS ->
                  getString(R.string.auth_plan_two_days)
              com.buzbuz.smartautoclicker.auth.SubscriptionPlan.THREE_DAYS ->
                  getString(R.string.auth_plan_three_days)
              com.buzbuz.smartautoclicker.auth.SubscriptionPlan.NONE ->
                  getString(R.string.subscription_status_unavailable)
          }

          if (profile.subscriptionPlan == com.buzbuz.smartautoclicker.auth.SubscriptionPlan.LIFETIME) {
              return plan
          }

          val expiry = profile.subscriptionExpiresAt?.let {
              DateFormat.getDateFormat(this).format(Date(it))
          }

          return if (expiry.isNullOrBlank()) {
              plan
          } else {
              getString(R.string.subscription_status_expires, plan, expiry)
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
