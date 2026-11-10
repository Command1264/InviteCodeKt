package io.github.command1264.invitecode.core

import java.time.Duration
import java.util.Locale

enum class StorageBackendType {
    SQLITE,
    MYSQL,
    JSON,
}

enum class MysqlTlsMode {
    VERIFY_IDENTITY,
    VERIFY_CA,
    REQUIRED,
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

data class JsonConfiguration(val file: String) {
    init {
        require(file.isNotBlank() && file.endsWith(".json", ignoreCase = true)) {
            "JSON file must be a non-blank .json path."
        }
        require(!file.startsWith('/') && !Regex("^[A-Za-z]:.*").matches(file)) {
            "JSON file must be relative to the plugin data directory."
        }
        require(file.replace('\\', '/').split('/').none { it.isBlank() || it == "." || it == ".." }) {
            "JSON file must not contain empty, current-directory, or parent-directory segments."
        }
    }
}

data class MysqlConfiguration(
    val host: String,
    val port: Int,
    val database: String,
    val username: String,
    val passwordSetting: String,
    val networkSecretReference: String,
    val connectionTimeout: Duration,
    val tlsMode: MysqlTlsMode = MysqlTlsMode.VERIFY_IDENTITY,
) {
    init {
        require(HOST.matches(host)) { "MySQL host must be a hostname or IPv4 address without URL parameters." }
        require(port in 1..65535) { "MySQL port must be between 1 and 65535." }
        require(DATABASE.matches(database)) { "MySQL database must contain only letters, digits and underscores." }
        require(username.isNotBlank()) { "MySQL username must not be blank." }
        require(passwordSetting.isNotBlank()) { "MySQL password must not be blank." }
        require(!passwordSetting.startsWith("${'$'}{") || ENV_REFERENCE.matches(passwordSetting)) {
            "MySQL password has an invalid environment variable reference."
        }
        require(ENV_REFERENCE.matches(networkSecretReference)) {
            "MySQL network secret must be an environment variable reference."
        }
        require(connectionTimeout.toMillis() in 250..60_000) {
            "MySQL connection timeout must be between 250ms and 60s."
        }
    }

    override fun toString(): String =
        "MysqlConfiguration(host=$host, port=$port, database=$database, username=$username, " +
            "password=<redacted>, networkSecretReference=$networkSecretReference, " +
            "connectionTimeout=$connectionTimeout, tlsMode=$tlsMode)"

    companion object {
        private val ENV_REFERENCE = Regex("\\$\\{[A-Za-z_][A-Za-z0-9_]*}")
        private val HOST = Regex("[A-Za-z0-9][A-Za-z0-9.-]*")
        private val DATABASE = Regex("[A-Za-z0-9_]+")
    }
}

data class StorageConfiguration(
    val version: Int,
    val backend: StorageBackendType,
    val sqlite: SqliteConfiguration,
    val mysql: MysqlConfiguration,
    val json: JsonConfiguration = JsonConfiguration("invitecode.json"),
)

object StorageConfigurationParser {
    const val CURRENT_VERSION = 1

    fun parse(value: Map<String, Any?>): StorageConfiguration {
        require(value.keys == ROOT_KEYS || value.keys == ROOT_KEYS + "json") {
            "Storage configuration must contain ${ROOT_KEYS.sorted().joinToString()} and optional json."
        }
        val version = value.requiredInteger("config-version", "Storage configuration")
        require(version == CURRENT_VERSION) {
            "Storage config-version $version is unsupported\nexpected $CURRENT_VERSION."
        }
        val sqlite = value.requiredMap("sqlite", "Storage configuration")
        require(sqlite.keys == setOf("file")) { "sqlite must contain only file." }
        val mysql = value.requiredMap("mysql", "Storage configuration")
        require(mysql.keys == MYSQL_KEYS) {
            "mysql must contain only ${MYSQL_KEYS.sorted().joinToString()}."
        }
        val json = if ("json" in value) {
            val map = value.requiredMap("json", "Storage configuration")
            require(map.keys == setOf("file")) { "json must contain only file." }
            JsonConfiguration(map.requiredString("file", "json"))
        } else JsonConfiguration("invitecode.json")
        return StorageConfiguration(
            version = version,
            backend = value.requiredEnum("backend", "Storage configuration"),
            sqlite = SqliteConfiguration(sqlite.requiredString("file", "sqlite")),
            mysql = MysqlConfiguration(
                host = mysql.requiredString("host", "mysql"),
                port = mysql.requiredInteger("port", "mysql"),
                database = mysql.requiredString("database", "mysql"),
                username = mysql.requiredString("username", "mysql"),
                passwordSetting = mysql.requiredString("password", "mysql"),
                networkSecretReference = mysql.requiredString("network-secret", "mysql"),
                connectionTimeout = parsePositiveDuration(
                    mysql.requiredString("connection-timeout", "mysql"),
                    "mysql.connection-timeout",
                ),
                tlsMode = mysql.requiredEnum("tls-mode", "mysql"),
            ),
            json = json,
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
        "network-secret",
        "connection-timeout",
        "tls-mode",
    )
    private val SHORT_DURATION = Regex("^(\\d+)(ms|s|m|h|d)$")
}
