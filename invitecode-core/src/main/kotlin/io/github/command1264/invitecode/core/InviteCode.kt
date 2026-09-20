package io.github.command1264.invitecode.core

class InviteCode private constructor(val normalized: String) {
    val isColorInviteCode: Boolean
        get() = COLOR_CODE.matches(normalized)

    fun displayValue(): String = normalized

    companion object {
        private val SEPARATORS = Regex("[-\\s]")
        private val VALID_CODE = Regex("[A-Z0-9]{4,32}")
        private val COLOR_CODE = Regex("[0-9A-F]{6}")

        fun parse(raw: String): InviteCode {
            val normalized = raw.trim().uppercase().replace(SEPARATORS, "")
            require(VALID_CODE.matches(normalized)) {
                "Invite Code must contain 4 to 32 ASCII letters or digits."
            }
            return InviteCode(normalized)
        }
    }
}

enum class RewardActionStatus {
    PENDING,
    PROCESSING,
    DELIVERED,
    FAILED,
    UNCERTAIN,
    EXPIRED,
}
