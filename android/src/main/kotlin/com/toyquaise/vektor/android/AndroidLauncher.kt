package com.toyquaise.vektor.android

import android.os.Bundle
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.toyquaise.vektor.VektorGame

class AndroidLauncher : AndroidApplication() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ads = AdMobAds(this)
        val config = AndroidApplicationConfiguration().apply {
            useImmersiveMode = true
            depth = 16
            numSamples = 2
        }
        initialize(VektorGame(ads = ads), config)
        ads.start()
    }
}
