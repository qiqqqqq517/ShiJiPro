package com.shiji.pro

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import com.shiji.pro.di.appModule

/**
 * 装配层 Application：启动 Koin 并注册 [appModule]（数据模块 + ViewModel）。
 * 不在此处做任何采集或 IO。
 */
class ShiJiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@ShiJiApplication)
            modules(appModule)
        }
    }
}
