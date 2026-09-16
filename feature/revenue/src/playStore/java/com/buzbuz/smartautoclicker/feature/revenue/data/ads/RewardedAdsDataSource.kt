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

    fun load(context: Context) {
        if (isLoaded || isLoading) return
        isLoading = true
        adsSdk.loadRewardedAd(
            context = context,
            onLoaded = {
                isLoading = false
                isLoaded = true
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
            onUnavailable()
            load(appContext)
            return
        }

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