package com.topoarg.app

import android.app.Application
import com.topoarg.app.data.PointRepository
import com.topoarg.app.geoid.GeoidModel
import com.topoarg.app.settings.Prefs

class TopoArgApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        PointRepository.init(this)
        GeoidModel.init(this)
    }
}
