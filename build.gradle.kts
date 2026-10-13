// Top-level build file where you can add configuration options common to all sub-projects/modules.

// @Hint by SuperMonster003 on Aug 16, 2023.
//  ! Blocks "buildscript" and "plugins" have been moved to "settings.gradle.kts".
//  ! zh-CN: 代码块 "buildscript" 以及 "plugins" 已迁移至 "settings.gradle.kts".

allprojects {
    repositories {
        // ponytail: local JitPack mirror — Java HTTPS via proxy to jitpack.io often drops TLS; curl-filled cache
        maven { url = uri(rootDir.resolve(".m2-jitpack")) }
        // ponytail: Aliyun first — same TLS drop issue as settings.gradle.kts plugin classpath
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/central")
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/jcenter")
        mavenCentral()
        google()
        gradlePluginPortal()
        maven("https://jitpack.io")
    }
}

tasks {
    register<Delete>("clean").configure {
        // @Legacy delete(rootProject.buildDir)
        delete(rootProject.layout.buildDirectory)
    }
}
