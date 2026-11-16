package com.nuvio.app

import android.content.Intent
import android.graphics.drawable.Animatable2
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

open class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Resume dari recents: jangan ulang animasi, langsung ke app.
        if (savedInstanceState != null) {
            launchMain()
            return
        }

        setContentView(R.layout.activity_splash)
        val drawable = findViewById<ImageView>(R.id.logo).drawable as? Animatable2
        if (drawable == null) {
            launchMain()
            return
        }
        drawable.registerAnimationCallback(object : Animatable2.AnimationCallback() {
            override fun onAnimationEnd(drawable: Drawable?) {
                // ponytail: hold 400ms agar wordmark terbaca; turunkan ke 0-200ms bila terasa lama.
                Handler(Looper.getMainLooper()).postDelayed({ launchMain() }, 400)
            }
        })
        drawable.start()
    }

    private fun launchMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}
