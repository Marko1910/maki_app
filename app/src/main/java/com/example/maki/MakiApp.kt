package com.example.maki

import android.app.Application
import com.example.maki.data.MakiPrefs

/**
 * Process-wide setup.
 *
 * WorkManager starts the app without any screen — Android had killed the process and
 * a booked shift came due — and [MakiPrefs] used to be initialised in MainActivity, so
 * in that process it silently answered "nothing stored": no session to restore, and a
 * worker that reported success having written nothing. Application.onCreate runs on
 * every entry into the process, which is the only place this belongs.
 */
class MakiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        MakiPrefs.init(this)
    }
}
