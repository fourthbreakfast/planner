plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ktlint)
}

val releaseKeystore = providers.environmentVariable("PLANNER_KEYSTORE").orNull
val releasePassword = providers.environmentVariable("PLANNER_KEYSTORE_PASSWORD").orNull

// the app's own Syncthing, built from the pinned source. Android only runs a program it unpacked itself, as a library
val syncthingSource = rootProject.layout.projectDirectory.dir("../third_party/syncthing")
val syncthingAndroid = tasks.register<SyncthingBinary>("syncthingAndroid") {
    source.set(syncthingSource)
    version.set(
        providers.exec { commandLine("git", "-C", syncthingSource.asFile.path, "describe", "--tags", "--always") }
            .standardOutput.asText.map { it.trim() },
    )
    goos.set("android")
    goarch.set("arm64")
    fileName.set("arm64-v8a/libsyncthing.so")
    output.set(layout.buildDirectory.dir("syncthing/jniLibs"))
}

androidComponents {
    onVariants { variant -> variant.sources.jniLibs?.addGeneratedSourceDirectory(syncthingAndroid, SyncthingBinary::output) }
}

android {
    namespace = "dev.fourthbreakfast.planner"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "dev.fourthbreakfast.planner"
        minSdk = 26
        targetSdk = 37
        versionCode = providers.gradleProperty("plannerVersionCode").get().toInt()
        versionName = providers.gradleProperty("plannerVersion").get()

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
    // unpacked on install, so Syncthing can be started from where Android put it
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
    // the database upgrade tests read the saved schemas
    sourceSets.getByName("androidTest").assets.directories.add(rootProject.file("shared/schemas").path)
}

dependencies {
    implementation(project(":shared"))
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
    implementation(libs.androidx.work.runtime)
    implementation(libs.zxing.core)
    implementation(libs.zxing.android.embedded)
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
