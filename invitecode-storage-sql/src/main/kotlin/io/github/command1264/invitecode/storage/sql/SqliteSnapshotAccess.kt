package io.github.command1264.invitecode.storage.sql

import java.nio.file.Path
import java.sql.Connection

/** Uses the bundled driver loader, which also works under legacy Bukkit plugin class loaders. */
object SqliteSnapshotAccess {
    fun <T> withConnection(
        database: Path,
        guard: BlockingOperationGuard,
        operation: (Connection) -> T,
    ): T {
        guard.checkAllowed()
        return connect(database).use(operation)
    }
}
