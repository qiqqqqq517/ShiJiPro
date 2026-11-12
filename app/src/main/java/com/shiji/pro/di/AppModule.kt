package com.shiji.pro.di

import com.shiji.pro.AppViewModel
import com.shiji.pro.data.di.dataModule
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** :app 装配模块：数据模块 + ViewModel。 */
val appModule = module {
    includes(dataModule)
    viewModel {
        AppViewModel(
            repo = get(),
            inMemory = get(),
            gate = get(),
            cellular = get(),
            wifi = get(),
            gnss = get(),
            device = get(),
            speed = get(),
            speedConfig = get(),
            traffic = get(),
        )
    }
}
