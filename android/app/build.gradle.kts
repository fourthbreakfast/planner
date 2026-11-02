plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktlint)
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
        versionCode = 9
        versionName = "0.6.0"

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
    // the database upgrade tests read the saved schemas
    sourceSets.getByName("androidTest").assets.directories.add("$projectDir/schemas")
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
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.room.testing)
    debugImplementation(libs.androidx.compose.ui.tooling)
    // the upgrade tests read the saved schemas with a newer one than the app pulls in by itself
    debugImplementation(libs.kotlinx.serialization.core)
}

// these tests install and then uninstall the app, which must never be the real one
if (gradle.startParameter.taskNames.any { "connected" in it } && !providers.gradleProperty("demo").isPresent) {
    error("device tests only run on the dev copy: add -Pdemo")
}
