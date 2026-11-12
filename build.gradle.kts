// ShiJiPro · 顶层构建脚本
// 版本目录统一在 gradle/libs.versions.toml；各子模块通过 alias 引用。
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.compiler) apply false
}
