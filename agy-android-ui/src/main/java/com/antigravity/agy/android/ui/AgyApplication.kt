package com.antigravity.agy.android.ui

import android.app.Application
import com.antigravity.agy.android.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class AgyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@AgyApplication)
            modules(appModule)
        }
    }
}
