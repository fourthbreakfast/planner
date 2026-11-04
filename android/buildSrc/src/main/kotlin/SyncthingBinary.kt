import java.io.File
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.register
import org.gradle.process.ExecOperations

/**
 * Builds Syncthing from the pinned source in third_party/syncthing for one platform, with the Go version Syncthing
 * itself asks for. It is built without C, so its database is the pure Go SQLite and no Android NDK is needed. The
 * binary lands at [fileName] inside [output].
 */
abstract class SyncthingBinary : DefaultTask() {
    @get:Inject
    abstract val exec: ExecOperations

    @get:Internal
    abstract val source: DirectoryProperty

    /** The release the submodule is on, so a new pin rebuilds it. */
    @get:Input
    abstract val version: Property<String>

    @get:Input
    abstract val goos: Property<String>

    @get:Input
    abstract val goarch: Property<String>

    @get:Input
    abstract val fileName: Property<String>

    @get:OutputDirectory
    abstract val output: DirectoryProperty

    @TaskAction
    fun build() {
        val root = source.get().asFile
        val goVersion = File(root, "go.mod").readLines().first { it.startsWith("go ") }.removePrefix("go ").trim()
        val toolchain = mapOf("GOTOOLCHAIN" to "go$goVersion")
        // the web interface's files are generated, not checked in; Syncthing will not build without them
        exec.exec {
            workingDir = root
            environment(toolchain)
            commandLine("go", "run", "build.go", "assets")
        }
        val stamp = "github.com/syncthing/syncthing/lib/build"
        val flags = listOf(
            "-s", "-w", "-buildid=",
            // a network library Syncthing uses reaches into Go's own, which Go only allows when asked
            "-checklinkname=0",
            "-X $stamp.Version=${version.get()}",
            "-X $stamp.User=planner",
            "-X $stamp.Host=planner",
            "-X $stamp.Tags=noupgrade",
        ).joinToString(" ")
        val target = output.get().file(fileName.get()).asFile
        target.parentFile.mkdirs()
        exec.exec {
            workingDir = root
            environment(toolchain + mapOf("CGO_ENABLED" to "0", "GOOS" to goos.get(), "GOARCH" to goarch.get()))
            commandLine("go", "build", "-trimpath", "-tags", "noupgrade", "-ldflags", flags, "-o", target.absolutePath, "./cmd/syncthing")
        }
    }
}

/**
 * Registers [name], which builds the pinned Syncthing for [goos] and [goarch] into the build folder's [outputDir], at
 * [fileName] there. The phone and the windows app each register one for themselves.
 */
fun Project.syncthingBinary(name: String, goos: String, goarch: String, fileName: String, outputDir: String): TaskProvider<SyncthingBinary> {
    val pinned = rootProject.layout.projectDirectory.dir("../third_party/syncthing")
    return tasks.register<SyncthingBinary>(name) {
        source.set(pinned)
        version.set(
            providers.exec { commandLine("git", "-C", pinned.asFile.path, "describe", "--tags", "--always") }
                .standardOutput.asText.map { it.trim() },
        )
        this.goos.set(goos)
        this.goarch.set(goarch)
        this.fileName.set(fileName)
        output.set(layout.buildDirectory.dir(outputDir))
    }
}
