package io.github.command1264.invitecode.storage.sql

import io.github.command1264.invitecode.core.InviteCodeStorage
import io.github.command1264.invitecode.core.StorageContractTest
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class SqliteStorageContractTest : StorageContractTest() {
    @TempDir lateinit var temporaryDirectory: Path

    override fun withStorage(test: (InviteCodeStorage) -> Unit) {
        val database = temporaryDirectory.resolve("contract.db")
        val guard = BlockingOperationGuard { }
        SqliteSchemaMigrator(database, guard).migrate()
        SqliteInviteCodeStorage.open(database, guard).use(test)
    }
}
