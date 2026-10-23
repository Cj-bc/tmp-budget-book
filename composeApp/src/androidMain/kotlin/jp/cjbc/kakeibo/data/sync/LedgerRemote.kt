package jp.cjbc.kakeibo.data.sync

import java.io.File
import java.io.IOException
import jp.cjbc.kakeibo.data.CommitAuthor
import jp.cjbc.kakeibo.data.LEDGER_BRANCH
import jp.cjbc.kakeibo.data.files.MASTER_FILE
import jp.cjbc.kakeibo.data.files.TRANSACTIONS_DIRECTORY
import jp.cjbc.kakeibo.data.files.layoutRecords
import jp.cjbc.kakeibo.data.files.mergeFiles
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.api.errors.TransportException
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.lib.RefUpdate
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevTree
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.revwalk.filter.RevFilter
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.RefSpec
import org.eclipse.jgit.transport.RemoteRefUpdate
import org.eclipse.jgit.treewalk.TreeWalk

/** The name of the remote that the ledger syncs with. */
const val REMOTE = "origin"

/** The remote's ledger branch, as last fetched. */
const val REMOTE_BRANCH_REF = "refs/remotes/$REMOTE/$LEDGER_BRANCH"

/** Where [useRemote] keeps the local commits it replaces, followed by the time in milliseconds. */
const val SET_ASIDE_REF_PREFIX = "refs/kakeibo/before-remote/"

/** How the local ledger branch stands against the remote one, as [analyze] finds it. */
enum class Divergence {
    /** Both are at the same commit. */
    UP_TO_DATE,

    /** The remote has no ledger branch yet. */
    REMOTE_EMPTY,

    /** The remote's commits are all local: push. */
    LOCAL_AHEAD,

    /** The local commits are all on the remote: fast-forward. */
    REMOTE_AHEAD,

    /** Both have commits the other has not, from a common ancestor: merge, then push. */
    DIVERGED,

    /** They share no commit: the ledger was started separately on each. */
    UNRELATED,
}

/**
 * How the branches stand. [localUntouched] tells that the local ledger is as it was started: one
 * commit, without any transaction.
 */
data class Analysis(val divergence: Divergence, val localUntouched: Boolean)

/** What [merge] did. */
sealed interface MergeOutcome {
    /** The remote had nothing new. */
    data object NothingToDo : MergeOutcome

    /** The local branch was moved to the remote commit. */
    data object FastForwarded : MergeOutcome

    /** The changes of both sides were combined in a merge commit. */
    data object Merged : MergeOutcome

    /** [count] records were changed differently on each side. Nothing changed. */
    data class Conflicted(val count: Int) : MergeOutcome

    /** The branches share no commit. Nothing changed; see [useRemote]. */
    data object Unrelated : MergeOutcome
}

/** What [push] did. */
enum class PushOutcome { PUSHED, UP_TO_DATE, REJECTED }

/** The remote refused the push for another reason than new commits on it. */
class PushFailedException(message: String) : IOException(message)

/** Whether [error], thrown by a fetch or push, means that the remote did not accept the credentials. */
fun isAuthenticationError(error: Throwable): Boolean {
    val transport = generateSequence(error) { it.cause }.firstOrNull { it is TransportException || it is org.eclipse.jgit.errors.TransportException }
        ?: return false
    val message = transport.message.orEmpty().lowercase()
    return "not authorized" in message || "authentication" in message || "401" in message || "403" in message
}

/** Points the remote at [url], fetching every branch of it. */
fun setRemoteUrl(git: Git, url: String) {
    val config = git.repository.config
    if (config.getString("remote", REMOTE, "url") == url) return
    config.setString("remote", REMOTE, "url", url)
    config.setString("remote", REMOTE, "fetch", "+refs/heads/*:refs/remotes/$REMOTE/*")
    config.save()
}

/** Forgets the remote and what was fetched from it. */
fun removeRemote(git: Git) {
    git.remoteRemove().setRemoteName(REMOTE).call()
}

/** Fetches the remote's branches. */
fun fetch(git: Git, credentials: CredentialsProvider?) {
    git.fetch()
        .setRemote(REMOTE)
        .setRefSpecs(RefSpec("+refs/heads/*:refs/remotes/$REMOTE/*"))
        .setRemoveDeletedRefs(true)
        .setCredentialsProvider(credentials)
        .call()
}

/**
 * Pushes the local ledger branch to the remote's. Commits on the remote that are not local make it
 * [PushOutcome.REJECTED]: fetch and merge them first.
 *
 * @throws PushFailedException if the remote refused it otherwise.
 */
fun push(git: Git, credentials: CredentialsProvider?): PushOutcome {
    val branch = "refs/heads/$LEDGER_BRANCH"
    val update = git.push()
        .setRemote(REMOTE)
        .setRefSpecs(RefSpec("$branch:$branch"))
        .setCredentialsProvider(credentials)
        .call()
        .flatMap { it.remoteUpdates }
        .single()
    return when (update.status) {
        RemoteRefUpdate.Status.OK -> PushOutcome.PUSHED
        RemoteRefUpdate.Status.UP_TO_DATE -> PushOutcome.UP_TO_DATE
        RemoteRefUpdate.Status.REJECTED_NONFASTFORWARD,
        RemoteRefUpdate.Status.REJECTED_REMOTE_CHANGED -> PushOutcome.REJECTED
        else -> throw PushFailedException("${update.status}: ${update.message}")
    }
}

/** How the local ledger branch stands against the remote one as last fetched. */
fun analyze(git: Git): Analysis {
    val repository = git.repository
    RevWalk(repository).use { walk ->
        val local = walk.parseCommit(checkNotNull(repository.resolve(Constants.HEAD)) { "the ledger has no commit" })
        val untouched = local.parentCount == 0 && !hasTransactions(repository, local.tree)
        val remoteId = repository.resolve(REMOTE_BRANCH_REF) ?: return Analysis(Divergence.REMOTE_EMPTY, untouched)
        val remote = walk.parseCommit(remoteId)
        val divergence = when (val base = mergeBase(walk, local, remote)) {
            null -> Divergence.UNRELATED
            local -> if (local == remote) Divergence.UP_TO_DATE else Divergence.REMOTE_AHEAD
            remote -> Divergence.LOCAL_AHEAD
            else -> Divergence.DIVERGED
        }
        return Analysis(divergence, untouched)
    }
}

/**
 * Takes in the remote's commits as last fetched: moves the local branch to them if it has nothing
 * new, and otherwise merges them record by record with `mergeFiles`, committing the result with
 * both as parents. A merge with conflicts changes nothing. Commit the changes to the files first.
 */
fun merge(git: Git, author: CommitAuthor): MergeOutcome {
    val repository = git.repository
    RevWalk(repository).use { walk ->
        val local = walk.parseCommit(checkNotNull(repository.resolve(Constants.HEAD)) { "the ledger has no commit" })
        val remoteId = repository.resolve(REMOTE_BRANCH_REF) ?: return MergeOutcome.NothingToDo
        val remote = walk.parseCommit(remoteId)
        val base = mergeBase(walk, local, remote) ?: return MergeOutcome.Unrelated
        when (base) {
            remote -> return MergeOutcome.NothingToDo
            local -> {
                git.reset().setMode(ResetCommand.ResetType.HARD).setRef(remote.name).call()
                return MergeOutcome.FastForwarded
            }
        }

        val ours = filesOf(repository, local.tree)
        val theirs = filesOf(repository, remote.tree)
        val merge = mergeFiles(
            filesOf(repository, base.tree).filterKeys(::isDataFile),
            ours.filterKeys(::isDataFile),
            theirs.filterKeys(::isDataFile),
        )
        if (merge.conflicts.isNotEmpty()) return MergeOutcome.Conflicted(merge.conflicts.size)

        val merged = layoutRecords(merge.merged)
        val workTree = repository.workTree
        ours.keys.filter { isDataFile(it) && it !in merged }.forEach { File(workTree, it).delete() }
        merged.forEach { (path, content) -> if (ours[path] != content) writeFile(File(workTree, path), content) }
        // Other files, such as .gitattributes, keep the local version, or take the remote's if new.
        theirs.forEach { (path, content) -> if (!isDataFile(path) && path !in ours) writeFile(File(workTree, path), content) }

        git.add().addFilepattern(".").call()
        git.add().addFilepattern(".").setUpdate(true).call()
        repository.writeMergeHeads(listOf(remote))
        val person = PersonIdent(author.name, author.email)
        git.commit()
            .setMessage("Merge the changes from $REMOTE")
            .setAuthor(person)
            .setCommitter(person)
            .call()
        return MergeOutcome.Merged
    }
}

/**
 * Replaces the local ledger with the remote's as last fetched. Local commits are kept under
 * [SET_ASIDE_REF_PREFIX] followed by [now], in milliseconds; returns that ref's name, or `null`
 * without any local commit.
 */
fun useRemote(git: Git, now: Long): String? {
    val repository = git.repository
    val remote = checkNotNull(repository.resolve(REMOTE_BRANCH_REF)) { "nothing was fetched" }
    val local = repository.resolve(Constants.HEAD)
    val setAside = local?.let { "$SET_ASIDE_REF_PREFIX$now" }
    if (local != null) {
        val result = repository.updateRef(setAside).run {
            setNewObjectId(local)
            setExpectedOldObjectId(ObjectId.zeroId())
            update()
        }
        check(result == RefUpdate.Result.NEW) { "could not keep the local commits as $setAside: $result" }
    } else {
        // Give the branch a commit, so that the reset below has one to start from.
        val result = repository.updateRef("refs/heads/$LEDGER_BRANCH").run {
            setNewObjectId(remote)
            update()
        }
        check(result == RefUpdate.Result.NEW) { "could not start $LEDGER_BRANCH: $result" }
    }
    git.reset().setMode(ResetCommand.ResetType.HARD).setRef(remote.name).call()
    return setAside
}

/** Whether [path] is one of the ledger's data files, which are merged record by record. */
private fun isDataFile(path: String): Boolean =
    path == MASTER_FILE || (path.startsWith("$TRANSACTIONS_DIRECTORY/") && path.endsWith(".jsonl"))

private fun mergeBase(walk: RevWalk, a: RevCommit, b: RevCommit): RevCommit? {
    walk.reset()
    walk.revFilter = RevFilter.MERGE_BASE
    walk.markStart(a)
    walk.markStart(b)
    val base = walk.next()
    walk.reset()
    walk.revFilter = RevFilter.ALL
    return base
}

private fun hasTransactions(repository: Repository, tree: RevTree): Boolean =
    filesOf(repository, tree).any { (path, content) ->
        path.startsWith("$TRANSACTIONS_DIRECTORY/") && content.isNotBlank()
    }

/** The files in [tree] with their contents, by path. */
private fun filesOf(repository: Repository, tree: RevTree): Map<String, String> =
    TreeWalk(repository).use { walk ->
        walk.addTree(tree)
        walk.isRecursive = true
        buildMap {
            while (walk.next()) put(walk.pathString, repository.open(walk.getObjectId(0)).bytes.decodeToString())
        }
    }

/** Writes [content] to a copy and renames it over [file], so that it is never half written. */
private fun writeFile(file: File, content: String) {
    file.parentFile?.mkdirs()
    val temporary = File(file.parentFile, "${file.name}.tmp")
    temporary.writeText(content)
    if (!temporary.renameTo(file)) throw IOException("could not replace $file")
}
