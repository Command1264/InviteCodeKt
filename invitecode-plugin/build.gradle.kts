import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.testing.Test
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.zip.ZipFile

plugins {
    id("com.gradleup.shadow")
}

abstract class VerifyPluginJar : DefaultTask() {
    @get:InputFile
    abstract val archiveFile: RegularFileProperty

    @get:Input
    abstract val requiredEntries: ListProperty<String>

    @get:Input
    abstract val forbiddenEntries: ListProperty<String>

    @TaskAction
    fun verify() {
        val archive = archiveFile.get().asFile

        ZipFile(archive).use { zip ->
            requiredEntries.get().forEach { entry ->
                check(zip.getEntry(entry) != null) {
                    "${archive.name} is missing required entry $entry"
                }
            }

            forbiddenEntries.get().forEach { entry ->
                check(zip.getEntry(entry) == null) {
                    "${archive.name} contains obsolete root entry $entry"
                }
            }

            val pluginYml = zip.getInputStream(requireNotNull(zip.getEntry("plugin.yml"))).use { input ->
                Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(input.readBytes())).toString()
            }
            check(pluginYml.all { it.code <= 0x7f }) {
                "plugin.yml must contain ASCII text only"
            }
            check(pluginYml.lineSequence().any { it.trim() == "name: InviteCode" }) {
                "plugin.yml must declare the product name InviteCode"
            }
            check(pluginYml.lineSequence().any { it.trim() == "api-version: '1.13'" }) {
                "plugin.yml must declare the oldest supported modern API version"
            }

            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (!entry.name.endsWith(".class")) continue

                zip.getInputStream(entry).use { input ->
                    val bytes = input.readBytes()
                    check(bytes.size >= 8) {
                        "Unable to read class header for ${entry.name}"
                    }
                    val major = ((bytes[6].toInt() and 0xff) shl 8) or
                        (bytes[7].toInt() and 0xff)
                    check(major <= 52) {
                        "${entry.name} targets class-file major $major; Java 8 requires 52 or lower"
                    }
                    if (!entry.name.startsWith(
                            "io/github/command1264/invitecode/plugin/VaultEconomyRewardGateway",
                        )
                    ) {
                        val constants = String(bytes, StandardCharsets.ISO_8859_1)
                        check(!constants.contains("net/milkbowl/vault/")) {
                            "${entry.name} links the optional Vault API outside its isolated adapter"
                        }
                    }
                }
            }
        }
    }
}

description = "Installable InviteCode Bukkit plugin."

dependencies {
    implementation(project(":invitecode-api"))
    implementation(project(":invitecode-core"))
    implementation(project(":invitecode-storage-json"))
    implementation(project(":invitecode-storage-sql"))
    implementation(project(":invitecode-platform-bukkit"))
    implementation(project(":invitecode-platform-modern"))
    compileOnly("org.spigotmc:spigot-api:1.8.8-R0.1-SNAPSHOT")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7")
    testImplementation("org.spigotmc:spigot-api:1.8.8-R0.1-SNAPSHOT")
}

val pluginVersion = version.toString()

tasks.processResources {
    filteringCharset = "UTF-8"
    inputs.property("version", pluginVersion)
    filesMatching("plugin.yml") {
        expand(mapOf("version" to pluginVersion))
    }
}

val shadowJar = tasks.named<ShadowJar>("shadowJar") {
    inputs.property("java8CompatibilityLayout", "v1")
    archiveBaseName.set("InviteCode")
    archiveClassifier.set("")
    exclude("module-info.class", "META-INF/versions/**")
    mergeServiceFiles()
}

tasks.withType<Test>().configureEach {
    dependsOn(shadowJar)
    doFirst {
        systemProperty("invitecode.shadowJar", shadowJar.get().archiveFile.get().asFile.absolutePath)
    }
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
            "config/config.yml",
            "config/storage.yml",
            "config/qualifications.yml",
            "config/rewards.yml",
            "config/messages/zh_TW.yml",
            "config/messages/en_US.yml",
            "io/github/command1264/invitecode/plugin/InviteCodePlugin.class",
            "org/sqlite/JDBC.class",
            "org/sqlite/native/Linux/x86_64/libsqlitejdbc.so",
            "org/sqlite/native/Windows/x86_64/sqlitejdbc.dll",
            "META-INF/services/java.sql.Driver",
        ),
    )
    forbiddenEntries.set(
        listOf(
            "config.yml",
            "storage.yml",
            "qualifications.yml",
            "rewards.yml",
            "messages/zh_TW.yml",
            "messages/en_US.yml",
        ),
    )
}

tasks.named("check") {
    dependsOn(verifyPluginJar)
}
