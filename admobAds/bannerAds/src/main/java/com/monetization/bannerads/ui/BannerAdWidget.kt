package com.monetization.bannerads.ui

import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewGroup
import com.facebook.shimmer.ShimmerFrameLayout
import com.monetization.bannerads.AdmobBannerAd
import com.monetization.bannerads.AdmobBannerAdsController
import com.monetization.bannerads.AdmobBannerAdsManager
import com.monetization.bannerads.BannerAdSize
import com.monetization.bannerads.BannerAdType
import com.monetization.core.ad_units.core.AdType
import com.monetization.core.commons.AdsCommons.logAds
import com.monetization.core.commons.NativeConstants.inflateLayoutByName
import com.monetization.core.commons.NativeConstants.makeGone
import com.monetization.core.commons.NativeConstants.makeVisible
import com.monetization.core.commons.NativeConstants.removeViewsFromIt
import com.monetization.core.controllers.AdsControllerBaseHelper
import com.monetization.core.listeners.UiAdsListener
import com.monetization.core.managers.AdsLoadingStatusListener
import com.monetization.core.models.RefreshAdInfo
import com.monetization.core.ui.ShimmerInfo
import com.monetization.core.ui.widgetBase.BaseAdsWidget

class BannerAdWidget @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : BaseAdsWidget<AdmobBannerAdsController>(context, attrs, defStyleAttr) {

    private var bannerRefreshed = false

    fun showBannerAdmob(
        activity: Activity,
        adKey: String,
        enabled: Boolean,
        shimmerInfo: ShimmerInfo = ShimmerInfo.GivenLayout(),
        oneTimeUse: Boolean = true,
        requestNewOnShow: Boolean = true,
        listener: UiAdsListener?
    ) {
        onShowAdCalled(
            adKey = adKey,
            activity = activity,
            oneTimeUse = oneTimeUse,
            requestNewOnShow = requestNewOnShow,
            enabled = enabled,
            shimmerInfo = shimmerInfo,
            adsManager = AdmobBannerAdsManager,
            adType = AdType.BANNER,
            listener = listener
        )
        logAds("showBannerAd called($key),enable=$enabled,")
    }

    override fun loadAd() {
        (getController() as? AdmobBannerAdsController)?.loadAd(
            activity = activity!!,
            calledFrom = "Base Banner Activity",
            placementKey = placementKey,
            callback = object : AdsLoadingStatusListener {
                override fun onAdRequested(adKey: String) {
                    uiListener?.onAdRequested(adKey)
                }

                override fun onAdLoaded(adKey: String, mediationClassName: String?) {
                    if (adLoaded) {
                        bannerRefreshed = true
                    }
                    adOnLoaded()
                    uiListener?.onAdLoaded(adKey, mediationClassName)
                }

                override fun onImpression(adKey: String) {
                    uiListener?.onImpression(adKey)
                }

                override fun onClicked(adKey: String) {
                    uiListener?.onAdClicked(adKey)
                }

                override fun onAdFailedToLoad(
                    adKey: String,
                    message: String,
                    code: Int,
                    mediationClassName: String?,
                    adapterResponses: List<AdsControllerBaseHelper.AdapterResponses>?
                ) {
                    adOnFailed()
                    uiListener?.onAdFailed(
                        key = adKey, msg = message, code = code,
                        mediationClassName = mediationClassName
                    )
                }
            }
        )
    }


    override fun populateAd() {
        adUnit?.let {
            (it as? AdmobBannerAd)?.populateAd(activity!!, this, onPopulated = {
                if (oneTimeUse) {
                    getController()?.destroyAd(activity!!)
                    if (bannerRefreshed) {
                        return@populateAd
                    }
                    if (requestNewOnShow) {
                        (getController() as AdmobBannerAdsController).loadAd(
                            activity = activity!!,
                            calledFrom = "",
                            callback = null,
                            placementKey = placementKey
                        )
                    }
                }
                refreshLayout()
            })
        }
    }

    private fun destroyAndHideAd(activity: Activity) {
        makeVisible()
        removeAllViews()
        makeGone()
        (adUnit as? AdmobBannerAd)?.destroyAd(activity)
        adUnit = null
        adPopulated = false
        isLoadAdCalled = false
        isShowAdCalled = false
        adLoaded = false
    }


    override fun showShimmerLayout(shimmer: ShimmerInfo?, activity: Activity) {
        try {
            val info = shimmer ?: shimmerInfo
            val shimmerLayout = LayoutInflater.from(activity)
                .inflate(com.monetization.core.R.layout.shimmer, null, false)
                ?.findViewById<ShimmerFrameLayout>(com.monetization.core.R.id.shimmerRoot)
            val shimmerView = when (info) {
                is ShimmerInfo.GivenLayout -> {
                    val bannerAdType = AdmobBannerAdsManager.getAdController(key)?.getBannerAdType()
                    val layoutForShimmer = when (bannerAdType) {
                        is BannerAdType.Collapsible -> {
                            "adapter_banner_shimmer"
                        }

                        is BannerAdType.Normal -> {
                            when ((bannerAdType as BannerAdType.Normal).size) {
                                BannerAdSize.AdaptiveBanner -> "adapter_banner_shimmer"
                                BannerAdSize.MediumRectangle -> "rectangular_banner_shimmer"
                                BannerAdSize.Banner -> "adapter_banner_shimmer"
                                BannerAdSize.LargeBanner -> "large_banner_shimmer"
                            }
                        }

                        null -> {
                            "adapter_banner_shimmer"
                        }
                    }
                    val adLayout = layoutForShimmer.inflateLayoutByName(activity!!)
                    shimmerLayout?.removeViewsFromIt()
                    shimmerLayout?.addView(adLayout)
                    shimmerLayout
                }

                is ShimmerInfo.ShimmerByView -> {
                    if (info.addInAShimmerView) {
                        info.layoutView?.let { view ->
                            try {
                                (view.parent as? ViewGroup)?.removeView(view)
                                shimmerLayout?.removeViewsFromIt()
                                shimmerLayout?.addView(view)
                                shimmerLayout
                            } catch (_: Exception) {
                                null
                            }
                        } ?: run { null }
                    } else {
                        info.layoutView
                    }
                }

                ShimmerInfo.None -> {
                    null
                }
            }
            removeAllViews()
            if (shimmerView != null) {
                try {
                    addView(shimmerView)
                } catch (_: Exception) {

                }
            }
        } catch (e: Exception) {
            logAds("showShimmerLayout Banner=${key} Exception=${e.stackTrace}", true)
        }
    }

    fun refreshAd(refreshAdInfo: RefreshAdInfo) {
        if (adPopulated || (refreshAdInfo.requestNewIfAlreadyFailed && isAdFailedToLoad)) {
            isAdFailedToLoad = false
            adPopulated = false
            isLoadAdCalled = false
            activity?.let {
                onShowAdCalled(
                    adKey = key,
                    activity = it,
                    oneTimeUse = oneTimeUse,
                    requestNewOnShow = requestNewOnShow,
                    enabled = isAdEnabled,
                    shimmerInfo = shimmerInfo,
                    adsManager = AdmobBannerAdsManager,
                    adType = AdType.BANNER,
                    listener = null,
                    isForRefresh = true,
                    refreshAdInfo = refreshAdInfo
                )
            }
        }
    }

    fun setWidgetKey(key: String) {
        placementKey = key
    }
}