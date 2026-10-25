package app.gocast.update

import java.net.URI
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking

class DownloadedUpdateTest {
    @Test fun changedInstallerCannotBeOpenedAsTheVerifiedDownload() = runBlocking<Unit> {
        val file = Files.createTempFile("gocast-update-test-", ".msi").toFile()
        val asset = UpdateAsset(
            "fixture.msi", URI("https://github.com/dimitarpst/gocast/releases/download/v1.0.4/fixture.msi"), 3,
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
        )
        try {
            file.writeText("abc")
            GitHubUpdates.verifyDownloaded(file, asset)
            file.writeText("abd") // Same size; length checking alone would miss this.
            assertFailsWith<UpdateFailure> { GitHubUpdates.verifyDownloaded(file, asset) }
            file.writeText("ab")
            assertFailsWith<UpdateFailure> { GitHubUpdates.verifyDownloaded(file, asset) }
        } finally {
            Files.deleteIfExists(file.toPath())
        }
    }
}
