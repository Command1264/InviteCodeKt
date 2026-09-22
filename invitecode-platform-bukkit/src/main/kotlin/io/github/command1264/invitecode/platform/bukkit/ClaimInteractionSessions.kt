package io.github.command1264.invitecode.platform.bukkit

import io.github.command1264.invitecode.core.ClaimConfirmation
import java.time.Duration
import java.time.Instant
import java.util.UUID

class ClaimInteractionSessions(
    private val now: () -> Instant = Instant::now,
) {
    private val sessions = mutableMapOf<UUID, Session>()

    @Synchronized
    fun startChatInput(playerId: UUID, timeout: Duration) {
        require(!timeout.isZero && !timeout.isNegative) { "Chat input timeout must be greater than zero." }
        sessions[playerId] = Session.ChatInput(now().plus(timeout))
    }

    @Synchronized
    fun startDialogInput(playerId: UUID, timeout: Duration) {
        require(!timeout.isZero && !timeout.isNegative) { "Dialog input timeout must be greater than zero." }
        sessions[playerId] = Session.DialogInput(now().plus(timeout))
    }

    @Synchronized
    fun startConfirmation(confirmation: ClaimConfirmation) {
        sessions[confirmation.inviteeId] = Session.Confirmation(confirmation)
    }

    @Synchronized
    fun hasChatInput(playerId: UUID): Boolean {
        val session = sessions[playerId] as? Session.ChatInput ?: return false
        if (now().isAfter(session.expiresAt)) {
            sessions.remove(playerId)
            return false
        }
        return true
    }

    @Synchronized
    fun consumeChatInput(playerId: UUID, message: String): String? {
        val session = sessions[playerId] as? Session.ChatInput ?: return null
        sessions.remove(playerId)
        if (now().isAfter(session.expiresAt)) return null
        return message.trim()
    }

    @Synchronized
    fun consumeDialogInput(playerId: UUID, value: String): String? {
        val session = sessions[playerId] as? Session.DialogInput ?: return null
        sessions.remove(playerId)
        if (now().isAfter(session.expiresAt)) return null
        return value.trim()
    }

    @Synchronized
    fun consumeConfirmation(playerId: UUID): ClaimConfirmation? {
        val session = sessions[playerId] as? Session.Confirmation ?: return null
        sessions.remove(playerId)
        if (now().isAfter(session.value.expiresAt)) return null
        return session.value
    }

    @Synchronized
    fun cancel(playerId: UUID): Boolean = sessions.remove(playerId) != null

    @Synchronized
    fun expireChatInput(playerId: UUID): Boolean {
        val session = sessions[playerId] as? Session.ChatInput ?: return false
        if (!now().isAfter(session.expiresAt)) return false
        sessions.remove(playerId)
        return true
    }

    @Synchronized
    fun expireDialogInput(playerId: UUID): Boolean {
        val session = sessions[playerId] as? Session.DialogInput ?: return false
        if (!now().isAfter(session.expiresAt)) return false
        sessions.remove(playerId)
        return true
    }

    @Synchronized
    fun expireConfirmation(playerId: UUID): Boolean {
        val session = sessions[playerId] as? Session.Confirmation ?: return false
        if (!now().isAfter(session.value.expiresAt)) return false
        sessions.remove(playerId)
        return true
    }

    fun disconnect(playerId: UUID) {
        cancel(playerId)
    }

    @Synchronized
    fun clear() {
        sessions.clear()
    }

    private sealed class Session {
        data class ChatInput(val expiresAt: Instant) : Session()
        data class DialogInput(val expiresAt: Instant) : Session()
        data class Confirmation(val value: ClaimConfirmation) : Session()
    }
}
