package com.shiji.pro.data.di

import com.shiji.pro.core.domain.AppStateRepository
import com.shiji.pro.core.domain.CapabilityGate
import com.shiji.pro.core.domain.DriveTestRepository
import com.shiji.pro.core.domain.LogRepository
import com.shiji.pro.core.domain.NmeaRepository
import com.shiji.pro.core.domain.SpeedTestConfigRepository
import com.shiji.pro.core.domain.SpeedTestRepository
import com.shiji.pro.data.collector.CapabilityGateImpl
import com.shiji.pro.data.collector.cellular.CellularCollector
import com.shiji.pro.data.collector.device.DeviceCollector
import com.shiji.pro.data.collector.gnss.GnssCollector
import com.shiji.pro.data.collector.radio.BluetoothCollector
import com.shiji.pro.data.collector.radio.TrafficCollector
import com.shiji.pro.data.collector.speed.SpeedTestCollector
import com.shiji.pro.data.collector.wifi.WifiCollector
import com.shiji.pro.data.local.InMemoryAppStateRepository
import com.shiji.pro.data.local.InMemoryDriveTestRepository
import com.shiji.pro.data.local.InMemoryLogRepository
import com.shiji.pro.data.local.InMemoryNmeaRepository
import com.shiji.pro.data.local.InMemorySpeedTestConfigRepository
import com.shiji.pro.data.local.InMemorySpeedTestRepository
import org.koin.dsl.module

/**
 * :data 的装配模块（Koin）。单一装配点：能力闸门 / 8 个采集器 / 5 个仓储实现。
 * P0 的 ShiJiApplication 里 `modules(emptyList())` 将改为 `modules(dataModule)`。
 */
val dataModule = module {
    single { CapabilityGateImpl(get()) }
    single<CapabilityGate> { get<CapabilityGateImpl>() }

    single { CellularCollector(get(), get()) }
    single { WifiCollector(get(), get()) }
    single { GnssCollector(get(), get()) }
    single { DeviceCollector(get(), get()) }
    single { BluetoothCollector(get(), get()) }
    single { TrafficCollector(get(), get()) }
    single { SpeedTestCollector(get(), get()) }

    // 具体单例先声明，接口从同一实例暴露，保证 refresh() 的 publish() 与 observeState() 是同一对象
    single { InMemoryAppStateRepository() }
    single<AppStateRepository> { get<InMemoryAppStateRepository>() }
    single<LogRepository> { InMemoryLogRepository() }
    single<SpeedTestRepository> { InMemorySpeedTestRepository() }
    single<SpeedTestConfigRepository> { InMemorySpeedTestConfigRepository() }
    single<DriveTestRepository> { InMemoryDriveTestRepository() }
    single<NmeaRepository> { InMemoryNmeaRepository() }
}
