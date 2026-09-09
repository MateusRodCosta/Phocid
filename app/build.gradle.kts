import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.dependency.analysis)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.aboutlibraries.android)
    id("com.ncorti.ktfmt.gradle") version "0.27.0"
}

val keystorePropertiesFile = rootProject.file("key.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
}

android {
    namespace = "com.mateusrodcosta.apps.lontramusic"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.mateusrodcosta.apps.lontramusic"
        minSdk = 30
        //noinspection OldTargetApi
        targetSdk = 36
        versionCode = 20260903
        versionName = "20260903"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        getByName("debug") {
            // Enable v3 signing only, also on debug builds
            enableV1Signing = false
            enableV2Signing = false
            enableV3Signing = true
        }

        if (keystorePropertiesFile.exists()) {
            create("release") {
                if (keystorePropertiesFile.exists()) {
                    keyAlias = keystoreProperties["keyAlias"] as String
                    keyPassword = keystoreProperties["keyPassword"] as String
                    storeFile = file(keystoreProperties["storeFile"] as String)
                    storePassword = keystoreProperties["storePassword"] as String
                }

                // Enable v3 signing only, which will be used on modern Android (9+)
                enableV1Signing = false
                enableV2Signing = false
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            optimization {
                enable = true
            }

            signingConfig = if (keystorePropertiesFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }

        debug {
            isPseudoLocalesEnabled = true

            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    dependenciesInfo {
        // Disables dependency metadata when building APKs.
        includeInApk = false
        // Disables dependency metadata when building Android App Bundles.
        includeInBundle = false
    }
    androidResources{
        @Suppress("UnstableApiUsage")
        generateLocaleConfig = true
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        languageVersion = KotlinVersion.KOTLIN_2_4
        jvmTarget = JvmTarget.JVM_21
    }
}

dependencies {
    implementation(libs.accompanist.permissions)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.palette.ktx)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    // TODO: Maybe requiring a dependency just for `FilenameUtils` is too much
    implementation(libs.commons.io)
    implementation(libs.core.splashscreen)
    // ICU can't be mocked by Robolectric, WTF
    implementation(libs.icu4j)
    implementation(libs.kotlinx.serialization.cbor)
    implementation(libs.kotlinx.serialization.json)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.jaudiotagger)
    implementation(libs.reorderable)
    implementation(project(":OpusMetadataIo"))

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.assertj.core)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation(libs.aboutlibraries.core)
    implementation(libs.aboutlibraries.compose.core)
    implementation(libs.aboutlibraries.compose.m3)

    implementation(libs.coil)
}

ktfmt { kotlinLangStyle() }

aboutLibraries {
    collect {
        // Define the path configuration files are located in. E.g. additional libraries, licenses to add to the target .json
        configPath = file("../config")
    }
}
