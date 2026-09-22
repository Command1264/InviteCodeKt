package io.github.command1264.invitecode.platform.modern

import io.github.command1264.invitecode.platform.bukkit.NativeDialogInput
import io.github.command1264.invitecode.platform.bukkit.NativeDialogRequest
import io.github.command1264.invitecode.platform.bukkit.UnavailableNativeDialogInput
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.plugin.EventExecutor
import org.bukkit.plugin.Plugin
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer
import java.util.logging.Level
import java.util.logging.Logger

internal sealed class DialogResponse {
    abstract val playerId: UUID

    data class Submitted(override val playerId: UUID, val value: String?) : DialogResponse()
    data class Cancelled(override val playerId: UUID) : DialogResponse()
}

internal interface DialogBackend {
    val provider: String
    fun register(listener: (DialogResponse) -> Unit)
    fun show(player: Player, request: NativeDialogRequest)
    fun close(player: Player)
}

internal class ReflectiveNativeDialogInput(
    private val backend: DialogBackend,
    private val logger: Logger,
) : NativeDialogInput {
    private data class Callbacks(
        val onSubmit: (String) -> Unit,
        val onCancel: () -> Unit,
    )

    private val callbacks = ConcurrentHashMap<UUID, Callbacks>()
    override val available: Boolean = true
    val provider: String = backend.provider

    init {
        backend.register(::handle)
    }

    override fun show(
        player: Player,
        request: NativeDialogRequest,
        onSubmit: (String) -> Unit,
        onCancel: () -> Unit,
    ): Boolean {
        callbacks[player.uniqueId] = Callbacks(onSubmit, onCancel)
        return try {
            backend.show(player, request)
            true
        } catch (error: ReflectiveOperationException) {
            callbacks.remove(player.uniqueId)
            logger.log(Level.WARNING, "無法開啟原生 Invite Code Dialog，將退回聊天輸入。", error)
            false
        } catch (error: LinkageError) {
            callbacks.remove(player.uniqueId)
            logger.log(Level.WARNING, "原生 Invite Code Dialog API 不相容，將退回聊天輸入。", error)
            false
        }
    }

    override fun cancel(player: Player) {
        callbacks.remove(player.uniqueId) ?: return
        try {
            backend.close(player)
        } catch (error: ReflectiveOperationException) {
            logger.log(Level.FINE, "無法關閉 Invite Code Dialog。", error)
        } catch (error: LinkageError) {
            logger.log(Level.FINE, "關閉 Invite Code Dialog 時 API 不相容。", error)
        }
    }

    override fun disconnect(playerId: UUID) {
        callbacks.remove(playerId)
    }

    override fun close() {
        callbacks.clear()
    }

    private fun handle(response: DialogResponse) {
        val callback = callbacks.remove(response.playerId) ?: return
        when (response) {
            is DialogResponse.Submitted -> callback.onSubmit(response.value.orEmpty())
            is DialogResponse.Cancelled -> callback.onCancel()
        }
    }

    companion object {
        fun create(plugin: Plugin, logger: Logger): NativeDialogInput {
            val backend = PaperDialogBackend.create(plugin, logger)
                ?: SpigotDialogBackend.create(plugin, logger)
                ?: return UnavailableNativeDialogInput
            return ReflectiveNativeDialogInput(backend, logger)
        }
    }
}

private abstract class RegisteredDialogBackend(
    protected val plugin: Plugin,
    private val eventClass: Class<out Event>,
) : DialogBackend {
    private var registered = false

    override fun register(listener: (DialogResponse) -> Unit) {
        check(!registered) { "Dialog listener is already registered." }
        registered = true
        plugin.server.pluginManager.registerEvent(
            eventClass,
            object : Listener {},
            EventPriority.NORMAL,
            EventExecutor { _, event -> readResponse(event)?.let(listener) },
            plugin,
        )
    }

    protected abstract fun readResponse(event: Event): DialogResponse?
}

private class PaperDialogBackend private constructor(
    plugin: Plugin,
    eventClass: Class<out Event>,
    private val eventIdentifier: Method,
    private val eventConnection: Method,
    private val eventResponse: Method,
    private val gameConnectionClass: Class<*>,
    private val connectionPlayer: Method,
    private val responseText: Method,
    private val componentText: Method,
    private val keyFactory: Method,
    private val dialogInputText: Method,
    private val textWidth: Method,
    private val textMaximumLength: Method,
    private val textBuild: Method,
    private val dialogBaseBuilder: Method,
    private val baseInputs: Method,
    private val baseCanClose: Method,
    private val baseBuild: Method,
    private val customClick: Method,
    private val actionButtonBuilder: Method,
    private val buttonAction: Method,
    private val buttonBuild: Method,
    private val confirmationType: Method,
    private val dialogCreate: Method,
    private val registryFactoryEmpty: Method,
    private val dialogBuilderBase: Method,
    private val dialogBuilderType: Method,
    private val showDialog: Method,
    private val closeDialog: Method,
) : RegisteredDialogBackend(plugin, eventClass) {
    override val provider: String = "PAPER"

    override fun show(player: Player, request: NativeDialogRequest) {
        val title = componentText.invoke(null, request.title)
        val label = componentText.invoke(null, request.label)
        val inputBuilder = dialogInputText.invoke(null, INPUT_KEY, label)
        textWidth.invoke(inputBuilder, 300)
        textMaximumLength.invoke(inputBuilder, request.maximumLength)
        val input = textBuild.invoke(inputBuilder)

        val baseBuilder = dialogBaseBuilder.invoke(null, title)
        baseInputs.invoke(baseBuilder, Collections.singletonList(input))
        baseCanClose.invoke(baseBuilder, true)
        val base = baseBuild.invoke(baseBuilder)
        val type = confirmationType.invoke(
            null,
            button(request.confirm, SUBMIT_ACTION),
            button(request.cancel, CANCEL_ACTION),
        )
        val dialog = dialogCreate.invoke(null, Consumer<Any> { factory ->
            val builder = registryFactoryEmpty.invoke(factory)
            dialogBuilderBase.invoke(builder, base)
            dialogBuilderType.invoke(builder, type)
        })
        showDialog.invoke(player, dialog)
    }

    override fun close(player: Player) {
        closeDialog.invoke(player)
    }

    private fun button(label: String, actionId: String): Any {
        val component = componentText.invoke(null, label)
        val key = keyFactory.invoke(null, actionId)
        val action = customClick.invoke(null, key, null)
        val builder = actionButtonBuilder.invoke(null, component)
        buttonAction.invoke(builder, action)
        return buttonBuild.invoke(builder)
    }

    override fun readResponse(event: Event): DialogResponse? {
        val identifier = eventIdentifier.invoke(event).toString()
        if (identifier != SUBMIT_ACTION && identifier != CANCEL_ACTION) return null
        val connection = eventConnection.invoke(event)
        if (!gameConnectionClass.isInstance(connection)) return null
        val player = connectionPlayer.invoke(connection) as? Player ?: return null
        if (identifier == CANCEL_ACTION) return DialogResponse.Cancelled(player.uniqueId)
        val response = eventResponse.invoke(event) ?: return DialogResponse.Submitted(player.uniqueId, null)
        return DialogResponse.Submitted(player.uniqueId, responseText.invoke(response, INPUT_KEY) as? String)
    }

    companion object {
        fun create(plugin: Plugin, logger: Logger): DialogBackend? = try {
            val event = load("io.papermc.paper.event.player.PlayerCustomClickEvent").asSubclass(Event::class.java)
            val component = load("net.kyori.adventure.text.Component")
            val key = load("net.kyori.adventure.key.Key")
            val binaryTag = load("net.kyori.adventure.nbt.api.BinaryTagHolder")
            val dialogInput = load("io.papermc.paper.registry.data.dialog.input.DialogInput")
            val textBuilder = load("io.papermc.paper.registry.data.dialog.input.TextDialogInput\$Builder")
            val dialogBase = load("io.papermc.paper.registry.data.dialog.DialogBase")
            val baseBuilder = load("io.papermc.paper.registry.data.dialog.DialogBase\$Builder")
            val dialogAction = load("io.papermc.paper.registry.data.dialog.action.DialogAction")
            val actionButton = load("io.papermc.paper.registry.data.dialog.ActionButton")
            val buttonBuilder = load("io.papermc.paper.registry.data.dialog.ActionButton\$Builder")
            val dialogType = load("io.papermc.paper.registry.data.dialog.type.DialogType")
            val dialog = load("io.papermc.paper.dialog.Dialog")
            val registryFactory = load("io.papermc.paper.registry.RegistryBuilderFactory")
            val dialogBuilder = load("io.papermc.paper.registry.data.dialog.DialogRegistryEntry\$Builder")
            val dialogLike = load("net.kyori.adventure.dialog.DialogLike")
            val audience = load("net.kyori.adventure.audience.Audience")
            val responseView = load("io.papermc.paper.dialog.DialogResponseView")
            val gameConnection = load("io.papermc.paper.connection.PlayerGameConnection")
            PaperDialogBackend(
                plugin = plugin,
                eventClass = event,
                eventIdentifier = event.getMethod("getIdentifier"),
                eventConnection = event.getMethod("getCommonConnection"),
                eventResponse = event.getMethod("getDialogResponseView"),
                gameConnectionClass = gameConnection,
                connectionPlayer = gameConnection.getMethod("getPlayer"),
                responseText = responseView.getMethod("getText", String::class.java),
                componentText = component.getMethod("text", String::class.java),
                keyFactory = key.getMethod("key", String::class.java),
                dialogInputText = dialogInput.getMethod("text", String::class.java, component),
                textWidth = textBuilder.getMethod("width", Int::class.javaPrimitiveType!!),
                textMaximumLength = textBuilder.getMethod("maxLength", Int::class.javaPrimitiveType!!),
                textBuild = textBuilder.getMethod("build"),
                dialogBaseBuilder = dialogBase.getMethod("builder", component),
                baseInputs = baseBuilder.getMethod("inputs", List::class.java),
                baseCanClose = baseBuilder.getMethod("canCloseWithEscape", Boolean::class.javaPrimitiveType!!),
                baseBuild = baseBuilder.getMethod("build"),
                customClick = dialogAction.getMethod("customClick", key, binaryTag),
                actionButtonBuilder = actionButton.getMethod("builder", component),
                buttonAction = buttonBuilder.getMethod("action", dialogAction),
                buttonBuild = buttonBuilder.getMethod("build"),
                confirmationType = dialogType.getMethod("confirmation", actionButton, actionButton),
                dialogCreate = dialog.getMethod("create", Consumer::class.java),
                registryFactoryEmpty = registryFactory.getMethod("empty"),
                dialogBuilderBase = dialogBuilder.getMethod("base", dialogBase),
                dialogBuilderType = dialogBuilder.getMethod("type", dialogType),
                showDialog = audience.getMethod("showDialog", dialogLike),
                closeDialog = audience.getMethod("closeDialog"),
            )
        } catch (_: ClassNotFoundException) {
            null
        } catch (error: ReflectiveOperationException) {
            logger.log(Level.WARNING, "偵測到 Paper Dialog API，但無法建立相容 adapter。", error)
            null
        } catch (error: LinkageError) {
            logger.log(Level.WARNING, "Paper Dialog API 無法連結。", error)
            null
        }
    }
}

private class SpigotDialogBackend private constructor(
    plugin: Plugin,
    eventClass: Class<out Event>,
    private val eventId: Method,
    private val eventData: Method,
    private val eventPlayer: Method,
    private val jsonAsObject: Method,
    private val jsonObjectGet: Method,
    private val jsonAsString: Method,
    private val textComponentConstructor: Constructor<*>,
    private val textInputConstructor: Constructor<*>,
    private val textWidth: Method,
    private val textMaximumLength: Method,
    private val dialogBaseConstructor: Constructor<*>,
    private val baseInputs: Method,
    private val baseCanClose: Method,
    private val actionConstructor: Constructor<*>,
    private val buttonConstructor: Constructor<*>,
    private val confirmationConstructor: Constructor<*>,
    private val confirmationYes: Method,
    private val confirmationNo: Method,
    private val showDialog: Method,
    private val clearDialog: Method,
) : RegisteredDialogBackend(plugin, eventClass) {
    override val provider: String = "SPIGOT"

    override fun show(player: Player, request: NativeDialogRequest) {
        val input = textInputConstructor.newInstance(INPUT_KEY, text(request.label))
        textWidth.invoke(input, 300)
        textMaximumLength.invoke(input, request.maximumLength)
        val base = dialogBaseConstructor.newInstance(text(request.title))
        baseInputs.invoke(base, Collections.singletonList(input))
        baseCanClose.invoke(base, true)
        val dialog = confirmationConstructor.newInstance(base)
        confirmationYes.invoke(dialog, button(request.confirm, SUBMIT_ACTION))
        confirmationNo.invoke(dialog, button(request.cancel, CANCEL_ACTION))
        showDialog.invoke(player, dialog)
    }

    override fun close(player: Player) {
        clearDialog.invoke(player)
    }

    private fun text(value: String): Any = textComponentConstructor.newInstance(value)

    private fun button(label: String, actionId: String): Any =
        buttonConstructor.newInstance(text(label), actionConstructor.newInstance(actionId))

    override fun readResponse(event: Event): DialogResponse? {
        val identifier = eventId.invoke(event).toString()
        if (identifier != SUBMIT_ACTION && identifier != CANCEL_ACTION) return null
        val player = eventPlayer.invoke(event) as? Player ?: return null
        if (identifier == CANCEL_ACTION) return DialogResponse.Cancelled(player.uniqueId)
        val data = eventData.invoke(event) ?: return DialogResponse.Submitted(player.uniqueId, null)
        val jsonObject = jsonAsObject.invoke(data)
        val element = jsonObjectGet.invoke(jsonObject, INPUT_KEY)
            ?: return DialogResponse.Submitted(player.uniqueId, null)
        return DialogResponse.Submitted(player.uniqueId, jsonAsString.invoke(element) as? String)
    }

    companion object {
        fun create(plugin: Plugin, logger: Logger): DialogBackend? = try {
            val event = load("org.bukkit.event.player.PlayerCustomClickEvent").asSubclass(Event::class.java)
            val baseComponent = load("net.md_5.bungee.api.chat.BaseComponent")
            val textComponent = load("net.md_5.bungee.api.chat.TextComponent")
            val dialogInput = load("net.md_5.bungee.api.dialog.input.DialogInput")
            val textInput = load("net.md_5.bungee.api.dialog.input.TextInput")
            val dialogBase = load("net.md_5.bungee.api.dialog.DialogBase")
            val action = load("net.md_5.bungee.api.dialog.action.Action")
            val customAction = load("net.md_5.bungee.api.dialog.action.CustomClickAction")
            val button = load("net.md_5.bungee.api.dialog.action.ActionButton")
            val dialog = load("net.md_5.bungee.api.dialog.Dialog")
            val confirmation = load("net.md_5.bungee.api.dialog.ConfirmationDialog")
            val jsonElement = load("com.google.gson.JsonElement")
            val jsonObject = load("com.google.gson.JsonObject")
            SpigotDialogBackend(
                plugin = plugin,
                eventClass = event,
                eventId = event.getMethod("getId"),
                eventData = event.getMethod("getData"),
                eventPlayer = event.getMethod("getPlayer"),
                jsonAsObject = jsonElement.getMethod("getAsJsonObject"),
                jsonObjectGet = jsonObject.getMethod("get", String::class.java),
                jsonAsString = jsonElement.getMethod("getAsString"),
                textComponentConstructor = textComponent.getConstructor(String::class.java),
                textInputConstructor = textInput.getConstructor(String::class.java, baseComponent),
                textWidth = textInput.getMethod("width", Int::class.javaObjectType),
                textMaximumLength = textInput.getMethod("maxLength", Int::class.javaObjectType),
                dialogBaseConstructor = dialogBase.getConstructor(baseComponent),
                baseInputs = dialogBase.getMethod("inputs", List::class.java),
                baseCanClose = dialogBase.getMethod("canCloseWithEscape", Boolean::class.javaObjectType),
                actionConstructor = customAction.getConstructor(String::class.java),
                buttonConstructor = button.getConstructor(baseComponent, action),
                confirmationConstructor = confirmation.getConstructor(dialogBase),
                confirmationYes = confirmation.getMethod("yes", button),
                confirmationNo = confirmation.getMethod("no", button),
                showDialog = Player::class.java.getMethod("showDialog", dialog),
                clearDialog = Player::class.java.getMethod("clearDialog"),
            )
        } catch (_: ClassNotFoundException) {
            null
        } catch (error: ReflectiveOperationException) {
            logger.log(Level.WARNING, "偵測到 Spigot Dialog API，但無法建立相容 adapter。", error)
            null
        } catch (error: LinkageError) {
            logger.log(Level.WARNING, "Spigot Dialog API 無法連結。", error)
            null
        }
    }
}

private fun load(name: String): Class<*> = Class.forName(name)

private const val INPUT_KEY = "invite_code"
private const val SUBMIT_ACTION = "invitecode:claim_dialog_submit"
private const val CANCEL_ACTION = "invitecode:claim_dialog_cancel"
