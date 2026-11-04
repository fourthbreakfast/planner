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
    testImplementation(testFixtures(project(":core")))
    testImplementation(libs.junit)
}

// the tests that run real Syncthings need one built for this computer, named by PLANNER_SYNCTHING; without it they are
// skipped. ci builds one from third_party/syncthing
tasks.test {
    for (name in listOf("PLANNER_SYNCTHING", "PLANNER_PEER_ID", "PLANNER_PEER_ADDRESS")) {
        providers.environmentVariable(name).orNull?.let { environment(name, it) }
    }
}
