// :core:domain —— 领域层（纯 Kotlin）：Collector / Repository 接口、UseCase、SamplingPolicy（P2 落地）
// 硬约束：本模块源码不得出现 android.* import；由架构单测守住。
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.shiji.pro.core.domain"
    compileSdk = 37
    defaultConfig { minSdk = 23 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    api(project(":core:model"))
    api(project(":core:common"))
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}
