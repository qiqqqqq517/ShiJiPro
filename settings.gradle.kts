pluginManagement {
    val useMirror: Boolean = (providers.gradleProperty("useMirror").orNull ?: "true").toBoolean()
    repositories {
        if (useMirror) {
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/central")
            maven("https://maven.aliyun.com/repository/gradle-plugin")
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    val useMirror: Boolean = (providers.gradleProperty("useMirror").orNull ?: "true").toBoolean()
    repositories {
        if (useMirror) {
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/central")
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "ShiJiPro"

include(
    ":app",
    ":core:model",
    ":core:common",
    ":core:domain",
    ":core:ui",
    ":data",
    ":features"
)
