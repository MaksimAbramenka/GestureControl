package com.gesturecontrol.app

import android.app.Application
import timber.log.Timber

class GestureControlApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
