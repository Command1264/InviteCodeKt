import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.DataInputStream

abstract class VerifyJava8Bytecode : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val classFiles: ConfigurableFileCollection

    @TaskAction
    fun verify() {
        val files = classFiles.asFileTree.files.filter { it.extension == "class" }
        check(files.isNotEmpty()) { "No production class files were found to verify." }
        files.forEach { classFile ->
            val major = DataInputStream(classFile.inputStream().buffered()).use { input ->
                check(input.readInt() == 0xCAFEBABE.toInt()) { "Invalid class file: $classFile" }
                input.readUnsignedShort()
                input.readUnsignedShort()
            }
            check(major <= 52) {
                "$classFile uses class major $major; expected Java 8 major 52 or older."
            }
        }
    }
}

plugins {
    base
    kotlin("jvm") version "2.3.21" apply false
    id("com.gradleup.shadow") version "8.3.11" apply false
}

allprojects {
    group = providers.gradleProperty("group").get()
    version = providers.gradleProperty("version").get()
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
        withSourcesJar()
    }

    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_1_8)
            if (JavaVersion.current().isJava9Compatible) {
                freeCompilerArgs.add("-Xjdk-release=8")
            }
        }
    }

    dependencies {
        add("testImplementation", platform("org.junit:junit-bom:5.13.4"))
        add("testImplementation", "org.junit.jupiter:junit-jupiter")
        add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events("failed", "skipped")
        }
    }
}

val verifyJava8Bytecode by tasks.registering(VerifyJava8Bytecode::class) {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Verifies that scaffold production classes use Java 8 bytecode or older."
    dependsOn(subprojects.map { "${it.path}:classes" })
    classFiles.from(subprojects.map { it.layout.buildDirectory.dir("classes/kotlin/main") })
    classFiles.from(subprojects.map { it.layout.buildDirectory.dir("classes/java/main") })
}

tasks.named("check") {
    dependsOn(verifyJava8Bytecode)
}
