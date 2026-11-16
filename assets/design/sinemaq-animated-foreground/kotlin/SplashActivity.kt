package com.example.sinemaq   // <- ganti dengan package kamu

import android.content.Intent
import android.graphics.drawable.Animatable2
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()                 // WAJIB sebelum super.onCreate()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val anim = findViewById<ImageView>(R.id.logo).drawable as Animatable2
        anim.registerAnimationCallback(object : Animatable2.AnimationCallback() {
            override fun onAnimationEnd(drawable: Drawable?) {
                // tahan sebentar (400ms) supaya logo terbaca, lalu masuk ke app
                Handler(Looper.getMainLooper()).postDelayed({
                    startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                    finish()
                    @Suppress("DEPRECATION")
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                }, 400)
            }
        })
        anim.start()
    }
}

// build.gradle (module): implementation("androidx.core:core-splashscreen:1.0.1")
