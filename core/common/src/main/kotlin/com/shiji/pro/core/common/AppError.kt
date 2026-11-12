package com.shiji.pro.core.common

/**
 * 领域层安全错误类型（设计稿 §§2.1 / 10.4）。
 * 采集层把「权限缺失 / 机型不支持 / API 版本不足 / 系统拒绝」统一映射成本类型，
 * ViewModel 只据此生成降级提示，不抛异常、不崩溃。
 */
sealed interface AppError {
    val message: String

    data class PermissionDenied(val permission: String) : AppError {
        override val message: String get() = "权限未授予：$permission"
    }

    data class CapabilityUnsupported(val capability: String, val reason: String) : AppError {
        override val message: String get() = "$capability 不可用：$reason"
    }

    data class ApiLevelTooLow(val requiredApi: Int, val currentApi: Int) : AppError {
        override val message: String get() = "需要 API $requiredApi，当前 API $currentApi"
    }

    data class SystemRejected(val detail: String) : AppError {
        override val message: String get() = "系统拒绝：$detail"
    }
}

/** 轻量结果封装，避免把异常当控制流。 */
sealed interface AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>

    fun getOrNull(): T? = (this as? Success)?.value
}
