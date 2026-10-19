package com.gothwad.store

import android.app.Application
import android.content.Intent
import android.net.Uri
import com.gothwad.store.data.auth.AuthManager
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The OAuth redirect moved from `gothwadstore://` to `kiwistore://`. These
 * tests pin the new contract: the authorize request, the callback filter in the
 * manifest and the token exchange must all use `kiwistore://oauth/callback`,
 * while the old scheme is only tolerated for logins started by an old APK.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AuthRedirectTest {

    private lateinit var server: MockWebServer
    private lateinit var auth: AuthManager

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        auth = AuthManager(
            context = RuntimeEnvironment.getApplication(),
            accountsBaseUrl = server.url("/api/").toString(),
            httpClient = OkHttpClient(),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `build config points at kiwistore`() {
        assertEquals("kiwistore://oauth/callback", BuildConfig.OAUTH_REDIRECT_URI)
        assertEquals("kiwistore", Uri.parse(BuildConfig.OAUTH_REDIRECT_URI).scheme)
        assertEquals(AuthManager.OAUTH_SCHEME, Uri.parse(BuildConfig.OAUTH_REDIRECT_URI).scheme)
        assertEquals("kiwi-store", BuildConfig.OAUTH_CLIENT_ID)
    }

    @Test
    fun `authorize request asks accounts to return to kiwistore`() {
        val uri = auth.authorizeUrl(
            scope = "profile email offline_access",
            state = "state-123",
            codeChallenge = "challenge-abc",
        )

        assertTrue(uri.path.orEmpty().endsWith("oauth/authorize"))
        assertEquals("kiwistore://oauth/callback", uri.getQueryParameter("redirect_uri"))
        assertEquals("kiwi-store", uri.getQueryParameter("client_id"))
        assertEquals("code", uri.getQueryParameter("response_type"))
        assertEquals("state-123", uri.getQueryParameter("state"))
        assertEquals("challenge-abc", uri.getQueryParameter("code_challenge"))
        assertEquals("S256", uri.getQueryParameter("code_challenge_method"))
    }

    @Test
    fun `kiwistore callback is accepted`() {
        assertTrue(auth.handleRedirect(Uri.parse("kiwistore://oauth/callback?error=access_denied")))
        assertEquals(
            "Gothwad sign-in was cancelled (access_denied).",
            auth.lastError.value,
        )
    }

    @Test
    fun `legacy gothwadstore callback still works during the transition`() {
        assertTrue(auth.handleRedirect(Uri.parse("gothwadstore://oauth/callback?error=access_denied")))
        assertEquals(
            "Gothwad sign-in was cancelled (access_denied).",
            auth.lastError.value,
        )
    }

    @Test
    fun `foreign schemes, hosts and paths are ignored`() {
        assertFalse(auth.handleRedirect(Uri.parse("https://kiwistore.gothwadtech.com/oauth/callback?code=x")))
        assertFalse(auth.handleRedirect(Uri.parse("kiwistoreevil://oauth/callback?code=x")))
        assertFalse(auth.handleRedirect(Uri.parse("kiwistore://other/callback?code=x")))
        assertFalse(auth.handleRedirect(Uri.parse("kiwistore://oauth/reset?code=x")))
        assertFalse(auth.handleRedirect(Uri.parse("gothwadstore://other/callback?code=x")))
    }

    @Test
    fun `token exchange sends the kiwistore redirect uri`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"access_token":"access-1","refresh_token":"refresh-1","expires_in":3600}"""),
        )
        auth.rememberPendingSignIn(verifier = "verifier-abc", state = "state-123")

        runBlocking { auth.exchangeCode("code-xyz") }

        val recorded = server.takeRequest()
        assertEquals("/api/oauth/token", recorded.path)
        val body = JSONObject(recorded.body.readUtf8())
        assertEquals("authorization_code", body.getString("grant_type"))
        assertEquals("code-xyz", body.getString("code"))
        assertEquals("kiwistore://oauth/callback", body.getString("redirect_uri"))
        assertEquals("kiwi-store", body.getString("client_id"))
        assertEquals("verifier-abc", body.getString("code_verifier"))
    }

    @Test
    fun `manifest routes both schemes to the store activity`() {
        val context = RuntimeEnvironment.getApplication()
        val activity = MainActivity::class.java.name

        fun handlers(url: String) = context.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE),
            0,
        ).orEmpty().map { it.activityInfo.name }

        assertTrue(handlers("kiwistore://oauth/callback").contains(activity))
        assertTrue(handlers("kiwistore://oauth/callback?code=abc").contains(activity))
        // Transition only; removed together with AuthManager.LEGACY_OAUTH_SCHEME.
        assertTrue(handlers("gothwadstore://oauth/callback").contains(activity))
        assertFalse(handlers("kiwistore://oauth/other").contains(activity))
    }
}
