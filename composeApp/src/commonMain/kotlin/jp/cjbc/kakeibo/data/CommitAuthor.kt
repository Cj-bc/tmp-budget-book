package jp.cjbc.kakeibo.data

private const val NAME_KEY = "kakeibo:git:authorName"
private const val EMAIL_KEY = "kakeibo:git:authorEmail"

/** Who the commits of the ledger's changes are by: both their author and their committer. */
data class CommitAuthor(val name: String, val email: String) {
    companion object {
        /** The app's name, with an address that can never be delivered to. */
        val DEFAULT = CommitAuthor("Kakeibo", "kakeibo@kakeibo.invalid")
    }
}

/** The commit author picked in the settings, [CommitAuthor.DEFAULT] until one is. */
fun readCommitAuthor(storage: KeyValueStorage = PlatformStorage): CommitAuthor = CommitAuthor(
    name = storage.read(NAME_KEY) ?: CommitAuthor.DEFAULT.name,
    email = storage.read(EMAIL_KEY) ?: CommitAuthor.DEFAULT.email,
)

fun writeCommitAuthor(author: CommitAuthor, storage: KeyValueStorage = PlatformStorage) {
    storage.write(NAME_KEY, author.name)
    storage.write(EMAIL_KEY, author.email)
}
