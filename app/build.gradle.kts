import org.jetbrains.kotlin.compose.compiler.gradle.ComposeFeatureFlag

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    val appId = project.group.toString()

    namespace = appId
    compileSdk = 35

    defaultConfig {
        applicationId = appId

        minSdk = 26
        targetSdk = 35

        versionCode = 1
        versionName = project.version.toString()

        multiDexEnabled = true
    }

    splits {
        abi {
            reset()
            isUniversalApk = true
        }
    }

    buildTypes {
        val name = "Auto pickup"

        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-DEBUG"
            manifestPlaceholders["appName"] = "$name (DEBUG)"
            buildConfigField("String", "APP_NAME", "\"$name (D)\"")
        }

        release {
            versionNameSuffix = "-RELEASE"

            isMinifyEnabled = true
            isShrinkResources = true

            manifestPlaceholders["appName"] = name
            buildConfigField("String", "APP_NAME", "\"$name\"")

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        freeCompilerArgs = freeCompilerArgs + listOf("-Xcontext-receivers")
    }

    packaging {
        resources.excludes.add("META-INF/**/*")
    }

    androidResources {
        @Suppress("UnstableApiUsage")
        generateLocaleConfig = true
    }
}

kotlin {
    jvmToolchain(libs.versions.jvm.get().toInt())
}

composeCompiler {
    featureFlags.addAll(ComposeFeatureFlag.OptimizeNonSkippingGroups)
}

dependencies {
    coreLibraryDesugaring(libs.desugaring)

    implementation(libs.activity)
    implementation(libs.core.ktx)

    implementation(libs.immutable)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)

    implementation(libs.compose.activity)
    implementation(libs.compose.material3)
}
