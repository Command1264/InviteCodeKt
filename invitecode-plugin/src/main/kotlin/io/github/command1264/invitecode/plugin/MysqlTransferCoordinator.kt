package io.github.command1264.invitecode.plugin

import io.github.command1264.invitecode.storage.sql.MysqlStorageException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.sql.Connection
import javax.sql.DataSource

/** Serializes plugin startup with offline transfer staging and publication in one MySQL database. */
internal object MysqlTransferCoordinator {
    fun <T> withLock(pool: DataSource, operation: (Connection) -> T): T = pool.connection.use { connection ->
        val database = requireNotNull(connection.catalog) { "MySQL database is not selected." }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(database.toByteArray(StandardCharsets.UTF_8))
            .take(20).joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val name = "invitecode:transfer:$digest"
        val acquired = connection.prepareStatement("SELECT GET_LOCK(?, 10)").use { statement ->
            statement.setString(1, name)
            statement.executeQuery().use { rows -> rows.next() && rows.getInt(1) == 1 }
        }
        if (!acquired) throw MysqlStorageException(
            "MySQL backend transfer/startup lock timed out\nstop all instances and retry.",
        )
        try {
            operation(connection)
        } finally {
            connection.prepareStatement("SELECT RELEASE_LOCK(?)").use { statement ->
                statement.setString(1, name)
                statement.executeQuery().use { rows ->
                    if (!rows.next() || rows.getInt(1) != 1) throw MysqlStorageException(
                        "MySQL backend transfer/startup lock was lost\ninspect the database before continuing.",
                    )
                }
            }
        }
    }
}
