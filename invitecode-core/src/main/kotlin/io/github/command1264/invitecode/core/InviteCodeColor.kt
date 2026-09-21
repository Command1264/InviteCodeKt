package io.github.command1264.invitecode.core

data class InviteCodeColor(
    val red: Int,
    val green: Int,
    val blue: Int,
) {
    init {
        require(red in 0..255 && green in 0..255 && blue in 0..255) {
            "RGB channels must be between 0 and 255."
        }
    }

    val rgb: Int = (red shl 16) or (green shl 8) or blue

    fun nearestLegacyColor(): LegacyInviteCodeColor =
        LegacyInviteCodeColor.entries.minByOrNull { legacy ->
            squaredDistanceTo(legacy.color)
        } ?: error("At least one legacy color must be defined.")

    private fun squaredDistanceTo(other: InviteCodeColor): Int {
        val redDelta = red - other.red
        val greenDelta = green - other.green
        val blueDelta = blue - other.blue
        return redDelta * redDelta + greenDelta * greenDelta + blueDelta * blueDelta
    }

    companion object {
        fun fromRgb(rgb: Int): InviteCodeColor {
            require(rgb in 0x000000..0xFFFFFF) { "RGB value must be between 0x000000 and 0xFFFFFF." }
            return InviteCodeColor(
                red = rgb shr 16 and 0xFF,
                green = rgb shr 8 and 0xFF,
                blue = rgb and 0xFF,
            )
        }
    }
}

enum class LegacyInviteCodeColor(
    val code: Char,
    val color: InviteCodeColor,
) {
    BLACK('0', InviteCodeColor.fromRgb(0x000000)),
    DARK_BLUE('1', InviteCodeColor.fromRgb(0x0000AA)),
    DARK_GREEN('2', InviteCodeColor.fromRgb(0x00AA00)),
    DARK_AQUA('3', InviteCodeColor.fromRgb(0x00AAAA)),
    DARK_RED('4', InviteCodeColor.fromRgb(0xAA0000)),
    DARK_PURPLE('5', InviteCodeColor.fromRgb(0xAA00AA)),
    GOLD('6', InviteCodeColor.fromRgb(0xFFAA00)),
    GRAY('7', InviteCodeColor.fromRgb(0xAAAAAA)),
    DARK_GRAY('8', InviteCodeColor.fromRgb(0x555555)),
    BLUE('9', InviteCodeColor.fromRgb(0x5555FF)),
    GREEN('a', InviteCodeColor.fromRgb(0x55FF55)),
    AQUA('b', InviteCodeColor.fromRgb(0x55FFFF)),
    RED('c', InviteCodeColor.fromRgb(0xFF5555)),
    LIGHT_PURPLE('d', InviteCodeColor.fromRgb(0xFF55FF)),
    YELLOW('e', InviteCodeColor.fromRgb(0xFFFF55)),
    WHITE('f', InviteCodeColor.fromRgb(0xFFFFFF)),
}
