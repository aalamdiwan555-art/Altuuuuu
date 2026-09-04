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
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope

import com.buzbuz.smartautoclicker.R
import com.buzbuz.smartautoclicker.auth.AdminActivity
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
        authRepository = SupabaseAuthRepository(this)
        setContentView(R.layout.auth_loading)

        lifecycleScope.launch {
            val profile = try {
                authRepository.loadCurrentProfile()
            } catch (_: Throwable) {
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

            accessGranted = true
            setContentView(R.layout.activity_scenario)
            scenarioViewModel.stopScenario()
            scenarioViewModel.requestUserConsentIfNeeded(this@ScenarioActivity)

            mediaProjectionRequest.registerForActivityResult(this@ScenarioActivity)

            // Splash screen is dismissed on first frame drawn, delay it until we have a user consent status
            findViewById<View>(android.R.id.content).delayDrawUntil {
                scenarioViewModel.userConsentState.value != UserConsentState.UNKNOWN
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (accessGranted) scenarioViewModel.refreshPurchaseState()
    }

    override fun startScenario(item: ScenarioListUiState.Item.ScenarioItem) {
        lifecycleScope.launch {
            val profile = try {
                authRepository.loadCurrentProfile()
            } catch (_: Throwable) {
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
