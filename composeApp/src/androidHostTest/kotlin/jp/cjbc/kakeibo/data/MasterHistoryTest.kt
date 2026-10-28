package jp.cjbc.kakeibo.data

import java.io.File
import java.nio.file.Files
import jp.cjbc.kakeibo.data.files.MASTER_FILE
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import org.eclipse.jgit.api.Git

private fun master(funds: Map<String, String>): String =
    "{\"version\": 1, \"funds\": [" +
        funds.entries.joinToString(", ") { (id, name) -> "{\"id\": \"$id\", \"name\": \"$name\"}" } +
        "], \"expense_categories\": [], \"income_categories\": [], \"allocation_rules\": [], \"recurring_items\": []}\n"

class MasterHistoryTest {
    private val directory: File = Files.createTempDirectory("history").toFile()
    private val git = Git.init().setDirectory(directory).setInitialBranch(LEDGER_BRANCH).call()

    @AfterTest
    fun deleteDirectory() {
        git.close()
        directory.deleteRecursively()
    }

    private fun commit(path: String, content: String) {
        File(directory, path).apply { parentFile?.mkdirs() }.writeText(content)
        git.add().addFilepattern(".").call()
        git.commit().setMessage("Change $path").setAuthor("Me", "me@example.invalid").setCommitter("Me", "me@example.invalid").call()
    }

    private fun find(vararg ids: String) =
        findNamesInHistory(git.repository, listOf(git.repository.resolve("HEAD")), ids.toSet())

    @Test
    fun findsTheNameADeletedFundLastHad() {
        commit(MASTER_FILE, master(mapOf("a" to "Food", "b" to "Phone")))
        commit(MASTER_FILE, master(mapOf("a" to "Food", "b" to "Mobile")))
        commit(MASTER_FILE, master(mapOf("a" to "Food")))
        commit("transactions/2026-10.jsonl", "{}\n")

        assertEquals(mapOf("b" to "Mobile"), find("b"))
    }

    @Test
    fun leavesOutWhatNoCommitHas() {
        commit(MASTER_FILE, master(mapOf("a" to "Food")))

        assertEquals(mapOf("a" to "Food"), find("a", "nowhere"))
    }

    @Test
    fun givesUpAfterItsTimeLimit() {
        commit(MASTER_FILE, master(mapOf("b" to "Phone")))
        commit(MASTER_FILE, master(emptyMap()))

        val found = findNamesInHistory(git.repository, listOf(git.repository.resolve("HEAD")), setOf("b"), timeLimit = Duration.ZERO)

        assertEquals(emptyMap(), found)
    }

    @Test
    fun skipsAMasterThatCannotBeRead() {
        commit(MASTER_FILE, master(mapOf("b" to "Phone")))
        commit(MASTER_FILE, "{ broken b")
        commit(MASTER_FILE, master(emptyMap()))

        assertEquals(mapOf("b" to "Phone"), find("b"))
    }
}
