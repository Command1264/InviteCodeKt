package io.github.command1264.invitecode.platform.bukkit

import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermission
import java.security.SecureRandom
import java.security.MessageDigest
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class NetworkAddressHasher private constructor(
    secret: ByteArray,
) : AutoCloseable {
    private val key = secret.copyOf()
    private val closed = AtomicBoolean(false)
    val keyId: String = MessageDigest.getInstance("SHA-256")
        .digest(key)
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    init {
        require(key.size == SECRET_SIZE) { "Network hash secret must contain exactly $SECRET_SIZE bytes." }
    }

    fun hash(addressBytes: ByteArray): String {
        check(!closed.get()) { "Network address hasher is closed." }
        require(addressBytes.size == IPV4_SIZE || addressBytes.size == IPV6_SIZE) {
            "Network address must be a 4-byte IPv4 or 16-byte IPv6 address."
        }
        val normalized = normalize(addressBytes)
        return try {
            val mac = Mac.getInstance(ALGORITHM)
            mac.init(SecretKeySpec(key, ALGORITHM))
            mac.doFinal(normalized).joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        } finally {
            normalized.fill(0)
        }
    }

    /** Domain-separated digest for permanently reserved Invite Codes; never hash a short code without a key. */
    fun hashReservedCode(normalizedCode: String): String {
        check(!closed.get()) { "Network address hasher is closed." }
        require(normalizedCode.isNotBlank()) { "Invite Code must not be blank." }
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(SecretKeySpec(key, ALGORITHM))
        val input = ("invitecode:reserved-code:v1:" + normalizedCode).toByteArray(StandardCharsets.UTF_8)
        return try {
            mac.doFinal(input).joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        } finally {
            input.fill(0)
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) key.fill(0)
    }

    companion object {
        private const val ALGORITHM = "HmacSHA256"
        private const val SECRET_SIZE = 32
        private const val IPV4_SIZE = 4
        private const val IPV6_SIZE = 16

        fun fromBase64(encoded: String): NetworkAddressHasher {
            val bytes = try {
                Base64.getDecoder().decode(encoded)
            } catch (error: IllegalArgumentException) {
                throw IllegalArgumentException("Network secret must be Base64 encoded.", error)
            }
            return try {
                NetworkAddressHasher(bytes)
            } finally {
                bytes.fill(0)
            }
        }

        fun loadExisting(path: Path): NetworkAddressHasher {
            require(Files.isRegularFile(path)) {
                "Network hash secret ${path.toAbsolutePath()} is missing\nrun the explicit storage migration."
            }
            val bytes = Files.readAllBytes(path)
            require(bytes.size == SECRET_SIZE) {
                "Network hash secret ${path.toAbsolutePath()} must contain exactly $SECRET_SIZE bytes."
            }
            return try {
                NetworkAddressHasher(bytes)
            } finally {
                bytes.fill(0)
            }
        }

        fun loadOrCreate(path: Path, random: SecureRandom = SecureRandom()): NetworkAddressHasher {
            if (Files.exists(path)) return loadExisting(path)
            path.toAbsolutePath().parent?.let(Files::createDirectories)
            val bytes = ByteArray(SECRET_SIZE).also(random::nextBytes)
            try {
                try {
                    Files.write(path, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
                    restrictPosixPermissions(path)
                } catch (_: FileAlreadyExistsException) {
                    return loadExisting(path)
                }
                return NetworkAddressHasher(bytes)
            } finally {
                bytes.fill(0)
            }
        }

        private fun normalize(addressBytes: ByteArray): ByteArray {
            val ipv4MappedPrefix = byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -1, -1)
            return if (
                addressBytes.size == IPV6_SIZE &&
                addressBytes.copyOfRange(0, ipv4MappedPrefix.size).contentEquals(ipv4MappedPrefix)
            ) {
                addressBytes.copyOfRange(ipv4MappedPrefix.size, IPV6_SIZE)
            } else {
                addressBytes.copyOf()
            }
        }

        private fun restrictPosixPermissions(path: Path) {
            runCatching {
                Files.setPosixFilePermissions(
                    path,
                    setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                )
            }
        }
    }
}
