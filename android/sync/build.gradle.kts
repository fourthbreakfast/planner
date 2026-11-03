import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

dependencies {
    api(project(":core"))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}

// the tests that run two real Syncthings need one built for this computer: tools/build-syncthing.sh makes it
tasks.test {
    for (name in listOf("PLANNER_SYNCTHING", "PLANNER_PEER_ID", "PLANNER_PEER_ADDRESS")) {
        providers.environmentVariable(name).orNull?.let { environment(name, it) }
    }
}
