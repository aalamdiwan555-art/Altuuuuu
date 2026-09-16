package com.buzbuz.smartautoclicker.feature.revenue.data.ads

import android.app.Activity
import android.content.Context
import com.buzbuz.smartautoclicker.core.base.di.Dispatcher
import com.buzbuz.smartautoclicker.core.base.di.HiltCoroutineDispatchers.Main
import com.buzbuz.smartautoclicker.feature.revenue.data.ads.sdk.IAdsSdk
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class RewardedAdsDataSource @Inject constructor(
    @ApplicationContext private val appContext: Context,
    @Dispatcher(Main) mainDispatcher: CoroutineDispatcher,
    private val adsSdk: IAdsSdk,
) {
    private val scope = CoroutineScope(SupervisorJob() + mainDispatcher)
    private var isLoaded = false
    private var isLoading = false
    private var pendingActivity: Activity? = null
    private var pendingRewarded: (() -> Unit)? = null
    private var pendingUnavailable: (() -> Unit)? = null

    fun load(context: Context) {
        if (isLoaded || isLoading) return
        isLoading = true
        adsSdk.loadRewardedAd(
            context = context,
            onLoaded = {
                isLoading = false
                isLoaded = true
                pendingActivity?.let { activity ->
                    val rewarded = pendingRewarded ?: return@let
                    val unavailable = pendingUnavailable ?: {}
                    pendingActivity = null
                    pendingRewarded = null
                    pendingUnavailable = null
                    showLoaded(activity, rewarded, unavailable)
                }
            },
            onError = { _, _ ->
                isLoading = false
                isLoaded = false
            },
        )
    }

    fun show(
        activity: Activity,
        onRewarded: () -> Unit,
        onUnavailable: () -> Unit,
    ) {
        if (!isLoaded) {
            pendingActivity = activity
            pendingRewarded = onRewarded
            pendingUnavailable = onUnavailable
            load(activity)
            return
        }

        showLoaded(activity, onRewarded, onUnavailable)
    }

    private fun showLoaded(
        activity: Activity,
        onRewarded: () -> Unit,
        onUnavailable: () -> Unit,
    ) {
        isLoaded = false
        adsSdk.showRewardedAd(
            activity = activity,
            onRewarded = { onRewarded() },
            onDismiss = { load(appContext) },
            onError = { _, _ ->
                onUnavailable()
                load(appContext)
            },
        )
    }
}