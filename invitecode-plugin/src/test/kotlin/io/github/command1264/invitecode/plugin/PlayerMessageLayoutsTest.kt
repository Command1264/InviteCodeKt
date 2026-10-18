package io.github.command1264.invitecode.plugin

import io.github.command1264.invitecode.core.InviteCode
import io.github.command1264.invitecode.core.InviteCodeFormats
import io.github.command1264.invitecode.platform.bukkit.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class PlayerMessageLayoutsTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `status reports real reasons within the same prefixed reply`() {
        val layout = PlayerMessageLayouts(BukkitConfigurationLoader(directory) {}.load().messages, "en_US", MessageAudience.LEGACY_PLAYER)
        val status = layout.status(false, "claimed", 2, listOf("already registered", "claim period ended"))
        assertEquals(1, Regex("\\[InviteCode]").findAll(status.plainText).count())
        assertTrue(status.plainText.endsWith("Cannot register: already registered\nclaim period ended"))
        assertFalse(layout.status(true, "none", 0, emptyList()).plainText.contains("Cannot register"))
    }

    @Test
    fun `bundled chat and native confirmation share the seven logical sections without trailing spaces`() {
        val messages = BukkitConfigurationLoader(directory) {}.load().messages
        val layout = PlayerMessageLayouts(messages, "zh_TW", MessageAudience.RGB_PLAYER)
        val code = InviteCode.parse("A1B2C3", InviteCodeFormats.colorHex)
        val chat = layout.chatConfirmation("&c{code}", code, 60)
        assertEquals(listOf("[InviteCode]", "確認邀請碼", "邀請人：&c{code}", "邀請碼：A1B2C3", "是否使用這組邀請碼？", "請在 60 秒內選擇"), chat.plainText.lines())
        assertTrue(chat.plainText.lines().none { it.endsWith(' ') })
        val dialog = layout.confirmationDialog("&c{code}", code, 60)
        assertEquals("[InviteCode]", dialog.title)
        assertEquals(chat.plainText.lines().drop(1), dialog.presentation!!.body.map { it.plainText })
        assertEquals("同意", dialog.presentation!!.confirm.plainText)
        assertEquals(0xA1B2C3, dialog.presentation!!.body[2].segments.last().color)
    }

    @Test
    fun `empty prefix uses useful dialog title without duplicate heading and respects English locale`() {
        BukkitConfigurationLoader(directory) {}.load()
        val path = directory.resolve("messages/en_US.yml")
        val text = java.nio.file.Files.readAllBytes(path).toString(Charsets.UTF_8)
            .replace("prefix: \"&7[&6InviteCode&7]&r\"", "prefix: \"\"")
        java.nio.file.Files.write(path, text.toByteArray(Charsets.UTF_8))
        val layout = PlayerMessageLayouts(BukkitConfigurationLoader(directory) {}.load().messages, "en_US", MessageAudience.RGB_PLAYER)
        val dialog = layout.confirmationDialog("Alex", InviteCode.parse("A1B2C3", InviteCodeFormats.colorHex), 60)
        assertEquals("Confirm Invite Code", dialog.title)
        assertEquals(4, dialog.presentation!!.body.size)
        assertEquals("Inviter: Alex", dialog.presentation!!.body.first().plainText)
        val input = layout.inputDialog()
        assertEquals("Enter Invite Code", input.title)
        assertTrue(input.presentation!!.body.isEmpty())
        assertEquals("Six-character Invite Code", input.presentation!!.inputLabel!!.plainText)
    }

    @Test
    fun `literal text identical to code is not clickable and real code payload stays raw`() {
        val messages = LocalizedMessages("zh_TW", false, mapOf("zh_TW" to mapOf(
            "presentation.prefix" to "[InviteCode]", "command.code.value" to "A1B2C3 {code}",
        )))
        val code = InviteCode.parse("A1B2C3", InviteCodeFormats.colorHex)
        val message = PlayerMessageLayouts(messages, null, MessageAudience.LEGACY_PLAYER).code(code)
        val hint = PresentedMessage(listOf(MessageSegment("Copy &c{code}")))
        val components = PlayerChatActions.codeMessage(message, code.displayValue(), hint, hint)
        val clicked = components.filter { it.clickEvent != null }
        assertEquals(1, clicked.size)
        assertEquals("A1B2C3", clicked.single().clickEvent.value)
        assertEquals("A1B2C3", clicked.single().toPlainText())
        assertEquals("Copy &c{code}", clicked.single().hoverEvent.value.joinToString("") { it.toPlainText() })
        assertEquals("[InviteCode] A1B2C3 A1B2C3", components.joinToString("") { it.toPlainText() })
    }
}
