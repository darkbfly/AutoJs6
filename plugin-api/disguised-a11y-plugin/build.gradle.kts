plugins {
    id("org.autojs.build.versions")
    id("org.autojs.build.signs")
    id("org.autojs.build.jvm-convention")
    id("com.android.application")
    kotlin("plugin.parcelize")
}

android {
    namespace = "org.autojs.plugin.disguised.a11y"
    compileSdk = versions.sdkVersionCompile

    defaultConfig {
        applicationId = "org.autojs.plugin.disguised.a11y"
        minSdk = versions.sdkVersionMin
        targetSdk = versions.sdkVersionTarget
        versionCode = 1
        versionName = "1.0.0"
    }

    lint {
        abortOnError = false
    }

    signingConfigs {
        if (signs.isValid) {
            create("release") {
                storeFile = signs.properties["storeFile"]?.let { file(it as String) }
                keyPassword = signs.properties["keyPassword"] as String
                keyAlias = signs.properties["keyAlias"] as String
                storePassword = signs.properties["storePassword"] as String
            }
        }
    }

    buildTypes {
        val niceSigningConfig = takeIf { signs.isValid }?.let {
            signingConfigs.getByName("release")
        }
        debug {
            niceSigningConfig?.let { signingConfig = it }
        }
        release {
            isMinifyEnabled = false
            niceSigningConfig?.let { signingConfig = it }
        }
    }

    buildFeatures {
        aidl = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":plugin-api:disguised-a11y-api"))
    implementation(libs.core.ktx)
}
