import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.ktlint)
}

// its own syncthing, built from the pinned source and shipped in the app's resource folder
val syncthingWindows =
    syncthingBinary("syncthingWindows", goos = "windows", goarch = "amd64", fileName = "windows/syncthing.exe", outputDir = "syncthing/resources")

// built for the java 21 runtime the installer carries
kotlin {
    jvmToolchain(21)
}

// the windows app: the shared screens in a window, with its own syncthing beside it
dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(libs.androidx.sqlite.bundled)
    implementation(libs.kotlinx.coroutines.swing)
    // tells Windows where the drawn title bar and edges are, so the window snaps as any other (NativeFrame)
    implementation(libs.jna)
    testImplementation(libs.junit)
}

compose.desktop {
    application {
        mainClass = "dev.fourthbreakfast.planner.desktop.MainKt"
        // packaging needs a jdk with jpackage, which the one inside android studio leaves out: gradle fetches temurin 21
        javaHome = javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(21))
            vendor.set(JvmVendorSpec.ADOPTIUM)
        }.get().metadata.installationPath.asFile.absolutePath
        nativeDistributions {
            appResourcesRootDir.set(syncthingWindows.flatMap { it.output })
            targetFormats(TargetFormat.Msi)
            modules("java.instrument", "java.prefs", "jdk.unsupported")
            packageName = "planner"
            packageVersion = providers.gradleProperty("plannerVersion").get()
            description = "tasks, agenda and notes"
            vendor = "fourthbreakfast"
            licenseFile.set(rootProject.file("../LICENSE"))
            windows {
                iconFile.set(project.file("icons/planner.ico"))
                // the same id for every version, so a new installer replaces the old app instead of adding a second
                upgradeUuid = "156c78d7-e497-45e4-892f-d6c9dea7e580"
                perUserInstall = true
                menu = true
                shortcut = true
            }
        }
    }
}

// the drawing test writes its pictures where PLANNER_RENDER says
tasks.test {
    providers.environmentVariable("PLANNER_RENDER").orNull?.let { environment("PLANNER_RENDER", it) }
    providers.environmentVariable("PLANNER_ICON").orNull?.let { environment("PLANNER_ICON", it) }
    for (name in listOf("PLANNER_SEED", "PLANNER_BACKUP")) providers.environmentVariable(name).orNull?.let { environment(name, it) }
}

tasks.matching { it.name == "prepareAppResources" }.configureEach { dependsOn(syncthingWindows) }

// the installer people download: inno setup around the app folder jpackage makes (iscc comes with inno setup 6)
val packageSetup by tasks.registering(Exec::class) {
    group = "distribution"
    dependsOn("createDistributable")
    val version = providers.gradleProperty("plannerVersion").get()
    val app = layout.buildDirectory.dir("compose/binaries/main/app/planner")
    val script = file("installer/planner.iss")
    inputs.dir(app)
    inputs.file(script)
    outputs.dir(layout.buildDirectory.dir("setup"))
    val candidates = listOf(
        providers.environmentVariable("ISCC").orNull,
        System.getenv("LOCALAPPDATA")?.let { "$it/Programs/Inno Setup 6/ISCC.exe" },
        "C:/Program Files (x86)/Inno Setup 6/ISCC.exe",
    )
    commandLine(
        candidates.filterNotNull().firstOrNull { file(it).exists() } ?: "iscc",
        "/Qp",
        "/DVersion=$version",
        "/DSource=${app.get().asFile.absolutePath}",
        script.absolutePath,
    )
}
