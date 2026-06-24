package com.inkvault

import android.app.Application
import com.inkvault.di.ServiceLocator

class InkVaultApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Crash recovery + auto-reconnect to the last pen.
        ServiceLocator.from(this).onStartup()
    }
}
