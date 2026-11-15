package io.github.justarandombaddev.seerrfintv

import android.app.Application

class SeerrfinApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
