package dev.hamo.AnimeDubsLibrary

import android.app.Application
import com.animedubs.AnimeDubs
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize the AnimeDubs library
        AnimeDubs.init(this)
        
        // Enable debug logging for demonstration purposes
        AnimeDubs.isDebugLoggingEnabled = true
    }
}
