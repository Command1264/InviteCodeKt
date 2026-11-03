package io.github.command1264.invitecode.plugin

import io.github.command1264.invitecode.platform.bukkit.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class DoctorMessageLayoutsTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `doctor shows packaged provenance and localizes missing fields`() {
        val jar = directory.resolve("plugin.jar")
        val manifest = java.util.jar.Manifest()
        val commit = "0123456789abcdef0123456789abcdef01234567"
        val builtAt = "2026-11-03T09:30:00Z"
        manifest.mainAttributes.apply {
            putValue("Manifest-Version", "1.0")
            putValue("InviteCode-Git-Commit", commit)
            putValue("InviteCode-Git-Dirty", "true")
            putValue("InviteCode-Built-At", builtAt)
        }
        java.util.jar.JarOutputStream(java.nio.file.Files.newOutputStream(jar), manifest).close()
        val messages = BukkitConfigurationLoader(directory.resolve("config")) {}.load().messages
        val provenance = BuildProvenance.read(jar, java.util.logging.Logger.getAnonymousLogger())
        assertEquals(commit, provenance.commit)
        val english = DoctorMessageLayouts(messages, "en_US", MessageAudience.CONSOLE)
        val report = english.render("admin.doctor-build", provenance.doctorValues()).plainText
        assertEquals("Git commit: 0123456", report.lineSequence().first())
        assertTrue(report.contains("Packaging time (UTC): " + builtAt))

        val chinese = DoctorMessageLayouts(messages, "zh_TW", MessageAudience.RGB_PLAYER)
        val unknown = chinese.render("admin.doctor-build", BuildProvenance.fromAttributes(null).doctorValues()).plainText
        assertEquals(listOf("Git 提交：未知", "工作目錄有變更：未知", "打包時間（UTC）：未知"), unknown.lines())
    }

    @Test
    fun `formatted doctor reaches console with one prefix per line and value colors intact`() {
        val messages = BukkitConfigurationLoader(directory) {}.load().messages
        val lines = mutableListOf<String>()
        val console = java.lang.reflect.Proxy.newProxyInstance(javaClass.classLoader,
            arrayOf(org.bukkit.command.CommandSender::class.java)) { _, method, args ->
            if (method.name == "sendMessage" && args?.firstOrNull() is String) lines.add(args[0] as String)
            null
        } as org.bukkit.command.CommandSender
        val layout = DoctorMessageLayouts(messages, null, MessageAudience.CONSOLE)
        BukkitMessageOutput.sendReport(console, messages, "runtime.help-topic.doctor",
            listOf("admin.doctor-runtime" to mapOf("phase" to "ACTIVE", "generation" to "9", "executor" to "ACCEPTING")), layout::render)
        assertEquals(4, lines.size)
        lines.forEach { assertEquals(1, Regex("\\[InviteCode]").findAll(it.replace(Regex("§[0-9a-fk-or]"), "")).count()) }
        assertTrue(lines.any { it.contains("§a運作中") })
        assertFalse(lines.any { it.contains("ACTIVE") || it.contains('\n') })
    }

    @Test
    fun `doctor localizes and colors semantic values without parsing dynamic formatting`() {
        val messages = BukkitConfigurationLoader(directory) {}.load().messages
        val layout = DoctorMessageLayouts(messages, "zh_TW", MessageAudience.RGB_PLAYER)
        val runtime = layout.render("admin.doctor-runtime", mapOf("phase" to "ACTIVE", "generation" to "9", "executor" to "ACCEPTING"))
        assertTrue(runtime.plainText.contains("運作中"))
        assertTrue(runtime.plainText.contains("接受新工作"))
        assertFalse(runtime.plainText.contains("ACTIVE"))
        assertTrue(runtime.segments.any { it.text == "運作中" && it.color == 0x55FF55 })
        val integrations = layout.render("admin.doctor-integrations", mapOf("placeholder" to "false", "vault" to "true", "dialog" to "PAPER", "rgb" to "true"))
        assertTrue(integrations.plainText.contains("未啟用"))
        assertTrue(integrations.plainText.contains("可用"))
        assertTrue(integrations.plainText.contains("Paper"))
        assertTrue(integrations.segments.any { it.text == "未啟用" && it.color == 0xFF5555 })
        assertTrue(integrations.segments.any { it.text == "支援" && it.color == 0x55FF55 })
    }

    @Test
    fun `positive counts use distinct colors while zero stays gray and settings choose language`() {
        val messages = BukkitConfigurationLoader(directory) {}.load().messages
        val values = mapOf("backend" to "SQLITE", "schema" to "9", "pending" to "1", "processing" to "2", "uncertain" to "3")
        val report = DoctorMessageLayouts(messages, "en_US", MessageAudience.CONSOLE).render("admin.doctor-storage", values)
        assertTrue(report.plainText.contains("SQLite"))
        assertTrue(report.plainText.contains("Pending"))
        assertTrue(report.segments.any { it.text == "1" && it.color == 0xFFFF55 })
        assertTrue(report.segments.any { it.text == "2" && it.color == 0x55FFFF })
        assertTrue(report.segments.any { it.text == "3" && it.color == 0xFFAA00 })
        val zero = DoctorMessageLayouts(messages, "zh_TW", MessageAudience.RGB_PLAYER).render("admin.doctor-storage", values.mapValues { if (it.key in setOf("pending","processing","uncertain")) "0" else it.value })
        assertEquals(3, zero.segments.count { it.text == "0" && it.color == 0xAAAAAA })
        val configured = LocalizedMessages("en_US", false, mapOf("en_US" to mapOf("x" to "{phase}", "admin.doctor-value.phase.active" to "&aActive")))
        assertEquals("Active", DoctorMessageLayouts(configured,"zh_TW",MessageAudience.CONSOLE).render("x",mapOf("phase" to "ACTIVE")).plainText)
    }

    @Test
    fun `unknown codes are neutral literal diagnostic values`() {
        val messages = BukkitConfigurationLoader(directory) {}.load().messages
        val report = DoctorMessageLayouts(messages,"zh_TW",MessageAudience.RGB_PLAYER).render("admin.doctor-runtime",mapOf("phase" to "&c{executor}","generation" to "0","executor" to "STOPPED"))
        assertTrue(report.plainText.contains("&c{executor}"))
        assertTrue(report.segments.any { it.text.contains("&c{executor}") && it.color == 0xAAAAAA })
    }
}
