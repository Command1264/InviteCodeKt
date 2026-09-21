package io.github.command1264.invitecode.core

import java.security.SecureRandom

fun interface InviteCodeGenerator {
    fun generate(): InviteCode
}

fun interface RandomBytes {
    fun nextBytes(destination: ByteArray)
}

class ColorInviteCodeGenerator(
    private val randomBytes: RandomBytes = SecureRandomBytes(),
) : InviteCodeGenerator {
    override fun generate(): InviteCode {
        val bytes = ByteArray(COLOR_BYTE_COUNT)
        randomBytes.nextBytes(bytes)
        val normalized = buildString(COLOR_CODE_LENGTH) {
            bytes.forEach { byte ->
                val unsigned = byte.toInt() and 0xFF
                append(HEX_DIGITS[unsigned shr 4])
                append(HEX_DIGITS[unsigned and 0x0F])
            }
        }
        return InviteCode.parse(normalized, InviteCodeFormats.colorHex)
    }

    private companion object {
        const val COLOR_BYTE_COUNT = 3
        const val COLOR_CODE_LENGTH = 6
        const val HEX_DIGITS = "0123456789ABCDEF"
    }
}

private class SecureRandomBytes(
    private val secureRandom: SecureRandom = SecureRandom(),
) : RandomBytes {
    override fun nextBytes(destination: ByteArray) {
        secureRandom.nextBytes(destination)
    }
}
