// 构建约定统一于 S-2 §3.4：Kotlin 2.1.20、JVM 17 toolchain、JUnit Platform、测试 workingDir=rootDir。
// 模块包含清单遵循 S-2 §3.5：本期仅 :frontend；待 :backend-js / :cli 启动时追加，
// 且必须先修订 S-2 留痕（禁止悄悄 include）。:lowering 永不建立（D-8）。

plugins {
    // JVM 17 toolchain 自动供给：构建机缺失目标 JDK 时按规范版本自动下载，免去手工配 JDK。
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "sublimation"

include(":frontend")
