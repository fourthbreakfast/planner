plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
    alias(libs.plugins.ktlint)
}

// everything the phone and the computer share: the screens, the look, the words and the database
kotlin {
    android {
        namespace = "dev.fourthbreakfast.planner.shared"
        compileSdk = 37
        minSdk = 26
        androidResources.enable = true
    }
    jvm("desktop") {
        // the windows app carries a java 21 runtime
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":sync"))
            api(libs.jetbrains.compose.runtime)
            api(libs.jetbrains.compose.foundation)
            api(libs.jetbrains.compose.material3)
            api(libs.jetbrains.compose.ui)
            api(libs.jetbrains.compose.resources)
            api(libs.jetbrains.lifecycle.viewmodel.compose)
            api(libs.jetbrains.lifecycle.runtime.compose)
            api(libs.androidx.room.runtime)
            implementation(libs.zxing.core)
            implementation(libs.kotlinx.serialization.json)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
        }
        getByName("desktopMain").dependencies {
            implementation(compose.desktop.common)
            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.kotlinx.coroutines.swing)
        }
        getByName("desktopTest").dependencies {
            implementation(libs.junit)
            implementation(compose.desktop.currentOs)
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "dev.fourthbreakfast.planner.resources"
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspDesktop", libs.androidx.room.compiler)
    add("desktopTestImplementation", testFixtures(project(":core")))
}

// the drawing tests write their pictures where PLANNER_RENDER says
tasks.withType<Test>().configureEach {
    providers.environmentVariable("PLANNER_RENDER").orNull?.let { environment("PLANNER_RENDER", it) }
}
