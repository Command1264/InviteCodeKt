package io.github.command1264.invitecode.plugin

import io.github.command1264.invitecode.core.InviteCode
import io.github.command1264.invitecode.platform.bukkit.*

/** Assembles user-visible layouts; none of these fragments are command or clipboard payloads. */
internal class PlayerMessageLayouts(
    private val messages: LocalizedMessages,
    private val locale: String?,
    private val audience: MessageAudience,
) {
    private val presenter = MessagePresenter(messages)

    fun fragment(key: String, variables: Map<String, String> = emptyMap()) =
        presenter.render(locale, key, variables, audience, PrefixPlacement.NONE)

    fun code(code: InviteCode): PresentedMessage = colorCode(presenter.render(
        locale, "command.code.value", mapOf("code" to code.displayValue()), audience,
    ), code)

    fun status(eligible: Boolean, invitation: String, pendingRewards: Int, reasons: List<String>): PresentedMessage {
        val summary = presenter.render(locale, "command.status.summary", mapOf(
            "eligible" to fragment(if (eligible) "value.yes" else "value.no").plainText,
            "invitation" to fragment("value.invitation.$invitation").plainText,
            "pending_rewards" to pendingRewards.toString(),
        ), audience)
        if (eligible || reasons.isEmpty()) return summary
        val reason = fragment("command.status.ineligible-reason", mapOf(
            "reasons" to reasons.joinToString(fragment("value.reason-separator").plainText),
        ))
        return PresentedMessage(summary.segments + MessageSegment("\n") + reason.segments)
    }

    fun chatConfirmation(inviter: String, code: InviteCode, seconds: Long) = colorCode(presenter.render(
        locale, "command.claim.confirmation",
        mapOf("inviter" to inviter, "code" to code.displayValue(), "seconds" to seconds.toString()),
        audience, PrefixPlacement.OWN_LINE,
    ), code)

    fun inputDialog(): NativeDialogRequest {
        val prefix = fragment("presentation.prefix")
        val heading = fragment("command.claim.dialog-title")
        val label = fragment("command.claim.dialog-label")
        val confirm = fragment("command.claim.dialog-confirm")
        val cancel = fragment("command.claim.dialog-cancel")
        return NativeDialogRequest(
            title = if (prefix.plainText.isEmpty()) heading.plainText else prefix.plainText,
            label = label.plainText, confirm = confirm.plainText, cancel = cancel.plainText, maximumLength = 64,
            presentation = NativeDialogPresentation(
                title = if (prefix.plainText.isEmpty()) heading else prefix,
                body = if (prefix.plainText.isEmpty()) emptyList() else listOf(heading),
                confirm = confirm, cancel = cancel, inputLabel = label,
            ),
        )
    }

    fun confirmationDialog(inviter: String, code: InviteCode, seconds: Long): NativeDialogConfirmationRequest {
        val prefix = fragment("presentation.prefix")
        val heading = fragment("command.claim.confirmation-title")
        val inviterLine = fragment("command.claim.confirmation-inviter", mapOf("inviter" to inviter))
        val codeLabel = fragment("command.claim.confirmation-code-label")
        val codeLine = colorCode(PresentedMessage(codeLabel.segments + MessageSegment(
            code.displayValue(), variable = "code",
        )), code)
        val question = fragment("command.claim.confirmation-question")
        val prompt = fragment("command.claim.confirmation-prompt", mapOf("seconds" to seconds.toString()))
        val agree = fragment("command.claim.agree")
        val disagree = fragment("command.claim.disagree")
        val agreeHint = fragment("command.claim.agree-hover")
        val disagreeHint = fragment("command.claim.disagree-hover")
        val hasPrefix = prefix.plainText.isNotEmpty()
        val body = (if (hasPrefix) listOf(heading) else emptyList()) + listOf(inviterLine, codeLine, question, prompt)
        return NativeDialogConfirmationRequest(
            title = if (hasPrefix) prefix.plainText else heading.plainText,
            inviterLine = inviterLine.plainText, codeLabel = codeLabel.plainText,
            code = code.displayValue(), codeRgb = code.color?.rgb, question = question.plainText,
            timeoutPrompt = prompt.plainText, agree = agree.plainText, disagree = disagree.plainText,
            agreeHint = agreeHint.plainText, disagreeHint = disagreeHint.plainText,
            presentation = NativeDialogPresentation(
                title = if (hasPrefix) prefix else heading, body = body,
                confirm = agree, cancel = disagree, confirmHint = agreeHint, cancelHint = disagreeHint,
            ),
        )
    }

    private fun colorCode(message: PresentedMessage, code: InviteCode): PresentedMessage {
        val color = code.color ?: return message
        val rgb = if (audience == MessageAudience.RGB_PLAYER) color.rgb else color.nearestLegacyColor().color.rgb
        return PresentedMessage(message.segments.map { if (it.variable == "code") it.copy(color = rgb) else it })
    }
}
