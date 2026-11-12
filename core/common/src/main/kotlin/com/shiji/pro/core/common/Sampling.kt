package com.shiji.pro.core.common

/**
 * 曲线采样与降频（设计稿 §10.6：曲线图表做采样降频，避免无限增长与主线程抖动）。
 * 纯 Kotlin、无 Android 依赖，可在 JVM 单测直接验证。
 */
object Sampling {

    /** 时间窗口（设计稿 §2.2 信号趋势可切换 20s / 1min / 5min）。 */
    enum class Window(val seconds: Long) {
        SEC_20(20L), MIN_1(60L), MIN_5(300L)
    }

    data class Point(val timestampMs: Long, val value: Double)

    /** 按时间窗口裁剪：仅保留 window 内的点；返回新列表，不改原序列。 */
    fun withinWindow(points: List<Point>, nowMs: Long, window: Window): List<Point> {
        val from = nowMs - window.seconds * 1000L
        return points.filter { it.timestampMs >= from }
    }

    /**
     * 降频：把长序列等距抽样到最多 [maxPoints] 个点。
     * 首尾点强制保留（保证趋势端点不错位）；点不足时原样返回。
     */
    fun downsample(points: List<Point>, maxPoints: Int): List<Point> {
        require(maxPoints >= 2) { "maxPoints 至少为 2" }
        if (points.size <= maxPoints) return points
        val step = (points.size - 1).toDouble() / (maxPoints - 1)
        return (0 until maxPoints).map { i ->
            points[(i * step).toInt().coerceAtMost(points.size - 1)]
        }
    }

    /** 有界环形缓冲：容量满后覆盖最旧点，保证内存上限固定。 */
    class RingBuffer(private val capacity: Int) {
        private val items = ArrayDeque<Point>()

        fun add(point: Point) {
            if (items.size >= capacity) items.removeFirst()
            items.addLast(point)
        }

        fun snapshot(): List<Point> = items.toList()

        fun clear() = items.clear()

        val size: Int get() = items.size
    }
}

/** 基础统计量（测速 Jitter、信号均值等复用）。 */
object Stats {
    fun mean(values: List<Double>): Double? =
        if (values.isEmpty()) null else values.sum() / values.size

    /** RFC 3550 jitter：相邻样本差的绝对值的平均。 */
    fun jitter(rttMs: List<Double>): Double? {
        if (rttMs.size < 2) return null
        var sum = 0.0
        for (i in 1 until rttMs.size) sum += kotlin.math.abs(rttMs[i] - rttMs[i - 1])
        return sum / (rttMs.size - 1)
    }

    fun minMax(values: List<Double>): Pair<Double, Double>? =
        if (values.isEmpty()) null else values.min() to values.max()
}
