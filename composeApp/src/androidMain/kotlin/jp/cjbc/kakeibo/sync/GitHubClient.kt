package jp.cjbc.kakeibo.sync

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import jp.cjbc.kakeibo.data.RepositoryCheck
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

private const val AUTHORIZE_URL = "https://github.com/login/oauth/authorize"
private const val TOKEN_URL = "https://github.com/login/oauth/access_token"
private const val API_URL = "https://api.github.com"
private const val API_VERSION = "2022-11-28"

/** Requests made when GitHub is busy or unreachable, before giving up. */
private const val MAX_ATTEMPTS = 3

/** The longest wait between attempts that a `Retry-After` header can ask for. */
private const val MAX_RETRY_DELAY_MILLIS = 60_000L

private const val TIMEOUT_MILLIS = 20_000

/** `owner/name` of a repository, as GitHub allows them. */
private val REPOSITORY_NAME = Regex("[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?/[A-Za-z0-9._-]+")

/**
 * A GitHub App user access token, with when it expires, and the token that renews it. The times
 * are in milliseconds since the epoch, `null` for tokens that do not expire.
 */
internal data class Tokens(
    val access: String,
    val accessExpiresAt: Long?,
    val refresh: String?,
    val refreshExpiresAt: Long?,
)

/** GitHub answered a token request with [error], such as `bad_refresh_token`. */
internal class GitHubAuthException(val error: String, description: String?) :
    IOException(description?.let { "$error: $it" } ?: error)

/** A code verifier for PKCE, and its S256 challenge. */
internal class Pkce(val verifier: String, val challenge: String) {
    companion object {
        fun generate(): Pkce {
            val verifier = randomToken()
            val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
            return Pkce(verifier, base64Url(digest))
        }
    }
}

/** 32 random bytes in URL-safe Base64, for PKCE verifiers and OAuth states. */
internal fun randomToken(): String = base64Url(ByteArray(32).also(SecureRandom()::nextBytes))

private fun base64Url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

/** [name] if it names a repository as `owner/name`, without a URL around it or `.git` after it. */
internal fun repositoryName(name: String): String? = name.trim()
    .removePrefix("https://github.com/")
    .removeSuffix("/")
    .removeSuffix(".git")
    .takeIf(REPOSITORY_NAME::matches)

/**
 * Reads GitHub's answer to a token request, made at [now]. GitHub answers errors with 200 too.
 *
 * @throws GitHubAuthException if GitHub refused the request.
 */
internal fun parseTokens(body: String, now: Long): Tokens {
    val json = Json.parseToJsonElement(body).jsonObject
    fun string(key: String) = json[key]?.jsonPrimitive?.contentOrNull
    fun expiry(key: String) = json[key]?.jsonPrimitive?.longOrNull?.let { now + it * 1000 }
    string("error")?.let { throw GitHubAuthException(it, string("error_description")) }
    return Tokens(
        access = string("access_token") ?: throw GitHubAuthException("no_access_token", null),
        accessExpiresAt = expiry("expires_in"),
        refresh = string("refresh_token"),
        refreshExpiresAt = expiry("refresh_token_expires_in"),
    )
}

/**
 * Signs in to the GitHub App [clientId] with the authorization code flow and PKCE, and calls
 * GitHub's API as the user. The calls block, so make them off the main thread. Requests that fail
 * because GitHub is busy or unreachable are tried again a few times, waiting longer each time, or
 * as long as GitHub asks.
 */
internal class GitHubClient(
    private val clientId: String,
    private val clientSecret: String,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /**
     * The page that installs the app [slug] on the repositories the user picks, or changes an
     * installation. GitHub then sends the user to the app's setup URL.
     */
    fun installUrl(slug: String): String = "https://github.com/apps/$slug/installations/new"

    /** The page that asks the user to authorize the app, and then redirects to [redirectUri]. */
    fun authorizeUrl(redirectUri: String, state: String, pkce: Pkce): String = AUTHORIZE_URL + "?" + form(
        "client_id" to clientId,
        "redirect_uri" to redirectUri,
        "state" to state,
        "code_challenge" to pkce.challenge,
        "code_challenge_method" to "S256",
    )

    /** Exchanges the [code] that GitHub redirected with for tokens. */
    fun exchangeCode(code: String, verifier: String, redirectUri: String): Tokens = requestTokens(
        "code" to code,
        "code_verifier" to verifier,
        "redirect_uri" to redirectUri,
    )

    /** Renews the access token. GitHub then also replaces the refresh token. */
    fun refresh(refreshToken: String): Tokens = requestTokens(
        "grant_type" to "refresh_token",
        "refresh_token" to refreshToken,
    )

    /** Revokes [accessToken], and with it the user's authorization of this token's refresh. */
    fun revoke(accessToken: String) {
        val credentials = Base64.getEncoder().encodeToString("$clientId:$clientSecret".toByteArray())
        val response = request(
            "DELETE",
            "$API_URL/applications/$clientId/token",
            apiHeaders + ("Authorization" to "Basic $credentials") + ("Content-Type" to "application/json"),
            buildJsonObject { put("access_token", accessToken) }.toString(),
        )
        // 404: the token was already revoked or expired.
        if (response.code != HttpURLConnection.HTTP_NO_CONTENT && response.code != HttpURLConnection.HTTP_NOT_FOUND) {
            throw IOException("could not revoke the token: HTTP ${response.code}")
        }
    }

    /** The login of the user that [accessToken] acts for. */
    fun login(accessToken: String): String {
        val response = request("GET", "$API_URL/user", apiHeaders + bearer(accessToken), body = null)
        if (response.code != HttpURLConnection.HTTP_OK) throw IOException("could not read the user: HTTP ${response.code}")
        return Json.parseToJsonElement(response.body).jsonObject.getValue("login").jsonPrimitive.content
    }

    /**
     * Whether the user can push to the repository [fullName] through the app. A user access token
     * of a GitHub App only reaches the repositories that the app is installed on.
     */
    fun checkRepository(accessToken: String, fullName: String): RepositoryCheck {
        val response = request("GET", "$API_URL/repos/$fullName", apiHeaders + bearer(accessToken), body = null)
        return when (response.code) {
            HttpURLConnection.HTTP_OK -> {
                val permissions = Json.parseToJsonElement(response.body).jsonObject["permissions"] as? JsonObject
                if (permissions?.get("push")?.jsonPrimitive?.booleanOrNull == true) RepositoryCheck.OK else RepositoryCheck.READ_ONLY
            }
            HttpURLConnection.HTTP_NOT_FOUND, HttpURLConnection.HTTP_FORBIDDEN -> RepositoryCheck.NOT_FOUND
            else -> RepositoryCheck.FAILED
        }
    }

    /**
     * The repositories, as `owner/name`, that the app is installed on and the user of
     * [accessToken] can access, up to 100 of each installation.
     */
    fun installedRepositories(accessToken: String): List<String> {
        val installations = getJson("$API_URL/user/installations?per_page=100", accessToken)["installations"] as? JsonArray
        return installations.orEmpty().flatMap { installation ->
            val id = installation.jsonObject.getValue("id").jsonPrimitive.content
            val repositories = getJson("$API_URL/user/installations/$id/repositories?per_page=100", accessToken)["repositories"] as? JsonArray
            repositories.orEmpty().map { it.jsonObject.getValue("full_name").jsonPrimitive.content }
        }
    }

    private fun getJson(url: String, accessToken: String): JsonObject {
        val response = request("GET", url, apiHeaders + bearer(accessToken), body = null)
        if (response.code != HttpURLConnection.HTTP_OK) throw IOException("could not read $url: HTTP ${response.code}")
        return Json.parseToJsonElement(response.body).jsonObject
    }

    private fun requestTokens(vararg parameters: Pair<String, String>): Tokens {
        val requestedAt = now()
        val response = request(
            "POST",
            TOKEN_URL,
            mapOf("Accept" to "application/json", "Content-Type" to "application/x-www-form-urlencoded"),
            form("client_id" to clientId, "client_secret" to clientSecret, *parameters),
        )
        if (response.code != HttpURLConnection.HTTP_OK) throw IOException("token request failed: HTTP ${response.code}")
        return parseTokens(response.body, requestedAt)
    }

    private val apiHeaders = mapOf("Accept" to "application/vnd.github+json", "X-GitHub-Api-Version" to API_VERSION)

    private fun bearer(token: String) = "Authorization" to "Bearer $token"

    private class Response(val code: Int, val body: String, val retryAfterSeconds: Long?)

    private fun request(method: String, url: String, headers: Map<String, String>, body: String?): Response {
        var delay = 1_000L
        for (attempt in 1..MAX_ATTEMPTS) {
            val response = try {
                send(method, url, headers, body)
            } catch (error: IOException) {
                if (attempt == MAX_ATTEMPTS) throw error
                null
            }
            val busy = response == null || response.code == 429 || response.code >= 500 ||
                (response.code == HttpURLConnection.HTTP_FORBIDDEN && response.retryAfterSeconds != null)
            if (!busy || attempt == MAX_ATTEMPTS) return checkNotNull(response)
            Thread.sleep(response?.retryAfterSeconds?.let { (it * 1000).coerceIn(0, MAX_RETRY_DELAY_MILLIS) } ?: delay)
            delay *= 2
        }
        error("unreachable")
    }

    private fun send(method: String, url: String, headers: Map<String, String>, body: String?): Response {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.setRequestProperty("User-Agent", "Kakeibo")
            headers.forEach(connection::setRequestProperty)
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = connection.responseCode
            val stream = if (code >= 400) connection.errorStream else connection.inputStream
            return Response(
                code,
                stream?.use { it.readBytes().decodeToString() }.orEmpty(),
                connection.getHeaderField("Retry-After")?.toLongOrNull(),
            )
        } finally {
            connection.disconnect()
        }
    }
}

private fun form(vararg parameters: Pair<String, String>): String =
    parameters.joinToString("&") { (name, value) -> "$name=${URLEncoder.encode(value, "UTF-8")}" }
