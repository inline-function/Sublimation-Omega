// 根工程只统一仓库与发布坐标，不向子模块施加插件——各管线模块按 S-2 §1 自行声明所需插件。
allprojects {
    // 坐标与包根 sublimation.frontend 对齐（S-2 §2）；旧包名 sugared.functor 退役。
    group = "sublimation"
    version = "0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

// 动机：测试 workingDir 必须在各模块 tasks.test { } 内以 rootDir 设置，不可在根工程用
// subprojects/configureEach 惰性配置——该写法会被 Kotlin Gradle Plugin 重置，导致 workingDir
// 失效（旧工程同款教训）。S-2 §3.4 要求测试工作目录统一 rootDir，具体设置见 :frontend。
