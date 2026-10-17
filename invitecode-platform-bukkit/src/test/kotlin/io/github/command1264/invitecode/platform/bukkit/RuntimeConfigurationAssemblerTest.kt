package io.github.command1264.invitecode.platform.bukkit

import io.github.command1264.invitecode.core.StorageBackendType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class RuntimeConfigurationAssemblerTest {
    @Test
    fun `only presentation prefix may be empty and must remain a single unpadded line`() {
        val catalog = mapOf("messages-version" to 1, "presentation" to mapOf("prefix" to ""))
        assertEquals("", MessageCatalogParser.parse("zh_TW", catalog)["presentation.prefix"])
        for (prefix in listOf("[InviteCode] ", "[InviteCode]\n", " ")) {
            assertThrows(IllegalArgumentException::class.java) {
                MessageCatalogParser.parse("zh_TW", mapOf("messages-version" to 1, "presentation" to mapOf("prefix" to prefix)))
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            MessageCatalogParser.parse("zh_TW", mapOf("messages-version" to 1, "message" to ""))
        }
    }

    @Test
    fun `parses all configuration documents into one snapshot`() {
        val configuration = RuntimeConfigurationAssembler.parse(
            general = general(),
            storage = storage(),
            qualifications = qualifications(),
            rewards = rewards(),
            messageCatalogs = mapOf(
                "zh_TW" to catalog("邀請成功"),
                "en_US" to catalog("Invitation completed"),
            ),
        )

        assertEquals(StorageBackendType.SQLITE, configuration.storage.backend)
        assertEquals("邀請成功", configuration.messages.resolve("ja_JP", "invite.completed"))
        assertEquals("Invitation completed", configuration.messages.resolve("en_US", "invite.completed"))
    }

    @Test
    fun `invalid replacement does not change installed snapshot`() {
        val snapshots = AtomicRuntimeSnapshot(
            RuntimeConfigurationAssembler.parse(
                general(), storage(), qualifications(), rewards(), mapOf("zh_TW" to catalog("舊訊息")),
            ),
        )

        assertThrows(IllegalArgumentException::class.java) {
            val invalid = RuntimeConfigurationAssembler.parse(
                general(), storage(), qualifications(), rewards(),
                mapOf("zh_TW" to mapOf("messages-version" to 2, "invite" to mapOf("completed" to "新訊息"))),
            )
            snapshots.replace(invalid)
        }

        assertEquals(0, snapshots.current().generation)
        assertEquals("舊訊息", snapshots.current().value.messages.resolve(null, "invite.completed"))
    }

    @Test
    fun `JSON backend is accepted in the configuration snapshot`() {
        val json = storage().toMutableMap().apply { this["backend"] = "JSON" }

        val configured = RuntimeConfigurationAssembler.parse(
            general(), json, qualifications(), rewards(), mapOf("zh_TW" to catalog("訊息")),
        )
        assertEquals(StorageBackendType.JSON, configured.storage.backend)
    }

    @Test
    fun `reload applies non storage changes while deferring storage cutover`() {
        val current = RuntimeConfigurationAssembler.parse(
            general(), storage(), qualifications(), rewards(), mapOf("zh_TW" to catalog("舊訊息")),
        )
        val changedStorage = storage().toMutableMap().apply {
            this["sqlite"] = mapOf("file" to "replacement.db")
        }
        val candidate = RuntimeConfigurationAssembler.parse(
            general(), changedStorage, qualifications(), rewards(), mapOf("zh_TW" to catalog("新訊息")),
        )

        val reload = RuntimeConfigurationReload.reconcile(current, candidate)

        assertEquals(true, reload.storageRestartRequired)
        assertEquals("invitecode.db", reload.configuration.storage.sqlite.file)
        assertEquals("新訊息", reload.configuration.messages.resolve(null, "invite.completed"))
    }

    private fun general(): Map<String, Any?> = mapOf(
        "config-version" to 1,
        "language" to mapOf("default" to "zh_TW", "use-client-locale" to true),
        "invite-code" to mapOf("format" to "HEX_COLOR", "custom-codes-enabled" to false),
        "claim" to mapOf(
            "window" to "24h",
            "input-mode" to "AUTO",
            "confirmation-timeout" to "30s",
            "chat-input-timeout" to "60s",
            "existing-player-policy" to "START_WINDOW",
        ),
        "anti-abuse" to mapOf("matching-ip-action" to "DENY", "ip-hash-retention" to "30d"),
        "time-zone" to "Asia/Taipei",
    )

    private fun storage(): Map<String, Any?> = mapOf(
        "config-version" to 1,
        "backend" to "SQLITE",
        "sqlite" to mapOf("file" to "invitecode.db"),
        "mysql" to mapOf(
            "host" to "localhost",
            "port" to 3306,
            "database" to "invitecode",
            "username" to "invitecode",
            "password" to "${'$'}{INVITECODE_DB_PASSWORD}",
            "network-secret" to "${'$'}{INVITECODE_NETWORK_SECRET}",
            "connection-timeout" to "5s",
            "tls-mode" to "VERIFY_IDENTITY",
        ),
    )

    private fun qualifications(): Map<String, Any?> = mapOf(
        "config-version" to 1,
        "policies" to mapOf(
            "new-player" to mapOf(
                "any-of" to listOf(
                    mapOf(
                        "all-of" to listOf(
                            mapOf("type" to "PLAY_TIME", "at-least" to "30m"),
                        ),
                    ),
                ),
            ),
        ),
    )

    private fun rewards(): Map<String, Any?> = mapOf(
        "config-version" to 1,
        "qualification-rewards" to mapOf(
            "default" to mapOf(
                "inviter" to listOf(mapOf("type" to "MESSAGE", "message" to "invite.completed")),
                "invitee" to emptyList<Any>(),
            ),
        ),
        "milestone-rewards" to emptyMap<String, Any>(),
    )

    private fun catalog(message: String): Map<String, Any?> = mapOf(
        "messages-version" to 1,
        "invite" to mapOf("completed" to message),
    )
}
