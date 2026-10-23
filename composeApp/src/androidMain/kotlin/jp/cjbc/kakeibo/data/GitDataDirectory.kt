package jp.cjbc.kakeibo.data

import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors
import kotlin.coroutines.suspendCoroutine
import jp.cjbc.kakeibo.data.files.MASTER_FILE
import jp.cjbc.kakeibo.data.files.TRANSACTIONS_DIRECTORY
import jp.cjbc.kakeibo.data.files.layoutRecords
import jp.cjbc.kakeibo.data.files.mergeFiles
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.lib.Config
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.lib.RefUpdate
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.revwalk.filter.RevFilter
import org.eclipse.jgit.storage.file.FileBasedConfig
import org.eclipse.jgit.storage.file.WindowCacheConfig
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.RefSpec
import org.eclipse.jgit.transport.RemoteRefUpdate
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.util.FS
import org.eclipse.jgit.util.SystemReader

/** The branch the ledger is committed on. */
const val LEDGER_BRANCH = "main"

/** Where the remote's branch is kept after a fetch. */
private const val REMOTE_BRANCH = "refs/remotes/origin/$LEDGER_BRANCH"

/** Seconds that a fetch or push may wait for the remote. */
private const val NETWORK_TIMEOUT = 20

/** Keeps the data files' line endings LF on every platform, as the format requires. */
private const val GIT_ATTRIBUTES = "*.json text eol=lf\n*.jsonl text eol=lf\n"

/**
 * The ledger's files in [directory], a git repository that gets a commit for each change. Files
 * are read, written and committed in order on one background thread, so that the UI never waits
 * for the disk. A write or commit that fails is reported and skipped.
 */
class GitDataDirectory(private val directory: File) {
    private val executor = Executors.newSingleThreadExecutor { Thread(it, "ledger-files") }

    /** The repository, opened or created on the executor's thread when first needed. */
    private var git: Git? = null

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

    fun commit(message: String, author: CommitAuthor) = enqueue("commit") { commitNow(message, author) }

    /**
     * Fetches [remote]'s branch, takes in its commits and pushes the local ones, after everything
     * queued before. [remote] is asked for on the background thread, and may take its time. Without
     * one, nothing happens and `null` is returned.
     *
     * When both sides have new commits, the data files are merged record by record from their
     * common ancestor, and the result is committed by [author] as a merge. If records conflict,
     * nothing changes. Files that are not committed are committed by [author] first.
     */
    suspend fun sync(author: CommitAuthor, remote: () -> GitRemote?): SyncStatus? = onExecutor {
        remote()?.let { syncNow(it, author) }
    }

    /**
     * Makes the local branch [remote]'s, keeping the local commits on a branch named after the
     * time, `local-<UTC time>`. The files then are the remote's.
     */
    suspend fun replaceWithRemote(author: CommitAuthor, remote: () -> GitRemote?): SyncStatus? = onExecutor {
        val target = remote() ?: return@onExecutor null
        attempt {
            val git = repository()
            commitPending(git, author)
            val theirs = fetch(git, target) ?: return@attempt SyncStatus.UpToDate
            git.repository.resolve("HEAD")?.let { ours ->
                val name = "local-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT)
                    .apply { timeZone = TimeZone.getTimeZone("UTC") }
                    .format(Date())
                git.branchCreate().setName(name).setStartPoint(ours.name).call()
            }
            moveTo(git, theirs)
            SyncStatus.Pulled
        }
    }

    /**
     * Pushes the local commits to [remote] after everything queued before, and passes the outcome
     * to [onResult] on the background thread. Without a remote, nothing happens.
     */
    fun push(remote: () -> GitRemote?, onResult: (SyncStatus) -> Unit) = enqueue("push") {
        val target = remote() ?: return@enqueue
        onResult(attempt { push(repository(), target) })
    }

    private fun commitNow(message: String, author: CommitAuthor) {
        val git = repository()
        // Stage new and changed files, then deleted ones.
        git.add().addFilepattern(".").call()
        git.add().addFilepattern(".").setUpdate(true).call()
        if (git.status().call().isClean) return
        val person = PersonIdent(author.name, author.email)
        git.commit().setMessage(message).setAuthor(person).setCommitter(person).call()
    }

    private fun commitPending(git: Git, author: CommitAuthor) {
        if (!git.status().call().isClean) commitNow("Record changes to the files that were not committed", author)
    }

    private fun syncNow(remote: GitRemote, author: CommitAuthor): SyncStatus = attempt {
        val git = repository()
        commitPending(git, author)
        val theirs = fetch(git, remote)
        val ours = git.repository.resolve("HEAD")
        when {
            theirs == null -> if (ours == null) SyncStatus.UpToDate else push(git, remote)
            ours == theirs -> SyncStatus.UpToDate
            // A new device has no data yet, at most .gitattributes.
            ours == null || MASTER_FILE !in dataFiles(git, ours) -> {
                moveTo(git, theirs)
                SyncStatus.Pulled
            }
            else -> when (val base = mergeBase(git, ours, theirs)) {
                theirs -> push(git, remote)
                ours -> {
                    moveTo(git, theirs)
                    SyncStatus.Pulled
                }
                // Histories that only share commits without data, which two new devices can make
                // alike, are as unrelated as ones that share nothing.
                null -> SyncStatus.UnrelatedHistories
                else -> if (MASTER_FILE in dataFiles(git, base)) merge(git, remote, author, base, ours, theirs) else SyncStatus.UnrelatedHistories
            }
        }
    }

    /** Runs [task], turning the errors that a sync can meet into [SyncStatus.Failed]. */
    private inline fun attempt(task: () -> SyncStatus): SyncStatus = try {
        task()
    } catch (error: Exception) {
        System.err.println("Ledger: could not sync: $error")
        SyncStatus.Failed(error.message ?: error.toString())
    }

    /** Fetches [remote]'s branch, and returns its commit, or `null` if it has no such branch. */
    private fun fetch(git: Git, remote: GitRemote): ObjectId? {
        git.fetch()
            .setRemote(remote.url)
            .setRefSpecs(RefSpec("+refs/heads/*:refs/remotes/origin/*"))
            .setRemoveDeletedRefs(true)
            .setCredentialsProvider(remote.credentials)
            .setTimeout(NETWORK_TIMEOUT)
            .call()
        return git.repository.resolve(REMOTE_BRANCH)
    }

    private fun push(git: Git, remote: GitRemote): SyncStatus {
        if (git.repository.resolve("HEAD") == null) return SyncStatus.UpToDate
        val updates = git.push()
            .setRemote(remote.url)
            .setRefSpecs(RefSpec("refs/heads/$LEDGER_BRANCH:refs/heads/$LEDGER_BRANCH"))
            .setCredentialsProvider(remote.credentials)
            .setTimeout(NETWORK_TIMEOUT)
            .call()
            .flatMap { it.remoteUpdates }
        return when {
            updates.all { it.status == RemoteRefUpdate.Status.UP_TO_DATE } -> SyncStatus.UpToDate
            updates.all { it.status == RemoteRefUpdate.Status.OK || it.status == RemoteRefUpdate.Status.UP_TO_DATE } ->
                SyncStatus.Pushed
            updates.any { it.status == RemoteRefUpdate.Status.REJECTED_NONFASTFORWARD } -> SyncStatus.Rejected
            else -> SyncStatus.Failed(updates.joinToString { "${it.status} ${it.message.orEmpty()}".trim() })
        }
    }

    /** Points the local branch at [commit], and makes the files its. */
    private fun moveTo(git: Git, commit: ObjectId) {
        val update = git.repository.updateRef("refs/heads/$LEDGER_BRANCH")
        update.setNewObjectId(commit)
        update.isForceUpdate = true
        val result = update.update()
        if (result != RefUpdate.Result.NEW && result != RefUpdate.Result.FORCED && result != RefUpdate.Result.NO_CHANGE) {
            throw IOException("could not move $LEDGER_BRANCH: $result")
        }
        git.reset().setMode(ResetCommand.ResetType.HARD).setRef(commit.name).call()
    }

    private fun mergeBase(git: Git, ours: ObjectId, theirs: ObjectId): ObjectId? = RevWalk(git.repository).use { walk ->
        walk.revFilter = RevFilter.MERGE_BASE
        walk.markStart(walk.parseCommit(ours))
        walk.markStart(walk.parseCommit(theirs))
        walk.next()?.toObjectId()
    }

    /**
     * Merges the data files of [ours] and [theirs] record by record from [base], and commits the
     * result as a merge.
     */
    private fun merge(git: Git, remote: GitRemote, author: CommitAuthor, base: ObjectId, ours: ObjectId, theirs: ObjectId): SyncStatus {
        val ourFiles = dataFiles(git, ours)
        val merge = mergeFiles(dataFiles(git, base), ourFiles, dataFiles(git, theirs))
        if (merge.conflicts.isNotEmpty()) return SyncStatus.Conflicts(merge.conflicts.size)
        val contents = layoutRecords(merge.merged)
        (ourFiles.keys - contents.keys).forEach { File(directory, it).delete() }
        contents.forEach { (path, content) ->
            val file = File(directory, path)
            file.parentFile?.mkdirs()
            file.writeText(content)
        }
        git.repository.writeMergeHeads(listOf(theirs))
        git.add().addFilepattern(".").call()
        git.add().addFilepattern(".").setUpdate(true).call()
        val person = PersonIdent(author.name, author.email)
        git.commit().setMessage("Merge the changes of another device").setAuthor(person).setCommitter(person).call()
        // The files changed either way; a push that fails is tried again with the next one.
        val pushed = push(git, remote)
        if (pushed != SyncStatus.Pushed) System.err.println("Ledger: could not push the merge: $pushed")
        return SyncStatus.Pulled
    }

    /** The data files in [commit]: master.json and the transaction files, by path. */
    private fun dataFiles(git: Git, commit: ObjectId): Map<String, String> = RevWalk(git.repository).use { walk ->
        TreeWalk(git.repository).use { tree ->
            tree.addTree(walk.parseCommit(commit).tree)
            tree.isRecursive = true
            buildMap {
                while (tree.next()) {
                    val path = tree.pathString
                    val isData = path == MASTER_FILE ||
                        (path.startsWith("$TRANSACTIONS_DIRECTORY/") && path.endsWith(".jsonl") && path.count { it == '/' } == 1)
                    if (isData) put(path, git.repository.open(tree.getObjectId(0)).bytes.decodeToString())
                }
            }
        }
    }

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

/** A remote repository: its HTTPS [url], and the [credentials] to use with it. */
class GitRemote(val url: String, val credentials: CredentialsProvider?)

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

    // JGit saves what it learns about the file system to its own configuration, which is this.
    override fun save() {}
}
