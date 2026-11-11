import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    // the in-memory stores the tests of core, sync and shared all run the rules against
    `java-test-fixtures`
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
    api(libs.kotlinx.coroutines.core)
    implementation("org.dmfs:lib-recur:0.17.1")
    testImplementation(libs.junit)
}
