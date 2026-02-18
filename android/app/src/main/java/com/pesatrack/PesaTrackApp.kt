package com.pesatrack

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * PesaTrack Application class
 * 
 * Initializes Hilt for dependency injection
 */
@HiltAndroidApp
class PesaTrackApp : Application() {
    
    override fun onCreate() {
        super.onCreate()
        // Initialize any app-wide components here
    }
}
