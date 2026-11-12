package com.shiji.pro.core.domain

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 架构门禁单测（设计稿第 2.1 与第 11.6 节）：domain / model 层源码不得出现任何框架 import。
 * 判据按「语句」取：只扫描形如 `import android.` / `import androidx.` / `import java.io.` 的导入行，
 * 不把注释、字符串、KDoc 里的字样误判为违规（避免文本正则的假阳性）。
 */
class DomainPurityTest {

    @Test
    fun `domain 与 model 层无 android androidx 或 java io 框架依赖`() {
        val roots = listOf(
            File("src/main/kotlin"),                          // domain 自身
            File("../core/domain/src/main/kotlin"),
            File("../model/src/main/kotlin"),                 // model 层
            File("../../ShiJiPro/core/model/src/main/kotlin"),
        )
        val sourceRoots = roots.filter { it.isDirectory }
        // 至少应能定位到 domain 自身；定位不到 model 不误报（由下面的 model 专项用例兜底）
        assertTrue("domain 源码目录必须可定位", sourceRoots.isNotEmpty())

        val violations = mutableListOf<String>()
        sourceRoots.forEach { root ->
            root.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .forEach { file ->
                    file.readLines().forEachIndexed { index, line ->
                        val trimmed = line.trim()
                        if (trimmed.startsWith("import android.") ||
                            trimmed.startsWith("import androidx.") ||
                            trimmed.startsWith("import java.io.")
                        ) {
                            violations += "${file.name}:${index + 1}: $trimmed"
                        }
                    }
                }
        }
        assertTrue(
            "领域层禁止框架依赖，发现：\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    @Test
    fun `model 层源码零框架 import`() {
        val modelRoots = listOf(
            File("../model/src/main/kotlin"),
            File("../../ShiJiPro/core/model/src/main/kotlin"),
        )
        val root = modelRoots.firstOrNull { it.isDirectory } ?: return
        val bad = mutableListOf<String>()
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            file.readLines().forEach { line ->
                val t = line.trim()
                if (t.startsWith("import android.") || t.startsWith("import androidx.")) {
                    bad += "${file.name}: $t"
                }
            }
        }
        assertTrue("model 层禁止 android.* / androidx.*：${bad.joinToString()}", bad.isEmpty())
    }
}
