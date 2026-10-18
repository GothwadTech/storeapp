package com.gothwad.store

import android.app.Application
import com.gothwad.store.core.AppGraph
import com.gothwad.store.service.DownloadNotifications

class KiwiStoreApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.ensure(this)
        DownloadNotifications.ensureChannels(this)
    }
}
