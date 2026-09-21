import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import java.util.zip.ZipFile

plugins {
    id("com.gradleup.shadow")
}

abstract class VerifyPluginJar : DefaultTask() {
    @get:InputFile
    abstract val archiveFile: RegularFileProperty

    @get:Input
    abstract val requiredEntries: ListProperty<String>

    @TaskAction
    fun verify() {
        val archive = archiveFile.get().asFile

        ZipFile(archive).use { zip ->
            requiredEntries.get().forEach { entry ->
                check(zip.getEntry(entry) != null) {
                    "${archive.name} is missing required entry $entry"
                }
            }

            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (!entry.name.endsWith(".class")) continue

                zip.getInputStream(entry).use { input ->
                    val header = ByteArray(8)
                    check(input.read(header) == header.size) {
                        "Unable to read class header for ${entry.name}"
                    }
                    val major = ((header[6].toInt() and 0xff) shl 8) or
                        (header[7].toInt() and 0xff)
                    check(major <= 52) {
                        "${entry.name} targets class-file major $major; Java 8 requires 52 or lower"
                    }
                }
            }
        }
    }
}

description = "Installable InviteCodeKt Bukkit plugin."

dependencies {
    implementation(project(":invitecode-api"))
    implementation(project(":invitecode-core"))
    implementation(project(":invitecode-storage-json"))
    implementation(project(":invitecode-storage-sql"))
    implementation(project(":invitecode-platform-bukkit"))
    implementation(project(":invitecode-platform-modern"))
    compileOnly("org.spigotmc:spigot-api:1.8.8-R0.1-SNAPSHOT")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7")
}

val pluginVersion = version.toString()

tasks.processResources {
    inputs.property("version", pluginVersion)
    filesMatching("plugin.yml") {
        expand(mapOf("version" to pluginVersion))
    }
}

val shadowJar = tasks.named<ShadowJar>("shadowJar") {
    inputs.property("java8CompatibilityLayout", "v1")
    archiveBaseName.set("InviteCodeKt")
    archiveClassifier.set("")
    exclude("module-info.class", "META-INF/versions/**")
    mergeServiceFiles()
}

tasks.build {
    dependsOn(shadowJar)
}

val verifyPluginJar by tasks.registering(VerifyPluginJar::class) {
    dependsOn(shadowJar)
    archiveFile.set(shadowJar.flatMap { it.archiveFile })
    requiredEntries.set(
        listOf(
            "plugin.yml",
            "config.yml",
            "storage.yml",
            "qualifications.yml",
            "rewards.yml",
            "messages/zh_TW.yml",
            "messages/en_US.yml",
            "io/github/command1264/invitecode/plugin/InviteCodePlugin.class",
            "org/sqlite/JDBC.class",
            "org/sqlite/native/Linux/x86_64/libsqlitejdbc.so",
            "org/sqlite/native/Windows/x86_64/sqlitejdbc.dll",
            "META-INF/services/java.sql.Driver",
        ),
    )
}

tasks.named("check") {
    dependsOn(verifyPluginJar)
}
