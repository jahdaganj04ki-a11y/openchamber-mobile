package com.openchamber.android

import android.app.Application
import com.openchamber.android.service.RuntimeForegroundService

class OpenChamberApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        RuntimeForegroundService.start(this)
    }
}
