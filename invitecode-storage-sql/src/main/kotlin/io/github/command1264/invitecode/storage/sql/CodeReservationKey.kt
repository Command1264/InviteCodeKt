package io.github.command1264.invitecode.storage.sql

/** The caller owns the secret and must close/erase it when the storage session ends. */
class CodeReservationKey(val keyId: String, private val digest: (String) -> String) {
    init { require(Regex("[0-9a-f]{64}").matches(keyId)) { "Anti-abuse key ID is invalid." } }

    fun hash(normalizedCode: String): String = digest(normalizedCode).also {
        require(Regex("[0-9a-f]{64}").matches(it)) { "Reserved-code digest is invalid." }
    }
}
