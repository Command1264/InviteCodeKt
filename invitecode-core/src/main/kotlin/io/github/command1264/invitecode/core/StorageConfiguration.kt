package io.github.command1264.invitecode.core

import java.time.Duration
import java.util.Locale

enum class StorageBackendType {
    SQLITE,
    MYSQL,
    JSON,
}

data class SqliteConfiguration(val file: String) {
    init {
        require(file.isNotBlank()) { "SQLite file must not be blank." }
        require(!file.startsWith('/') && !WINDOWS_ABSOLUTE.matches(file)) {
            "SQLite file must be relative to the plugin data directory."
        }
        val segments = file.replace('\\', '/').split('/')
        require(segments.none { it.isBlank() || it == "." || it == ".." }) {
            "SQLite file must not contain empty, current-directory, or parent-directory segments."
        }
    }

    companion object {
        private val WINDOWS_ABSOLUTE = Regex("^[A-Za-z]:.*")
    }
}

data class MysqlConfiguration(
    val host: String,
    val port: Int,
    val database: String,
    val username: String,
    val passwordReference: String,
    val connectionTimeout: Duration,
) {
    init {
        require(host.isNotBlank()) { "MySQL host must not be blank." }
        require(port in 1..65535) { "MySQL port must be between 1 and 65535." }
        require(database.isNotBlank()) { "MySQL database must not be blank." }
        require(username.isNotBlank()) { "MySQL username must not be blank." }
        require(passwordReference.isNotBlank()) { "MySQL password reference must not be blank." }
        require(!connectionTimeout.isZero && !connectionTimeout.isNegative) {
            "MySQL connection timeout must be greater than zero."
        }
    }
}

data class StorageConfiguration(
    val version: Int,
    val backend: StorageBackendType,
    val sqlite: SqliteConfiguration,
    val mysql: MysqlConfiguration,
) {
    fun requireSupportedBackend(): StorageConfiguration {
        if (backend != StorageBackendType.SQLITE) {
            throw UnsupportedStorageBackendException(backend)
        }
        return this
    }
}

class UnsupportedStorageBackendException(backend: StorageBackendType) : IllegalStateException(
    "Storage backend $backend is not implemented in this version; select SQLITE or install a version that supports it.",
)

object StorageConfigurationParser {
    const val CURRENT_VERSION = 1

    fun parse(value: Map<String, Any?>): StorageConfiguration {
        require(value.keys == ROOT_KEYS) {
            "Storage configuration must contain only ${ROOT_KEYS.sorted().joinToString()}."
        }
        val version = value.requiredInteger("config-version", "Storage configuration")
        require(version == CURRENT_VERSION) {
            "Storage config-version $version is unsupported; expected $CURRENT_VERSION."
        }
        val sqlite = value.requiredMap("sqlite", "Storage configuration")
        require(sqlite.keys == setOf("file")) { "sqlite must contain only file." }
        val mysql = value.requiredMap("mysql", "Storage configuration")
        require(mysql.keys == MYSQL_KEYS) {
            "mysql must contain only ${MYSQL_KEYS.sorted().joinToString()}."
        }
        return StorageConfiguration(
            version = version,
            backend = value.requiredEnum("backend", "Storage configuration"),
            sqlite = SqliteConfiguration(sqlite.requiredString("file", "sqlite")),
            mysql = MysqlConfiguration(
                host = mysql.requiredString("host", "mysql"),
                port = mysql.requiredInteger("port", "mysql"),
                database = mysql.requiredString("database", "mysql"),
                username = mysql.requiredString("username", "mysql"),
                passwordReference = mysql.requiredString("password", "mysql"),
                connectionTimeout = parsePositiveDuration(
                    mysql.requiredString("connection-timeout", "mysql"),
                    "mysql.connection-timeout",
                ),
            ),
        )
    }

    private fun parsePositiveDuration(raw: String, location: String): Duration {
        val duration = SHORT_DURATION.matchEntire(raw)?.let { match ->
            val amount = match.groupValues[1].toLongOrNull()
                ?: throw IllegalArgumentException("$location duration is outside the supported range.")
            when (match.groupValues[2]) {
                "ms" -> Duration.ofMillis(amount)
                "s" -> Duration.ofSeconds(amount)
                "m" -> Duration.ofMinutes(amount)
                "h" -> Duration.ofHours(amount)
                "d" -> Duration.ofDays(amount)
                else -> error("Unreachable duration unit")
            }
        } ?: try {
            Duration.parse(raw)
        } catch (error: Exception) {
            throw IllegalArgumentException("$location must be a duration such as 5s or PT5S.", error)
        }
        require(!duration.isZero && !duration.isNegative) { "$location must be greater than zero." }
        return duration
    }

    private fun Map<String, Any?>.requiredMap(name: String, location: String): Map<String, Any?> {
        val value = this[name]
        require(value is Map<*, *> && value.keys.all { it is String }) {
            "$location requires map $name with string keys."
        }
        @Suppress("UNCHECKED_CAST")
        return value as Map<String, Any?>
    }

    private fun Map<String, Any?>.requiredString(name: String, location: String): String {
        val value = this[name]
        require(value is String && value.isNotBlank()) { "$location requires non-blank string $name." }
        return value
    }

    private fun Map<String, Any?>.requiredInteger(name: String, location: String): Int =
        (this[name] as? Number)?.toString()?.toIntOrNull()
            ?: throw IllegalArgumentException("$location requires integer $name.")

    private inline fun <reified T : Enum<T>> Map<String, Any?>.requiredEnum(
        name: String,
        location: String,
    ): T {
        val raw = requiredString(name, location)
        return try {
            enumValueOf(raw.uppercase(Locale.ROOT))
        } catch (error: IllegalArgumentException) {
            throw IllegalArgumentException("$location has unsupported $name '$raw'.", error)
        }
    }

    private val ROOT_KEYS = setOf("config-version", "backend", "sqlite", "mysql")
    private val MYSQL_KEYS = setOf(
        "host",
        "port",
        "database",
        "username",
        "password",
        "connection-timeout",
    )
    private val SHORT_DURATION = Regex("^(\\d+)(ms|s|m|h|d)$")
}
