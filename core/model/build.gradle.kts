// :core:model —— 领域模型层（纯 Kotlin，约定禁止出现 android.* import）
// 依赖方向：无。单一真源：FieldValue/Origin、制式与星座枚举、频段常量表（P1 落地）。
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.shiji.pro.core.model"
    compileSdk = 37
    defaultConfig { minSdk = 23 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // 刻意不依赖任何 Android / 第三方库，保持领域模型可移植。
    testImplementation(libs.junit)
}
