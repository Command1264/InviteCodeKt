package io.github.command1264.invitecode.storage.json

import io.github.command1264.invitecode.core.InviteCodeStorage
import io.github.command1264.invitecode.core.StorageContractTest
import io.github.command1264.invitecode.storage.sql.BlockingOperationGuard
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class JsonStorageContractTest : StorageContractTest() {
    @TempDir lateinit var temporaryDirectory: Path

    override fun withStorage(test: (InviteCodeStorage) -> Unit) {
        JsonInviteCodeStorage.open(
            temporaryDirectory.resolve("contract.json"), BlockingOperationGuard { },
        ).use(test)
    }
}
