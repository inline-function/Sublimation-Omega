// 前端模块构建：本模块承担词法单元流 / AST / HIR / JSON 产物（S-2 §1）。
// 构建约定对齐 S-2 §3.4 与旧工程点查（frontend/build.gradle.kts）：Kotlin 2.1.20、
// JVM 17 toolchain、JUnit Platform、测试 workingDir=rootDir。

// application 插件提供最小 CLI 调用入口（S-2 §2）：gradlew :frontend:run --args="..."；
// 不引入任何第三方运行时依赖（S-2 §3.3）。
plugins {
    kotlin("jvm") version "2.1.20"
    application
}

application {
    mainClass = "sublimation.frontend.cli.MainKt"
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // 运行时零第三方依赖（S-2 §3.3）；kotlin("test") 仅进入测试编译类路径。
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    // 见根工程注释：workingDir 必须在模块内设置，根级惰性配置会被 KGP 重置。
    workingDir = rootDir
}
