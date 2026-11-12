// :core:common —— 通用工具层（纯 Kotlin）：频段/频率/信道换算、CSV、脱敏、采样、安全 Result（P1 落地）
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.shiji.pro.core.common"
    compileSdk = 37
    defaultConfig { minSdk = 23 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    api(project(":core:model"))
    testImplementation(libs.junit)
}
