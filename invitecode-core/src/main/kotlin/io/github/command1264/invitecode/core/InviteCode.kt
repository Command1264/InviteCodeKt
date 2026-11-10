package io.github.command1264.invitecode.core

class InviteCode private constructor(val normalized: String) {
    val isColorInviteCode: Boolean
        get() = COLOR_CODE.matches(normalized)

    val color: InviteCodeColor?
        get() = if (isColorInviteCode) {
            InviteCodeColor.fromRgb(normalized.toInt(radix = 16))
        } else {
            null
        }

    fun displayValue(): String = normalized

    override fun equals(other: Any?): Boolean =
        this === other || (other is InviteCode && normalized == other.normalized)

    override fun hashCode(): Int = normalized.hashCode()

    override fun toString(): String = normalized

    companion object {
        private val COLOR_CODE = Regex("[0-9A-F]{6}")

        fun parse(
            raw: String,
            format: InviteCodeFormat = InviteCodeFormats.alphanumeric,
        ): InviteCode {
            val normalized = format.normalize(raw)
            format.requireValid(normalized)
            return InviteCode(normalized)
        }

        /** Restores a canonical value previously accepted and stored by InviteCode. */
        fun fromNormalized(normalized: String): InviteCode {
            require(normalized.isNotBlank() && normalized == normalized.trim()) {
                "Stored Invite Code must be a non-blank canonical value."
            }
            return InviteCode(normalized)
        }
    }
}
