package jp.cjbc.kakeibo.sync

import java.security.MessageDigest
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GitHubClientTest {
    @Test
    fun theChallengeIsTheVerifiersSha256() {
        val pkce = Pkce.generate()
        val digest = MessageDigest.getInstance("SHA-256").digest(pkce.verifier.toByteArray())
        assertEquals(Base64.getUrlEncoder().withoutPadding().encodeToString(digest), pkce.challenge)
        // RFC 7636 allows 43 to 128 unreserved characters.
        assertTrue(pkce.verifier.length in 43..128)
        assertTrue(pkce.verifier.all { it.isLetterOrDigit() || it in "-._~" })
        assertNotEquals(pkce.verifier, Pkce.generate().verifier)
    }

    @Test
    fun readsExpiringTokens() {
        val tokens = parseTokens(
            """{"access_token":"ghu_a","expires_in":28800,"refresh_token":"ghr_b","refresh_token_expires_in":15897600,"token_type":"bearer","scope":""}""",
            now = 1_000,
        )
        assertEquals(Tokens("ghu_a", 1_000 + 28_800_000L, "ghr_b", 1_000 + 15_897_600_000L), tokens)
    }

    @Test
    fun readsTokensThatDoNotExpire() {
        assertEquals(Tokens("ghu_a", null, null, null), parseTokens("""{"access_token":"ghu_a","token_type":"bearer"}""", now = 0))
    }

    @Test
    fun throwsTheErrorThatGitHubAnswers() {
        val error = assertFailsWith<GitHubAuthException> {
            parseTokens("""{"error":"bad_refresh_token","error_description":"The refresh token passed is incorrect or expired."}""", now = 0)
        }
        assertEquals("bad_refresh_token", error.error)
    }

    @Test
    fun readsRepositoryNames() {
        assertEquals("me/ledger", repositoryName(" me/ledger "))
        assertEquals("me/my.ledger-data", repositoryName("https://github.com/me/my.ledger-data.git"))
        assertNull(repositoryName("ledger"))
        assertNull(repositoryName("me/ledger/tree/main"))
        assertNull(repositoryName("-me/ledger"))
    }

    @Test
    fun theAuthorizeUrlAsksForTheS256Challenge() {
        val pkce = Pkce("verifier", "challenge")
        val url = GitHubClient("Iv1.abc", "secret").authorizeUrl("jp.cjbc.kakeibo://oauth/callback", "state", pkce)
        assertEquals(
            "https://github.com/login/oauth/authorize?client_id=Iv1.abc" +
                "&redirect_uri=jp.cjbc.kakeibo%3A%2F%2Foauth%2Fcallback&state=state" +
                "&code_challenge=challenge&code_challenge_method=S256",
            url,
        )
    }
}
