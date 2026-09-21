package io.github.command1264.invitecode.core

interface InviteCodeFormat {
    fun normalize(raw: String): String

    fun requireValid(normalized: String)
}

object InviteCodeFormats {
    private val separators = Regex("[-\\s]")

    val alphanumeric: InviteCodeFormat = regexFormat(
        pattern = Regex("[A-Z0-9]{4,32}"),
        errorMessage = "Invite Code must contain 4 to 32 ASCII letters or digits.",
    )

    val colorHex: InviteCodeFormat = regexFormat(
        pattern = Regex("[0-9A-F]{6}"),
        errorMessage = "A Color Invite Code must contain exactly six hexadecimal characters.",
    )

    fun crockfordBase32(length: Int): InviteCodeFormat {
        require(length in 4..32) { "Crockford Base32 length must be between 4 and 32 characters." }
        val pattern = Regex("[0-9A-HJKMNP-TV-Z]{$length}")
        return object : InviteCodeFormat {
            override fun normalize(raw: String): String = commonNormalize(raw)
                .replace('O', '0')
                .replace('I', '1')
                .replace('L', '1')

            override fun requireValid(normalized: String) {
                require(pattern.matches(normalized)) {
                    "A Crockford Base32 Invite Code must contain exactly $length valid characters."
                }
            }
        }
    }

    fun custom(
        pattern: Regex,
        description: String,
    ): InviteCodeFormat {
        require(description.isNotBlank()) { "Custom Invite Code description must not be blank." }
        return regexFormat(
            pattern = pattern,
            errorMessage = "$description is invalid.",
        )
    }

    private fun regexFormat(
        pattern: Regex,
        errorMessage: String,
    ): InviteCodeFormat = object : InviteCodeFormat {
        override fun normalize(raw: String): String = commonNormalize(raw)

        override fun requireValid(normalized: String) {
            require(pattern.matches(normalized)) { errorMessage }
        }
    }

    internal fun commonNormalize(raw: String): String =
        raw.trim().uppercase().replace(separators, "")
}
