plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

val releaseKeystore = providers.environmentVariable("PLANNER_KEYSTORE").orNull
val releasePassword = providers.environmentVariable("PLANNER_KEYSTORE_PASSWORD").orNull

android {
    namespace = "dev.fourthbreakfast.planner"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "dev.fourthbreakfast.planner"
        minSdk = 26
        targetSdk = 37
        versionCode = 8
        versionName = "0.5.2"

        manifestPlaceholders["appLabel"] = "planner"

        // -Pdemo installs a second copy named planner dev with its own data, so a change can be tried on the phone
        // without touching the real app, which is signed with the release key and cannot be replaced by a debug build
        if (providers.gradleProperty("demo").isPresent) {
            applicationIdSuffix = ".demo"
            manifestPlaceholders["appLabel"] = "planner dev"
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // the key and its password never live in the repo: tools/release.ps1 passes them in
    if (releaseKeystore != null) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = releasePassword
                keyAlias = "planner"
                keyPassword = releasePassword
            }
        }
    }

    buildTypes {
        release {
            if (releaseKeystore != null) signingConfig = signingConfigs.getByName("release")
            optimization {
                enable = true
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}