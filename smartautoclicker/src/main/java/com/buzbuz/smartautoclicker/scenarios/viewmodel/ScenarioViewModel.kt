/*
 * Copyright (C) 2024 Kevin Buzeau
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
package com.buzbuz.smartautoclicker.scenarios.viewmodel

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build

import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.PermissionChecker
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import com.buzbuz.smartautoclicker.core.base.data.AppComponentsProvider
import com.buzbuz.smartautoclicker.core.common.accessibility.domain.LocalAccessibilityServiceConnection
import com.buzbuz.smartautoclicker.core.common.quality.domain.QualityRepository
import com.buzbuz.smartautoclicker.core.display.config.DisplayConfigManager
import com.buzbuz.smartautoclicker.core.domain.IRepository
import com.buzbuz.smartautoclicker.core.dumb.domain.IDumbRepository
import com.buzbuz.smartautoclicker.core.dumb.domain.model.DumbScenario
import com.buzbuz.smartautoclicker.core.common.permissions.PermissionsController
import com.buzbuz.smartautoclicker.core.common.permissions.model.PermissionAccessibilityService
import com.buzbuz.smartautoclicker.core.common.permissions.model.PermissionOverlay
import com.buzbuz.smartautoclicker.core.common.permissions.model.PermissionPostNotification
import com.buzbuz.smartautoclicker.core.settings.domain.SettingsRepository
import com.buzbuz.smartautoclicker.feature.backup.domain.Backup
import com.buzbuz.smartautoclicker.feature.backup.domain.BackupRepository
import com.buzbuz.smartautoclicker.feature.revenue.IRevenueRepository
import com.buzbuz.smartautoclicker.feature.revenue.UserConsentState

import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/** AndroidViewModel for create/delete/list click scenarios from an LifecycleOwner. */
@HiltViewModel
class ScenarioViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val revenueRepository: IRevenueRepository,
    private val smartRepository: IRepository,
    private val dumbRepository: IDumbRepository,
    private val backupRepository: BackupRepository,
    private val displayConfigManager: DisplayConfigManager,
    private val qualityRepository: QualityRepository,
    private val permissionController: PermissionsController,
    private val settingsRepository: SettingsRepository,
    private val appComponentsProvider: AppComponentsProvider,
    private val serviceConnection: LocalAccessibilityServiceConnection,
) : ViewModel() {

    val userConsentState: StateFlow<UserConsentState> = revenueRepository.userConsentState
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserConsentState.UNKNOWN)

    fun ensureDefaultScenario() {
        viewModelScope.launch(Dispatchers.IO) {
            val preferences = context.getSharedPreferences(DEFAULT_SCENARIO_PREFERENCES, Context.MODE_PRIVATE)
            if (preferences.getBoolean(DEFAULT_SCENARIO_CREATED, false)) return@launch

            val smartScenarios = smartRepository.scenarios.first()
            val dumbScenarios = dumbRepository.dumbScenarios.first()
            if (smartScenarios.isEmpty() && dumbScenarios.isEmpty()) {
                var imported = false
                backupRepository.restoreScenarioBackup(
                    inputStream = context.assets.open(DEFAULT_SCENARIO_ASSET),
                    screenSize = displayConfigManager.displayConfig.sizePx,
                ).collect { state ->
                    if (state is Backup.Completed) {
                        imported = state.successCount > 0
                    }
                }
                if (!imported) return@launch
            }

            preferences.edit().putBoolean(DEFAULT_SCENARIO_CREATED, true).apply()
        }
    }

    fun isEntireScreenCaptureForced(): Boolean =
        settingsRepository.isEntireScreenCaptureForced()

    fun requestUserConsentIfNeeded(activity: Activity) {
        revenueRepository.refreshPurchases()
        revenueRepository.startUserConsentRequestUiFlowIfNeeded(activity)
    }

    fun refreshPurchaseState() {
        revenueRepository.refreshPurchases()
    }

    fun startPermissionFlowIfNeeded(activity: AppCompatActivity, onAllGranted: () -> Unit) {
        permissionController.startPermissionsUiFlow(
            activity = activity,
            permissions = listOf(
                PermissionOverlay(),
                PermissionAccessibilityService(
                    componentName = appComponentsProvider.klickrServiceComponentName,
                    isServiceRunning = { serviceConnection.isServiceStarted() },
                ),
                PermissionPostNotification(optional = true),
            ),
            onAllGranted = onAllGranted,
        )
    }

    fun startTroubleshootingFlowIfNeeded(activity: FragmentActivity, onCompleted: () -> Unit) {
        qualityRepository.startTroubleshootingUiFlowIfNeeded(activity, onCompleted)
    }

    /**
     * Start the overlay UI and instantiates the detection objects for a given scenario.
     *
     * This requires the media projection permission code and its data intent, they both can be retrieved using the
     * results of the activity intent provided by
     * [android.media.projection.MediaProjectionManager.createScreenCaptureIntent] (this Intent shows the dialog
     * warning about screen recording privacy). Any attempt to call this method without the correct screen capture
     * intent result will leads to a crash.
     *
     * @param resultCode the result code provided by the screen capture intent activity result callback
     * [android.app.Activity.onActivityResult]
     * @param data the data intent provided by the screen capture intent activity result callback
     * [android.app.Activity.onActivityResult]
     * @param scenario the identifier of the scenario of clicks to be used for detection.
     */
    fun loadSmartScenario(context: Context, resultCode: Int, data: Intent, scenario: Scenario): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val foregroundPermission = PermissionChecker.checkSelfPermission(context, Manifest.permission.FOREGROUND_SERVICE)
            if (foregroundPermission != PermissionChecker.PERMISSION_GRANTED) return false
        }

        serviceConnection.getLocalService()?.startSmartScenario(resultCode, data, scenario)
        return true
    }

    fun loadDumbScenario(context: Context, scenario: DumbScenario): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val foregroundPermission = PermissionChecker.checkSelfPermission(context, Manifest.permission.FOREGROUND_SERVICE)
            if (foregroundPermission != PermissionChecker.PERMISSION_GRANTED) return false
        }

        serviceConnection.getLocalService()?.startDumbScenario(scenario)
        return true
    }

    /** Stop the overlay UI and release all associated resources. */
    fun stopScenario() {
        serviceConnection.getLocalService()?.stopScenario()
    }
}

private const val DEFAULT_SCENARIO_PREFERENCES = "scenario_defaults"
private const val DEFAULT_SCENARIO_CREATED = "default_template_created"
private const val DEFAULT_SCENARIO_ASSET = "default_template.zip"

