package com.toyquaise.vektor

/**
 * Advertising, as the platform provides it (AdMob on Android, nothing on desktop). Callbacks run
 * on the render thread.
 */
interface Ads {
    /** Shows a full-screen ad if one is ready, then runs [then] (straight away if none is). */
    fun interstitial(then: () -> Unit)

    /**
     * Offers a rewarded video. [rewarded] runs if the player watched it to the end; [unavailable]
     * if no ad could be shown (offline, none loaded). Closing it early runs neither.
     */
    fun rewarded(rewarded: () -> Unit, unavailable: () -> Unit)

    /** Whether the consent rules ask for a way to change the privacy choices (a menu button). */
    val privacyOptionsRequired: Boolean

    fun showPrivacyOptions()
}

/** No ads: the desktop build, and screenshots. Rewards are given straight away. */
object NoAds : Ads {
    override fun interstitial(then: () -> Unit) = then()
    override fun rewarded(rewarded: () -> Unit, unavailable: () -> Unit) = rewarded()
    override val privacyOptionsRequired: Boolean get() = false
    override fun showPrivacyOptions() {}
}
