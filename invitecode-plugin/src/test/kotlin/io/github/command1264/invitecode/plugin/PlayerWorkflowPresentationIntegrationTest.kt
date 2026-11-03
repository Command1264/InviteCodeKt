package io.github.command1264.invitecode.plugin

import io.github.command1264.invitecode.core.ColorInviteCodeGenerator
import io.github.command1264.invitecode.core.InviteCodeLifecycleService
import io.github.command1264.invitecode.core.InvitationAdministrationContext
import io.github.command1264.invitecode.core.QualificationProviderRegistry
import io.github.command1264.invitecode.core.StoredPlayer
import io.github.command1264.invitecode.platform.bukkit.*
import io.github.command1264.invitecode.storage.sql.BlockingOperationGuard
import io.github.command1264.invitecode.storage.sql.SqliteInviteCodeStorage
import io.github.command1264.invitecode.storage.sql.SqliteSchemaMigrator
import net.md_5.bungee.api.chat.BaseComponent
import net.md_5.bungee.api.chat.ClickEvent
import org.bukkit.Server
import org.bukkit.entity.Player
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.plugin.Plugin
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.lang.reflect.Proxy
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.logging.Logger

/** Exercises the player workflow boundary with real asynchronous SQLite operations and no server. */
class PlayerWorkflowPresentationIntegrationTest {
    @TempDir lateinit var directory: Path

    private var fixture: Fixture? = null

    @AfterEach
    fun close() { fixture?.close() }

    @Test
    fun `code command renders one clickable code segment with raw payload and prefix`() {
        val f = fixture()
        f.controller.handle(f.inviter, command("code"))

        val output = f.awaitOutput()
        val clicked = output.flatMap { it.toList() }.filter { it.clickEvent != null }
        assertEquals(1, clicked.size)
        assertTrue(clicked.single().clickEvent!!.action == ClickEvent.Action.SUGGEST_COMMAND ||
            clicked.single().clickEvent!!.action.name == "COPY_TO_CLIPBOARD")
        assertEquals(f.inviterCode, clicked.single().clickEvent!!.value)
        assertTrue(output.flattenText().contains("[InviteCode]"))
        assertEquals(f.inviterCode, clicked.single().toPlainText())
    }

    @Test
    fun `chat claim confirmation consumes once and status and stats render through components`() {
        val f = fixture()
        val confirmation = f.startClaim(f.inviterCode).flattenText()
        assertEquals(7, confirmation.lines().size)
        assertEquals(1, confirmation.lines().count { it == "[InviteCode]" })
        assertTrue(confirmation.contains("Invite Code"))
        assertTrue(confirmation.contains("Inviter: Inviter"))
        assertTrue(confirmation.contains(f.inviterCode))

        f.controller.handle(f.invitee, command("confirm"))
        val claimed = f.awaitOutput().flattenText()
        assertTrue(claimed.contains("Your invitation has been registered."))
        f.controller.handle(f.invitee, command("confirm"))
        assertTrue(f.awaitOutput().flattenText().contains("There is no invitation to confirm"))
        assertEquals(1, f.storage!!.invitationsByInvitee(f.inviteeId).size)

        f.controller.handle(f.invitee, command("status"))
        val status = f.awaitOutput().flattenText()
        assertTrue(status.contains("awaiting qualification"))
        assertTrue(status.lines().first().contains("[InviteCode]"))

        f.controller.handle(f.inviter, command("stats"))
        val stats = f.awaitOutput().flattenText()
        assertTrue(stats.contains("1"))
        assertTrue(stats.lines().first().contains("[InviteCode]"))
    }

    @Test
    fun `decline timeout and existing claim reject before another confirmation`() {
        val f = fixture()
        assertTrue(f.startClaim(f.inviterCode).flattenText().contains("Use this Invite Code?"))
        f.controller.handle(f.invitee, command("cancel"))
        assertTrue(f.awaitOutput().flattenText().contains("cancelled"))
        f.controller.handle(f.invitee, command("confirm"))
        assertTrue(f.awaitOutput().flattenText().contains("There is no invitation to confirm"))

        assertTrue(f.startClaim(f.inviterCode).flattenText().contains("Use this Invite Code?"))
        f.advanceClock(Duration.ofSeconds(31))
        f.dispatcher.runAllDelays()
        assertTrue(f.awaitOutput().flattenText().contains("expired"))
        f.controller.handle(f.invitee, command("confirm"))
        assertTrue(f.awaitOutput().flattenText().contains("There is no invitation to confirm"))

        assertTrue(f.startClaim(f.inviterCode).flattenText().contains("Use this Invite Code?"))
        f.controller.handle(f.invitee, command("confirm"))
        f.awaitOutput()
        f.controller.handle(f.invitee, command("claim", f.inviterCode))
        assertTrue(f.awaitOutput().flattenText().contains("already"))
        assertEquals(1, f.storage!!.invitationsByInvitee(f.inviteeId).size)
    }

    @Test
    fun `native input closes and explains timeout exactly at its deadline`() {
        val dialog = FakeDialog()
        val f = fixture(dialog)
        f.controller.handle(f.invitee, command("claim"))
        f.awaitDialog(dialog)
        f.advanceClock(Duration.ofSeconds(30))
        f.dispatcher.runAllDelays()
        assertEquals(1, dialog.closed)
        assertTrue(f.awaitOutput().flattenText().contains("expired"))
        assertTrue(f.storage.invitationsByInvitee(f.inviteeId).isEmpty())
    }

    @Test
    fun `early native input timeout task reschedules until the wall clock deadline`() {
        val dialog = FakeDialog()
        val f = fixture(dialog)
        f.controller.handle(f.invitee, command("claim"))
        f.awaitDialog(dialog)
        f.advanceClock(Duration.ofSeconds(29))
        f.dispatcher.runOneDelay()
        assertEquals(0, dialog.closed)
        f.advanceClock(Duration.ofSeconds(1))
        f.dispatcher.runAllDelays()
        assertEquals(1, dialog.closed)
        assertTrue(f.awaitOutput().flattenText().contains("expired"))
    }

    @Test
    fun `late native input submission reports expiration instead of silently consuming it`() {
        val dialog = FakeDialog()
        val f = fixture(dialog)
        f.controller.handle(f.invitee, command("claim"))
        f.awaitDialog(dialog)
        f.advanceClock(Duration.ofSeconds(31))
        dialog.submit!!(f.inviterCode)
        assertTrue(f.awaitOutput().flattenText().contains("expired"))
        f.dispatcher.runAllDelays()
        assertTrue(f.storage.invitationsByInvitee(f.inviteeId).isEmpty())
    }

    @Test
    fun `expired inviter status displays the inviter reason without a second reason line`() {
        val f = fixture()
        f.advanceClock(Duration.ofHours(25))
        f.controller.handle(f.inviter, command("status"))
        val text = f.awaitOutput().flattenText()
        assertTrue(text.contains("you have already created your own Invite Code"))
        assertFalse(text.contains("the claim period has ended"))
        assertEquals(5, text.lines().size)
    }

    @Test
    fun `status reports only the current eligibility generation after reset`() {
        val f = fixture()
        fun register() {
            f.startClaim(f.inviterCode)
            f.controller.handle(f.invitee, command("confirm"))
            assertTrue(f.awaitOutput().flattenText().contains("Your invitation has been registered."))
        }
        register()
        val context = InvitationAdministrationContext(UUID.randomUUID(), "console", "test reset", Instant.parse("2026-01-01T00:00:00Z"))
        f.storage.saveAuditedClaimStateTransition(f.storage.claimState(f.inviteeId).adminResetEligibility(context))
        f.controller.handle(f.invitee, command("status"))
        assertTrue(f.awaitOutput().flattenText().contains("not registered"))
        assertEquals(1, f.storage.invitationsByInvitee(f.inviteeId).size)
        register()
        f.controller.handle(f.invitee, command("status"))
        assertTrue(f.awaitOutput().flattenText().contains("awaiting qualification"))
    }

    @Test
    fun `late native input cancel remains an explicit cancellation if timer has not run`() {
        val dialog = FakeDialog()
        val f = fixture(dialog)
        f.controller.handle(f.invitee, command("claim"))
        f.awaitDialog(dialog)
        f.advanceClock(Duration.ofSeconds(31))
        dialog.cancelInput!!()
        assertTrue(f.awaitOutput().flattenText().contains("cancelled"))
        f.dispatcher.runAllDelays()
        assertTrue(f.storage.invitationsByInvitee(f.inviteeId).isEmpty())
    }

    private fun fixture(dialog: FakeDialog? = null): Fixture = Fixture(directory, dialog).also { fixture = it }

    private fun command(vararg args: String): ParsedInviteCodeCommand =
        InviteCodeCommandParser.parse(args.toList()) as ParsedInviteCodeCommand

    private inner class Fixture(directory: Path, dialog: FakeDialog?) : AutoCloseable {
        private val guard = BlockingOperationGuard { }
        private val clock = AtomicReference(Instant.parse("2026-01-01T00:00:00Z"))
        private val now: Instant get() = clock.get()
        val inviterId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val inviteeId = UUID.fromString("00000000-0000-0000-0000-000000000002")
        val playerOutput = PlayerOutput()
        val inviter: Player = player(inviterId, "Inviter", playerOutput)
        val invitee: Player = player(inviteeId, "Invitee", playerOutput)
        val dispatcher = ControlledDispatcher()
        val storage: SqliteInviteCodeStorage
        private val async = AsyncOperationExecutor("workflow-presentation-test", dispatcher::global)
        private val config: InviteCodeRuntimeConfiguration
        val inviterCode: String
        val controller: PlayerWorkflowController

        init {
            val database = directory.resolve("workflow.db")
            SqliteSchemaMigrator(database, guard).migrate()
            storage = SqliteInviteCodeStorage.open(database, guard)
            storage.savePlayer(StoredPlayer(inviterId, now, "Inviter"))
            storage.savePlayer(StoredPlayer(inviteeId, now, "Invitee"))
            inviterCode = InviteCodeLifecycleService(storage, ColorInviteCodeGenerator(), 64)
                .assign(inviterId, now).code.displayValue()
            val data = directory.resolve("config")
            val loaded = BukkitConfigurationLoader(data) {}.load()
            config = loaded.copy(
                general = loaded.general.copy(
                    claim = loaded.general.claim.copy(
                        inputMode = if (dialog == null) InputMode.CHAT else InputMode.DIALOG,
                        confirmationTimeout = Duration.ofSeconds(30),
                        chatInputTimeout = Duration.ofSeconds(30),
                    ),
                ),
            )
            dispatcher.players[inviterId] = inviter
            dispatcher.players[inviteeId] = invitee
            val plugin = proxy(Plugin::class.java) { method, _ ->
                when (method.name) { "getLogger" -> Logger.getLogger("InviteCode-test"); "getServer" -> proxy(Server::class.java) { _, _ -> null }; else -> defaultValue(method.returnType) }
            }
            controller = PlayerWorkflowController(
                plugin,
                AtomicRuntimeSnapshot(config),
                AtomicReference(storage),
                async,
                dispatcher,
                ClaimInteractionSessions { now },
                QualificationProviderRegistry(),
                AtomicReference(null),
                PlatformCapabilities(nativeDialogInput = dialog != null, rgbText = false),
                dialog ?: UnavailableNativeDialogInput,
                LegacyInviteCodeDisplayFormatter,
                now = { now },
            )
        }

        fun startClaim(code: String): List<List<BaseComponent>> {
            controller.handle(invitee, command("claim"))
            awaitOutput()
            val event = AsyncPlayerChatEvent(true, invitee, code, mutableSetOf(invitee))
            controller.onChat(event)
            assertTrue(event.isCancelled)
            return awaitOutput()
        }

        fun advanceClock(duration: Duration) { clock.updateAndGet { it.plus(duration) } }

        fun awaitDialog(dialog: FakeDialog) {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (dialog.submit == null && System.nanoTime() < deadline) {
                dispatcher.runOneTask()
                Thread.sleep(1)
            }
            assertNotNull(dialog.submit)
        }

        fun awaitOutput(): List<List<BaseComponent>> = playerOutput.awaitOutput(dispatcher)

        override fun close() {
            async.close(Duration.ofSeconds(2))
            storage.close()
        }
    }

    private class PlayerOutput {
        private val available = java.util.concurrent.LinkedBlockingQueue<List<BaseComponent>>()
        fun record(components: Array<out BaseComponent>) {
            val batch = components.toList()
            available.put(batch)
        }
        fun awaitOutput(dispatcher: ControlledDispatcher): List<List<BaseComponent>> {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            var first: List<BaseComponent>? = null
            while (first == null && System.nanoTime() < deadline) {
                first = available.poll(20, TimeUnit.MILLISECONDS)
                if (first == null) dispatcher.runOneTask()
            }
            val result = mutableListOf(first ?: fail("Timed out waiting for player output; no server is running in this test."))
            while (true) result += available.poll() ?: break
            return result
        }
    }

    private class ControlledDispatcher : PlatformTaskDispatcher {
        val players = ConcurrentHashMap<UUID, Player>()
        private val callbacks = java.util.concurrent.LinkedBlockingQueue<() -> Unit>()
        private val playerWork = java.util.concurrent.LinkedBlockingQueue<() -> Unit>()
        private val delays = java.util.concurrent.LinkedBlockingQueue<() -> Unit>()
        override fun track(player: Player) = Unit
        override fun untrack(player: Player) = Unit
        override fun onlinePlayers(): Collection<Player> = players.values
        override fun onlinePlayerNames(): Collection<String> = players.values.map(Player::getName)
        override fun global(operation: () -> Unit) { callbacks.put(operation) }
        override fun player(player: Player, operation: () -> Unit, retired: () -> Unit) { playerWork.put(operation) }
        override fun repeatGlobal(delayTicks: Long, periodTicks: Long, operation: () -> Unit): AutoCloseable = AutoCloseable {}
        override fun delayPlayer(player: Player, delayTicks: Long, operation: () -> Unit, retired: () -> Unit) { delays.put(operation) }
        override fun onlinePlayer(playerId: UUID): Player? = players[playerId]
        override fun currentPlayer(playerId: UUID): Player? = players[playerId]
        override fun isManagedThread(): Boolean = true
        fun runAllDelays() {
            while (true) (delays.poll() ?: break).invoke()
        }
        fun runOneDelay() { delays.poll()?.invoke() }
        fun runOneTask() {
            (playerWork.poll() ?: callbacks.poll())?.invoke()
        }
    }

    private class FakeDialog : NativeDialogInput {
        override val available = true
        var submit: ((String) -> Unit)? = null
        var cancelInput: (() -> Unit)? = null
        var closed = 0
        override fun show(player: Player, request: NativeDialogRequest, onSubmit: (String) -> Unit, onCancel: () -> Unit): Boolean {
            submit = onSubmit
            cancelInput = onCancel
            return true
        }
        override fun cancel(player: Player) { closed++ }
        override fun disconnect(playerId: UUID) = Unit
        override fun close() = Unit
    }

    private fun player(id: UUID, name: String, output: PlayerOutput): Player {
        val spigot = object : Player.Spigot() {
            override fun sendMessage(vararg components: BaseComponent) = output.record(components)
            override fun getLocale(): String = "en_US"
        }
        return proxy(Player::class.java) { method, _ ->
            when (method.name) {
                "getUniqueId" -> id; "getName" -> name; "isOnline" -> true; "spigot" -> spigot
                "hasPlayedBefore" -> false; else -> defaultValue(method.returnType)
            }
        }
    }

    private fun <T> proxy(type: Class<T>, handler: (java.lang.reflect.Method, Array<out Any?>?) -> Any?): T =
        Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args -> handler(method, args) } as T

    private fun defaultValue(type: Class<*>): Any? = when (type) {
        java.lang.Boolean.TYPE -> false; java.lang.Integer.TYPE -> 0; java.lang.Long.TYPE -> 0L
        java.lang.Double.TYPE -> 0.0; java.lang.Float.TYPE -> 0f; java.lang.Short.TYPE -> 0.toShort()
        java.lang.Byte.TYPE -> 0.toByte(); java.lang.Character.TYPE -> '\u0000'; else -> null
    }

    private fun List<List<BaseComponent>>.flattenText(): String = flatten().joinToString("") { it.toPlainText() }
}
