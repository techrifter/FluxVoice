package com.techrifter.fluxvoice

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class FluxVoiceApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.d("FluxVoice", "Application started")
    }
}
