package jp.cjbc.kakeibo.sync

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.IOException
import jp.cjbc.kakeibo.data.CommitAuthor
import jp.cjbc.kakeibo.data.GitDataDirectory
import jp.cjbc.kakeibo.data.GitRemote
import jp.cjbc.kakeibo.data.RemoteSync
import jp.cjbc.kakeibo.data.RepositoryCheck
import jp.cjbc.kakeibo.data.SyncStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider

private const val PREFERENCES_NAME = "kakeibo-sync"
private const val ACCOUNT = "account"
private const val REPOSITORY = "repository"
private const val ACCESS_EXPIRES_AT = "accessExpiresAt"
private const val PENDING_STATE = "pendingState"
private const val PENDING_VERIFIER = "pendingVerifier"

// Kept in the CredentialStore.
private const val ACCESS_TOKEN = "accessToken"
private const val REFRESH_TOKEN = "refreshToken"

/** An access token is renewed when it expires within this time. */
private const val EXPIRY_MARGIN_MILLIS = 5 * 60 * 1000L

/** Stored instead of an expiry for tokens that do not expire. */
private const val NO_EXPIRY = -1L

/**
 * Syncs [directory] with a repository on GitHub, signed in to the GitHub App that the build names
 * in [GitHubAppConfig]. The tokens are kept encrypted with a key in the Android Keystore.
 */
internal class GitHubSync(context: Context, private val directory: GitDataDirectory) : RemoteSync {
    private val context = context.applicationContext
    private val preferences = this.context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val credentials = CredentialStore(this.context)
    private val client = GitHubClient(GitHubAppConfig.CLIENT_ID, GitHubAppConfig.CLIENT_SECRET)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Where GitHub sends the user back to: this app, by its ID, which the debug build changes. */
    private val redirectUri = "${this.context.packageName}://oauth/callback"

    /** Guards the tokens, which a sync and a push can both renew. */
    private val tokenLock = Any()

    override val isConfigured = GitHubAppConfig.CLIENT_ID.isNotEmpty() && GitHubAppConfig.CLIENT_SECRET.isNotEmpty()

    override var account by mutableStateOf(preferences.getString(ACCOUNT, null))
        private set

    override var signInFailed by mutableStateOf(false)
        private set

    override var repository by mutableStateOf(preferences.getString(REPOSITORY, null))
        private set

    override var status by mutableStateOf<SyncStatus?>(null)
        private set

    override fun signIn() {
        if (!isConfigured) return
        val pkce = Pkce.generate()
        val state = randomToken()
        preferences.edit().putString(PENDING_STATE, state).putString(PENDING_VERIFIER, pkce.verifier).apply()
        signInFailed = false
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(client.authorizeUrl(redirectUri, state, pkce)))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (error: ActivityNotFoundException) {
            System.err.println("Sync: no browser to sign in with: $error")
            signInFailed = true
        }
    }

    /**
     * Finishes signing in with the [uri] that GitHub redirected to, if it is this app's redirect.
     * Returns whether it was.
     */
    fun handleRedirect(uri: Uri): Boolean {
        if (uri.toString().substringBefore('?') != redirectUri) return false
        val state = preferences.getString(PENDING_STATE, null)
        val verifier = preferences.getString(PENDING_VERIFIER, null)
        preferences.edit().remove(PENDING_STATE).remove(PENDING_VERIFIER).apply()
        val code = uri.getQueryParameter("code")
        // A redirect without the state the app sent did not come from its own request.
        if (state == null || verifier == null || code == null || uri.getQueryParameter("state") != state) {
            uri.getQueryParameter("error")?.let { System.err.println("Sync: sign-in failed: $it") }
            signInFailed = true
            return true
        }
        scope.launch {
            runCatching {
                val tokens = client.exchangeCode(code, verifier, redirectUri)
                val login = client.login(tokens.access)
                synchronized(tokenLock) { saveTokens(tokens) }
                preferences.edit().putString(ACCOUNT, login).apply()
                login
            }.onSuccess { account = it }.onFailure {
                System.err.println("Sync: could not sign in: $it")
                signInFailed = true
            }
        }
        return true
    }

    override suspend fun signOut() = withContext(Dispatchers.IO) {
        synchronized(tokenLock) {
            credentials.read(ACCESS_TOKEN)?.let { token ->
                runCatching { client.revoke(token) }.onFailure { System.err.println("Sync: could not revoke the token: $it") }
            }
            forgetTokens()
        }
        clearRepository()
    }

    override suspend fun setRepository(fullName: String): RepositoryCheck {
        val name = repositoryName(fullName) ?: return RepositoryCheck.NOT_FOUND
        val check = withContext(Dispatchers.IO) {
            try {
                accessToken()?.let { client.checkRepository(it, name) } ?: RepositoryCheck.FAILED
            } catch (error: IOException) {
                System.err.println("Sync: could not check $name: $error")
                RepositoryCheck.FAILED
            }
        }
        if (check == RepositoryCheck.OK) {
            preferences.edit().putString(REPOSITORY, name).apply()
            repository = name
            status = null
        }
        return check
    }

    override fun clearRepository() {
        preferences.edit().remove(REPOSITORY).apply()
        repository = null
        status = null
    }

    override suspend fun sync(author: CommitAuthor): SyncStatus? =
        directory.sync(author, ::remote)?.also { status = it }

    override suspend fun replaceWithRemote(author: CommitAuthor): SyncStatus? =
        directory.replaceWithRemote(author, ::remote)?.also { status = it }

    /**
     * Pushes the commits made since, unless the last sync stopped at what the user has to settle
     * first, or found that the remote has commits to take in.
     */
    fun push() {
        when (status) {
            is SyncStatus.Conflicts, SyncStatus.UnrelatedHistories, SyncStatus.Rejected -> return
            else -> directory.push(::remote) { status = it }
        }
    }

    /** Pushes again if the last push failed. */
    fun retryPush() {
        if (status is SyncStatus.Failed) push()
    }

    /** The repository to sync with, with a token to push with, or `null` if signed out or there is none. */
    private fun remote(): GitRemote? {
        val name = repository ?: return null
        val token = accessToken() ?: return null
        return GitRemote("https://github.com/$name.git", UsernamePasswordCredentialsProvider("x-access-token", token))
    }

    /**
     * A user access token that has not expired, renewed with the refresh token if needed, or `null`
     * when signed out. A refresh token that GitHub refuses signs the user out.
     *
     * @throws IOException if GitHub cannot be reached to renew the token.
     */
    private fun accessToken(): String? {
        synchronized(tokenLock) {
            return renewedAccessToken()
        }
    }

    private fun renewedAccessToken(): String? {
        val access = credentials.read(ACCESS_TOKEN) ?: return null
        val expiresAt = preferences.getLong(ACCESS_EXPIRES_AT, NO_EXPIRY)
        if (expiresAt == NO_EXPIRY || System.currentTimeMillis() < expiresAt - EXPIRY_MARGIN_MILLIS) return access
        val refresh = credentials.read(REFRESH_TOKEN)
        if (refresh == null) {
            forgetTokens()
            return null
        }
        return try {
            client.refresh(refresh).also(::saveTokens).access
        } catch (error: GitHubAuthException) {
            System.err.println("Sync: signed out: $error")
            forgetTokens()
            null
        }
    }

    private fun saveTokens(tokens: Tokens) {
        credentials.write(ACCESS_TOKEN, tokens.access)
        credentials.write(REFRESH_TOKEN, tokens.refresh)
        preferences.edit().putLong(ACCESS_EXPIRES_AT, tokens.accessExpiresAt ?: NO_EXPIRY).apply()
    }

    private fun forgetTokens() {
        credentials.write(ACCESS_TOKEN, null)
        credentials.write(REFRESH_TOKEN, null)
        preferences.edit().remove(ACCESS_EXPIRES_AT).remove(ACCOUNT).apply()
        account = null
    }
}
