package jp.cjbc.kakeibo.data

import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import kotlin.coroutines.suspendCoroutine
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Config
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.storage.file.FileBasedConfig
import org.eclipse.jgit.storage.file.WindowCacheConfig
import org.eclipse.jgit.util.FS
import org.eclipse.jgit.util.SystemReader

/** The branch the ledger is committed on. */
const val LEDGER_BRANCH = "main"

/** Keeps the data files' line endings LF on every platform, as the format requires. */
private const val GIT_ATTRIBUTES = "*.json text eol=lf\n*.jsonl text eol=lf\n"

/**
 * The ledger's files in [directory], a git repository that gets a commit for each change. Files
 * are read, written and committed in order on one background thread, so that the UI never waits
 * for the disk. A write or commit that fails is reported and skipped.
 */
class GitDataDirectory(val directory: File) {
    private val executor = Executors.newSingleThreadExecutor { Thread(it, "ledger-files") }

    /** The repository, opened or created on the executor's thread when first needed. */
    private var git: Git? = null

    /** Called on the executor's thread after each commit that recorded a change. */
    @Volatile
    var onCommitted: (() -> Unit)? = null

    suspend fun read(path: String): String? = onExecutor { File(directory, path).takeIf { it.isFile }?.readText() }

    suspend fun list(path: String): List<String> = onExecutor {
        File(directory, path).listFiles { file -> file.isFile }.orEmpty().map { it.name }
    }

    fun write(path: String, content: String) = enqueue("write $path") {
        val file = File(directory, path)
        file.parentFile?.mkdirs()
        // Write a copy and rename it, so that a crash never leaves the file half written.
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(content)
        if (!temporary.renameTo(file)) throw IOException("could not replace $file")
    }

    fun delete(path: String) = enqueue("delete $path") { File(directory, path).delete() }

    fun commit(message: String, author: CommitAuthor) = enqueue("commit") {
        val git = repository()
        // Stage new and changed files, then deleted ones.
        git.add().addFilepattern(".").call()
        git.add().addFilepattern(".").setUpdate(true).call()
        if (git.status().call().isClean) return@enqueue
        val person = PersonIdent(author.name, author.email)
        git.commit().setMessage(message).setAuthor(person).setCommitter(person).call()
        onCommitted?.invoke()
    }

    /**
     * Runs [task] on the repository after everything queued before, and returns what it returns.
     * Nothing else reads or writes the files meanwhile.
     */
    suspend fun <T> inRepository(task: (Git) -> T): T = onExecutor { task(repository()) }

    /** Runs [task] after everything queued before, and returns what it returns. */
    private suspend fun <T> onExecutor(task: () -> T): T = suspendCoroutine { continuation ->
        executor.execute { continuation.resumeWith(runCatching(task)) }
    }

    private fun enqueue(description: String, task: () -> Unit) = executor.execute {
        runCatching(task).onFailure { System.err.println("Ledger: could not $description: $it") }
    }

    private fun repository(): Git = git ?: run {
        if (File(directory, ".git").isDirectory) {
            Git.open(directory)
        } else {
            directory.mkdirs()
            File(directory, ".gitattributes").writeText(GIT_ATTRIBUTES)
            Git.init().setDirectory(directory).setInitialBranch(LEDGER_BRANCH).call()
        }
    }.also { git = it }

    companion object {
        init {
            // Android has no JMX, which JGit uses to report its cache by default.
            WindowCacheConfig().apply { exposeStatsViaJmx = false }.install()
            SystemReader.setInstance(RepositoryConfigOnly(SystemReader.getInstance()))
        }
    }
}

/**
 * Reads no user, system or JGit configuration, only the repository's own, so that settings made
 * for other tools (or that JGit does not know, such as `gpg.format=ssh`) never affect the ledger.
 */
private class RepositoryConfigOnly(private val delegate: SystemReader) : SystemReader() {
    override fun getHostname(): String = delegate.hostname

    override fun getenv(variable: String): String? = delegate.getenv(variable)

    override fun getProperty(key: String): String? = delegate.getProperty(key)

    override fun openUserConfig(parent: Config?, fs: FS): FileBasedConfig = EmptyConfig(parent, fs)

    override fun openSystemConfig(parent: Config?, fs: FS): FileBasedConfig = EmptyConfig(parent, fs)

    override fun openJGitConfig(parent: Config?, fs: FS): FileBasedConfig = EmptyConfig(parent, fs)

    override fun getCurrentTime(): Long = delegate.currentTime

    override fun getTimezone(time: Long): Int = delegate.getTimezone(time)
}

private class EmptyConfig(parent: Config?, fs: FS) : FileBasedConfig(parent, null, fs) {
    override fun load() {}

    override fun isOutdated(): Boolean = false
}
