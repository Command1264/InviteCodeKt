package io.github.command1264.invitecode.platform.modern

import io.github.command1264.invitecode.platform.bukkit.NativeDialogInput
import io.github.command1264.invitecode.platform.bukkit.NativeDialogConfirmationRequest
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
    data class Confirmed(override val playerId: UUID) : DialogResponse()
    data class Declined(override val playerId: UUID) : DialogResponse()
}

internal interface DialogBackend {
    val provider: String
    fun register(listener: (DialogResponse) -> Unit)
    fun show(player: Player, request: NativeDialogRequest)
    fun showConfirmation(player: Player, request: NativeDialogConfirmationRequest)
    fun close(player: Player)
}

internal class ReflectiveNativeDialogInput(
    private val backend: DialogBackend,
    private val logger: Logger,
) : NativeDialogInput {
    private sealed class Callbacks {
        data class Input(val onSubmit: (String) -> Unit, val onCancel: () -> Unit) : Callbacks()
        data class Confirmation(val onConfirm: () -> Unit, val onDecline: () -> Unit) : Callbacks()
    }

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
        callbacks[player.uniqueId] = Callbacks.Input(onSubmit, onCancel)
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

    override fun showConfirmation(
        player: Player,
        request: NativeDialogConfirmationRequest,
        onConfirm: () -> Unit,
        onDecline: () -> Unit,
    ): Boolean {
        callbacks[player.uniqueId] = Callbacks.Confirmation(onConfirm, onDecline)
        return try {
            backend.showConfirmation(player, request)
            true
        } catch (error: ReflectiveOperationException) {
            callbacks.remove(player.uniqueId)
            logger.log(Level.WARNING, "無法開啟原生 Claim Confirmation Dialog，將退回聊天確認。", error)
            false
        } catch (error: LinkageError) {
            callbacks.remove(player.uniqueId)
            logger.log(Level.WARNING, "原生 Claim Confirmation Dialog API 不相容，將退回聊天確認。", error)
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
        val callback = callbacks[response.playerId] ?: return
        when {
            callback is Callbacks.Input && response is DialogResponse.Submitted -> {
                if (callbacks.remove(response.playerId, callback)) callback.onSubmit(response.value.orEmpty())
            }
            callback is Callbacks.Input && response is DialogResponse.Cancelled -> {
                if (callbacks.remove(response.playerId, callback)) callback.onCancel()
            }
            callback is Callbacks.Confirmation && response is DialogResponse.Confirmed -> {
                if (callbacks.remove(response.playerId, callback)) callback.onConfirm()
            }
            callback is Callbacks.Confirmation && response is DialogResponse.Declined -> {
                if (callbacks.remove(response.playerId, callback)) callback.onDecline()
            }
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
    private val componentColoredText: Method,
    private val textColorRgb: Method,
    private val componentAppend: Method,
    private val plainMessage: Method,
    private val keyFactory: Method,
    private val dialogInputText: Method,
    private val textWidth: Method,
    private val textMaximumLength: Method,
    private val textBuild: Method,
    private val dialogBaseBuilder: Method,
    private val baseInputs: Method,
    private val baseBody: Method,
    private val baseCanClose: Method,
    private val baseBuild: Method,
    private val customClick: Method,
    private val actionButtonBuilder: Method,
    private val buttonAction: Method,
    private val buttonTooltip: Method,
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

    override fun showConfirmation(player: Player, request: NativeDialogConfirmationRequest) {
        val baseBuilder = dialogBaseBuilder.invoke(null, componentText.invoke(null, request.title))
        val codeText = request.codeRgb?.let { rgb ->
            componentColoredText.invoke(null, request.code, textColorRgb.invoke(null, rgb))
        } ?: componentText.invoke(null, request.code)
        val codeLine = componentAppend.invoke(componentText.invoke(null, request.codeLabel), codeText)
        baseBody.invoke(baseBuilder, listOf(
            plainMessage.invoke(null, componentText.invoke(null, request.inviterLine)),
            plainMessage.invoke(null, codeLine),
            plainMessage.invoke(null, componentText.invoke(null, request.question)),
            plainMessage.invoke(null, componentText.invoke(null, request.timeoutPrompt)),
        ))
        baseCanClose.invoke(baseBuilder, true)
        val base = baseBuild.invoke(baseBuilder)
        val type = confirmationType.invoke(
            null,
            button(request.agree, CONFIRM_ACTION, 0x55FF55, request.agreeHint),
            button(request.disagree, DECLINE_ACTION, 0xFF5555, request.disagreeHint),
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

    private fun button(label: String, actionId: String, rgb: Int? = null, tooltip: String? = null): Any {
        val component = rgb?.let { componentColoredText.invoke(null, label, textColorRgb.invoke(null, it)) }
            ?: componentText.invoke(null, label)
        val key = keyFactory.invoke(null, actionId)
        val action = customClick.invoke(null, key, null)
        val builder = actionButtonBuilder.invoke(null, component)
        buttonAction.invoke(builder, action)
        if (tooltip != null) buttonTooltip.invoke(builder, componentText.invoke(null, tooltip))
        return buttonBuild.invoke(builder)
    }

    override fun readResponse(event: Event): DialogResponse? {
        val identifier = eventIdentifier.invoke(event).toString()
        if (identifier !in setOf(SUBMIT_ACTION, CANCEL_ACTION, CONFIRM_ACTION, DECLINE_ACTION)) return null
        val connection = eventConnection.invoke(event)
        if (!gameConnectionClass.isInstance(connection)) return null
        val player = connectionPlayer.invoke(connection) as? Player ?: return null
        if (identifier == CANCEL_ACTION) return DialogResponse.Cancelled(player.uniqueId)
        if (identifier == CONFIRM_ACTION) return DialogResponse.Confirmed(player.uniqueId)
        if (identifier == DECLINE_ACTION) return DialogResponse.Declined(player.uniqueId)
        val response = eventResponse.invoke(event) ?: return DialogResponse.Submitted(player.uniqueId, null)
        return DialogResponse.Submitted(player.uniqueId, responseText.invoke(response, INPUT_KEY) as? String)
    }

    companion object {
        fun create(plugin: Plugin, logger: Logger): DialogBackend? = try {
            val event = load("io.papermc.paper.event.player.PlayerCustomClickEvent").asSubclass(Event::class.java)
            val component = load("net.kyori.adventure.text.Component")
            val textColor = load("net.kyori.adventure.text.format.TextColor")
            val dialogBody = load("io.papermc.paper.registry.data.dialog.body.DialogBody")
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
                componentColoredText = component.getMethod("text", String::class.java, textColor),
                textColorRgb = textColor.getMethod("color", Int::class.javaPrimitiveType!!),
                componentAppend = component.getMethod("append", component),
                plainMessage = dialogBody.getMethod("plainMessage", component),
                keyFactory = key.getMethod("key", String::class.java),
                dialogInputText = dialogInput.getMethod("text", String::class.java, component),
                textWidth = textBuilder.getMethod("width", Int::class.javaPrimitiveType!!),
                textMaximumLength = textBuilder.getMethod("maxLength", Int::class.javaPrimitiveType!!),
                textBuild = textBuilder.getMethod("build"),
                dialogBaseBuilder = dialogBase.getMethod("builder", component),
                baseInputs = baseBuilder.getMethod("inputs", List::class.java),
                baseBody = baseBuilder.getMethod("body", List::class.java),
                baseCanClose = baseBuilder.getMethod("canCloseWithEscape", Boolean::class.javaPrimitiveType!!),
                baseBuild = baseBuilder.getMethod("build"),
                customClick = dialogAction.getMethod("customClick", key, binaryTag),
                actionButtonBuilder = actionButton.getMethod("builder", component),
                buttonAction = buttonBuilder.getMethod("action", dialogAction),
                buttonTooltip = buttonBuilder.getMethod("tooltip", component),
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
    private val setColor: Method,
    private val addExtra: Method,
    private val chatColorOf: Method,
    private val greenColor: Any,
    private val redColor: Any,
    private val plainMessageConstructor: Constructor<*>,
    private val textInputConstructor: Constructor<*>,
    private val textWidth: Method,
    private val textMaximumLength: Method,
    private val dialogBaseConstructor: Constructor<*>,
    private val baseInputs: Method,
    private val baseBody: Method,
    private val baseCanClose: Method,
    private val actionConstructor: Constructor<*>,
    private val buttonConstructor: Constructor<*>,
    private val buttonTooltip: Method,
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

    override fun showConfirmation(player: Player, request: NativeDialogConfirmationRequest) {
        val base = dialogBaseConstructor.newInstance(text(request.title))
        val codeLine = text(request.codeLabel)
        val codeText = text(request.code)
        request.codeRgb?.let { rgb ->
            setColor.invoke(codeText, chatColorOf.invoke(null, "#%06X".format(rgb)))
        }
        addExtra.invoke(codeLine, codeText)
        baseBody.invoke(base, listOf(
            plainMessageConstructor.newInstance(text(request.inviterLine)),
            plainMessageConstructor.newInstance(codeLine),
            plainMessageConstructor.newInstance(text(request.question)),
            plainMessageConstructor.newInstance(text(request.timeoutPrompt)),
        ))
        baseCanClose.invoke(base, true)
        val dialog = confirmationConstructor.newInstance(base)
        confirmationYes.invoke(dialog, button(request.agree, CONFIRM_ACTION, greenColor, request.agreeHint))
        confirmationNo.invoke(dialog, button(request.disagree, DECLINE_ACTION, redColor, request.disagreeHint))
        showDialog.invoke(player, dialog)
    }

    override fun close(player: Player) {
        clearDialog.invoke(player)
    }

    private fun text(value: String): Any = textComponentConstructor.newInstance(value)

    private fun button(label: String, actionId: String, color: Any? = null, tooltip: String? = null): Any {
        val component = text(label)
        if (color != null) setColor.invoke(component, color)
        val button = buttonConstructor.newInstance(component, actionConstructor.newInstance(actionId))
        if (tooltip != null) buttonTooltip.invoke(button, text(tooltip))
        return button
    }

    override fun readResponse(event: Event): DialogResponse? {
        val identifier = eventId.invoke(event).toString()
        if (identifier !in setOf(SUBMIT_ACTION, CANCEL_ACTION, CONFIRM_ACTION, DECLINE_ACTION)) return null
        val player = eventPlayer.invoke(event) as? Player ?: return null
        if (identifier == CANCEL_ACTION) return DialogResponse.Cancelled(player.uniqueId)
        if (identifier == CONFIRM_ACTION) return DialogResponse.Confirmed(player.uniqueId)
        if (identifier == DECLINE_ACTION) return DialogResponse.Declined(player.uniqueId)
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
            val chatColor = load("net.md_5.bungee.api.ChatColor")
            val textComponent = load("net.md_5.bungee.api.chat.TextComponent")
            val plainMessage = load("net.md_5.bungee.api.dialog.body.PlainMessageBody")
            val dialogInput = load("net.md_5.bungee.api.dialog.input.DialogInput")
            val textInput = load("net.md_5.bungee.api.dialog.input.TextInput")
            val dialogBase = load("net.md_5.bungee.api.dialog.DialogBase")
            val action = load("net.md_5.bungee.api.dialog.action.Action")
            val customAction = load("net.md_5.bungee.api.dialog.action.CustomClickAction")
            val button = load("net.md_5.bungee.api.dialog.action.ActionButton")
            val dialog = load("net.md_5.bungee.api.dialog.Dialog")
            val confirmation = load("net.md_5.bungee.api.dialog.ConfirmationDialog")
            // Shadow 8.3 relocates matching string constants. Construct the server API name at
            // runtime so this reflective adapter still uses the server's Gson classes.
            val serverJsonPackage = listOf("com", "google", "gson").joinToString(".")
            val jsonElement = load("$serverJsonPackage.JsonElement")
            val jsonObject = load("$serverJsonPackage.JsonObject")
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
                setColor = baseComponent.getMethod("setColor", chatColor),
                addExtra = baseComponent.getMethod("addExtra", baseComponent),
                chatColorOf = chatColor.getMethod("of", String::class.java),
                greenColor = chatColor.getField("GREEN").get(null),
                redColor = chatColor.getField("RED").get(null),
                plainMessageConstructor = plainMessage.getConstructor(baseComponent),
                textInputConstructor = textInput.getConstructor(String::class.java, baseComponent),
                textWidth = textInput.getMethod("width", Int::class.javaObjectType),
                textMaximumLength = textInput.getMethod("maxLength", Int::class.javaObjectType),
                dialogBaseConstructor = dialogBase.getConstructor(baseComponent),
                baseInputs = dialogBase.getMethod("inputs", List::class.java),
                baseBody = dialogBase.getMethod("body", List::class.java),
                baseCanClose = dialogBase.getMethod("canCloseWithEscape", Boolean::class.javaObjectType),
                actionConstructor = customAction.getConstructor(String::class.java),
                buttonConstructor = button.getConstructor(baseComponent, action),
                buttonTooltip = button.getMethod("tooltip", baseComponent),
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
private const val CONFIRM_ACTION = "invitecode:claim_confirmation_confirm"
private const val DECLINE_ACTION = "invitecode:claim_confirmation_decline"
