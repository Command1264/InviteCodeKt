package io.github.command1264.invitecode.platform.bukkit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64

class NetworkAddressHasherTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `generated secret remains stable across reload without exposing the address`() {
        val path = temporaryDirectory.resolve("anti-abuse.key")
        val address = byteArrayOf(192.toByte(), 0, 2, 10)
        val first = NetworkAddressHasher.loadOrCreate(path).use { it.keyId to it.hash(address) }
        val second = NetworkAddressHasher.loadExisting(path).use { it.keyId to it.hash(address) }

        assertEquals(first, second)
        assertEquals(64, first.first.length)
        assertEquals(64, first.second.length)
        assertEquals(32, Files.size(path))
        assertNotEquals(address.toList(), Files.readAllBytes(path).toList())
    }

    @Test
    fun `IPv4 and IPv4-mapped IPv6 addresses have the same digest`() {
        val hasher = NetworkAddressHasher.loadOrCreate(temporaryDirectory.resolve("anti-abuse.key"))
        val ipv4 = byteArrayOf(203.toByte(), 0, 113, 7)
        val mapped = byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -1, -1) + ipv4

        hasher.use {
            assertEquals(it.hash(ipv4), it.hash(mapped))
        }
    }

    @Test
    fun `shared Base64 secret yields the same digest and rejects invalid length`() {
        val secret = Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() })
        val address = byteArrayOf(192.toByte(), 0, 2, 10)
        val first = NetworkAddressHasher.fromBase64(secret).use { it.keyId to it.hash(address) }
        val second = NetworkAddressHasher.fromBase64(secret).use { it.keyId to it.hash(address) }
        assertEquals(first, second)
        assertThrows<IllegalArgumentException> { NetworkAddressHasher.fromBase64("not-base64") }
        assertThrows<IllegalArgumentException> {
            NetworkAddressHasher.fromBase64(Base64.getEncoder().encodeToString(ByteArray(16)))
        }
    }
}
