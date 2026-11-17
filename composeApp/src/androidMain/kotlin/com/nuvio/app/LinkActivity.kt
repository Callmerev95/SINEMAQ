package com.nuvio.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle

class LinkActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.dataString?.let { link ->
            // Ponytail: langsung ke MainActivity agar callback OAuth (Trakt/Simkl) tak
            // hilang lewat launcher alias + SplashActivity yang membuang EXTRA_LINK.
            // CLEAR_TOP|SINGLE_TOP membuat app yang sudah jalan tetap di tempat
            // (onNewIntent), bukan cold-start.
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(link)).apply {
                    setClass(this@LinkActivity, MainActivity::class.java)
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP,
                    )
                },
            )
        }
        finish()
    }

    companion object {
        const val EXTRA_LINK = "link"
    }
}
