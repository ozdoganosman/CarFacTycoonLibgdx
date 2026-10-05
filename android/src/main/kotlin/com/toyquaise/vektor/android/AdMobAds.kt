package com.toyquaise.vektor.android

import android.app.Activity
import com.badlogic.gdx.Gdx
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.toyquaise.vektor.Ads
import com.toyquaise.vektor.BuildConfig
import java.util.concurrent.atomic.AtomicBoolean

/**
 * AdMob for the game: a full-screen ad now and then between levels, and a rewarded video for a
 * hint. Consent is asked first where the law needs it (Google's User Messaging Platform); no ad
 * is requested before it allows. Ads are rated for a general audience with parental guidance at
 * most, as the game is played at school. Everything runs on the UI thread; the game's callbacks
 * are handed back to the render thread.
 */
class AdMobAds(private val activity: Activity) : Ads {
    private val consent: ConsentInformation = UserMessagingPlatform.getConsentInformation(activity)
    private val started = AtomicBoolean(false)
    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null
    private var loadingInterstitial = false
    private var loadingRewarded = false

    /** Updates the consent state (showing the form if needed), then starts the ads. Call once from onCreate. */
    fun start() {
        val params = ConsentRequestParameters.Builder().build()
        consent.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { startAds() }
            },
            { startAds() },
        )
        // Consent given in an earlier session already allows ads while the update runs.
        startAds()
    }

    private fun startAds() {
        if (!consent.canRequestAds() || !started.compareAndSet(false, true)) return
        MobileAds.setRequestConfiguration(
            RequestConfiguration.Builder()
                .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_PG)
                .build(),
        )
        Thread {
            MobileAds.initialize(activity) {
                activity.runOnUiThread {
                    loadInterstitial()
                    loadRewarded()
                }
            }
        }.start()
    }

    private fun request(): AdRequest = AdRequest.Builder().build()

    private fun loadInterstitial() {
        if (!started.get() || interstitialAd != null || loadingInterstitial) return
        loadingInterstitial = true
        InterstitialAd.load(activity, BuildConfig.ADMOB_INTERSTITIAL, request(), object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) {
                interstitialAd = ad
                loadingInterstitial = false
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                loadingInterstitial = false
            }
        })
    }

    private fun loadRewarded() {
        if (!started.get() || rewardedAd != null || loadingRewarded) return
        loadingRewarded = true
        RewardedAd.load(activity, BuildConfig.ADMOB_REWARDED, request(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) {
                rewardedAd = ad
                loadingRewarded = false
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                loadingRewarded = false
            }
        })
    }

    override fun interstitial(then: () -> Unit) {
        activity.runOnUiThread {
            val ad = interstitialAd
            if (ad == null) {
                Gdx.app.postRunnable(then)
                loadInterstitial()
                return@runOnUiThread
            }
            interstitialAd = null
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Gdx.app.postRunnable(then)
                    loadInterstitial()
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    Gdx.app.postRunnable(then)
                    loadInterstitial()
                }
            }
            ad.show(activity)
        }
    }

    override fun rewarded(rewarded: () -> Unit, unavailable: () -> Unit) {
        activity.runOnUiThread {
            val ad = rewardedAd
            if (ad == null) {
                Gdx.app.postRunnable(unavailable)
                loadRewarded()
                return@runOnUiThread
            }
            rewardedAd = null
            var earned = false
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                // The reward is handed over when the video closes, so the hint shows on the game.
                override fun onAdDismissedFullScreenContent() {
                    if (earned) Gdx.app.postRunnable(rewarded)
                    loadRewarded()
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    Gdx.app.postRunnable(unavailable)
                    loadRewarded()
                }
            }
            ad.show(activity) { earned = true }
        }
    }

    override val privacyOptionsRequired: Boolean
        get() = consent.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    override fun showPrivacyOptions() {
        activity.runOnUiThread {
            UserMessagingPlatform.showPrivacyOptionsForm(activity) { startAds() }
        }
    }
}
