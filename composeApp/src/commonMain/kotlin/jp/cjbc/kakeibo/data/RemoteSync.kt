package jp.cjbc.kakeibo.data

/** The outcome of syncing the ledger's files with the remote repository. */
sealed interface SyncStatus {
    /** Both sides had the same commits. */
    data object UpToDate : SyncStatus

    /** The local commits were pushed; the files did not change. */
    data object Pushed : SyncStatus

    /** The remote's changes were taken into the files, and the ledger must be opened again. */
    data object Pulled : SyncStatus

    /**
     * Both sides changed [count] records differently. Nothing was merged, and the sync stays
     * stopped until the conflicts are resolved.
     */
    data class Conflicts(val count: Int) : SyncStatus

    /**
     * The local and remote histories have no commit in common: both have data, as on the first
     * connection to a repository that another device filled. Nothing changed.
     */
    data object UnrelatedHistories : SyncStatus

    /** The remote got commits that the push did not include. The next sync takes them in. */
    data object Rejected : SyncStatus

    /** The sync stopped with an error, e.g. without a network: [message] says what failed. */
    data class Failed(val message: String) : SyncStatus
}

/** Whether a repository can be synced with, as [RemoteSync.setRepository] checks it. */
enum class RepositoryCheck {
    /** The app can read and write it. It is now the repository synced with. */
    OK,

    /** There is no such repository, or the app is not installed on it. */
    NOT_FOUND,

    /** The signed-in user cannot push to it. */
    READ_ONLY,

    /** The check could not be made, e.g. without a network. */
    FAILED,
}

/**
 * Syncing the ledger's files with a git repository on GitHub, as the app signed in to GitHub.
 * The properties are snapshot state, so composables reading them recompose when they change.
 */
interface RemoteSync {
    /** Whether the build has the GitHub App's client ID and secret. Without them, nothing works. */
    val isConfigured: Boolean

    /** The login of the signed-in GitHub user, or `null` when signed out. */
    val account: String?

    /** Whether signing in failed since it was last started. */
    val signInFailed: Boolean

    /** The repository synced with, as `owner/name`, or `null` if none is set. */
    val repository: String?

    /** How the last sync or push went, or `null` if there was none since the app started. */
    val status: SyncStatus?

    /** Whether the app can send the user to change the repositories it is installed on. */
    val canChangeInstallation: Boolean

    /**
     * Opens GitHub's pages that install the app on the repositories the user picks and ask the user
     * to let the app act for them. When the app is installed on one repository only, it becomes
     * the one synced with.
     */
    fun signIn()

    /**
     * Opens GitHub's page that changes the repositories the app is installed on. When the app is
     * installed on one repository only afterwards, and not on the current one, it becomes the one
     * synced with.
     */
    fun changeInstallation()

    /** Revokes the tokens and forgets them, as well as the repository. */
    suspend fun signOut()

    /**
     * Checks that the app can push to the repository [fullName] (`owner/name`), and makes it the
     * one synced with if so. Sync with it afterwards.
     */
    suspend fun setRepository(fullName: String): RepositoryCheck

    /** Stops syncing, forgetting the repository. The local files stay as they are. */
    fun clearRepository()

    /**
     * Takes in the remote's commits, merging the records that both sides changed when they do not
     * conflict, and pushes the local ones. Uncommitted changes are first committed by [author], as
     * is the merge. Returns `null` without a repository to sync with, or when signed out.
     */
    suspend fun sync(author: CommitAuthor): SyncStatus?

    /**
     * Replaces the local history with the remote's, after [SyncStatus.UnrelatedHistories]. The local
     * history is kept aside on a branch of its own.
     */
    suspend fun replaceWithRemote(author: CommitAuthor): SyncStatus?
}
