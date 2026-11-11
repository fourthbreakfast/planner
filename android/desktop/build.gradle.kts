import org.gradle.api.tasks.bundling.Compression
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
val syncthingLinux =
    syncthingBinary("syncthingLinux", goos = "linux", goarch = "amd64", fileName = "linux/syncthing", outputDir = "syncthing-linux/resources")
val desktopOs = providers.systemProperty("os.name").get()
val desktopArchitecture = providers.systemProperty("os.arch").get()
val linuxHost = desktopOs.startsWith("Linux", ignoreCase = true)
val windowsHost = desktopOs.startsWith("Windows", ignoreCase = true)
val syncthingHost = if (linuxHost) syncthingLinux else syncthingWindows

// built for the java 21 runtime the installer carries
kotlin {
    jvmToolchain(21)
}

// the desktop app: the shared screens in a window, with its own syncthing beside it
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
        if (linuxHost) jvmArgs("-Dsun.awt.X11.XWMClass=fourthbreakfast-planner")
        // packaging needs a jdk with jpackage, which the one inside android studio leaves out: gradle fetches temurin 21
        javaHome = javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(21))
            vendor.set(JvmVendorSpec.ADOPTIUM)
        }.get().metadata.installationPath.asFile.absolutePath
        nativeDistributions {
            appResourcesRootDir.set(syncthingHost.flatMap { it.output })
            if (linuxHost) targetFormats(TargetFormat.Deb)
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
            linux {
                iconFile.set(project.file("icons/planner.png"))
                packageName = "fourthbreakfast-planner"
                appCategory = "utils"
                menuGroup = "Office"
                debMaintainer = "214921250+fourthbreakfast@users.noreply.github.com"
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

tasks.matching { it.name == "prepareAppResources" }.configureEach {
    dependsOn(syncthingHost)
    doFirst {
        check(windowsHost || linuxHost) { "Desktop packages currently support Windows and Linux" }
        check(desktopArchitecture.lowercase() in setOf("amd64", "x86_64")) { "Desktop packages currently support x86-64" }
    }
}

tasks.matching { it.name == "createDistributable" }.configureEach {
    val binary = layout.buildDirectory.file("compose/binaries/main/app/planner/lib/app/resources/syncthing")
    doLast {
        if (linuxHost) {
            check(binary.get().asFile.setExecutable(true, false)) { "Cannot mark bundled Linux Syncthing executable" }
        }
    }
}

// the installer people download: inno setup around the app folder jpackage makes (iscc comes with inno setup 6)
val packageSetup by tasks.registering(Exec::class) {
    group = "distribution"
    dependsOn("createDistributable")
    onlyIf { windowsHost }
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

// A relocatable Linux folder with the same bundled runtime as the .deb. It never needs a system Java installation.
tasks.register<Tar>("packageLinuxTar") {
    group = "distribution"
    dependsOn("createDistributable")
    onlyIf { linuxHost }
    val version = providers.gradleProperty("plannerVersion").get()
    val app = layout.buildDirectory.dir("compose/binaries/main/app/planner")
    archiveFileName.set("planner-$version-linux-x64.tar.gz")
    destinationDirectory.set(layout.buildDirectory.dir("portable"))
    compression = Compression.GZIP
    val executables = listOf("bin/**", "lib/runtime/bin/**", "lib/runtime/lib/jexec", "lib/runtime/lib/jspawnhelper", "lib/app/resources/syncthing")
    into("planner") {
        from(app) { exclude(executables) }
        from(app) {
            include(executables)
            filePermissions { unix("755") }
        }
    }
}
