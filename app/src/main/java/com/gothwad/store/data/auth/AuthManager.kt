package com.gothwad.store.data.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.browser.customtabs.CustomTabsIntent
import com.gothwad.store.BuildConfig
import com.gothwad.store.data.model.GothwadDeveloper
import com.gothwad.store.data.model.GothwadUser
import com.gothwad.store.data.model.MeResponse
import com.gothwad.store.data.remote.StoreNetwork
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class SignedIn(
        val user: GothwadUser,
        val developer: GothwadDeveloper? = null,
        val isAdmin: Boolean = false,
    ) : AuthState
}

/**
 * "Sign in with Gothwad" for the Android client.
 *
 * OAuth 2.0 Authorization Code + PKCE, exactly like the web storefront: the
 * browser (Custom Tab) hits Accounts, Accounts redirects back to
 * `kiwistore://oauth/callback`, and the app exchanges the code for tokens.
 * No client secret ever ships in the APK, and no Supabase key is involved.
 *
 * Transition: builds released before the scheme rename registered
 * `gothwadstore://oauth/callback`. [handleRedirect] still accepts that scheme
 * so a sign-in started by an old APK can finish after updating; the matching
 * legacy intent filter lives in `AndroidManifest.xml`. Both go away together.
 */
class AuthManager(
    private val context: Context,
    private val accountsBaseUrl: String = BuildConfig.ACCOUNTS_API_BASE_URL,
    httpClient: OkHttpClient = StoreNetwork.downloadClient,
) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val refreshMutex = Mutex()

    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val client = httpClient.newBuilder()
        .callTimeout(45, TimeUnit.SECONDS)
        .build()

    val isSignedIn: Boolean get() = _state.value is AuthState.SignedIn

    fun attachTokenProvider() {
        StoreNetwork.tokenProvider = { accessTokenBlocking() }
    }

    // ── Sign-in ────────────────────────────────────────────────────────────
    fun startSignIn(scope: String = DEFAULT_SCOPE): Result<Unit> = runCatching {
        val verifier = randomUrlSafe(32)
        val challenge = base64Url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
        val state = randomUrlSafe(16)
        rememberPendingSignIn(verifier, state)

        val url = authorizeUrl(scope, state, challenge)

        val customTabs = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
        customTabs.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            customTabs.launchUrl(context, url)
        } catch (_: Throwable) {
            val fallback = Intent(Intent.ACTION_VIEW, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(fallback)
        }
    }

    /**
     * Stores a pending PKCE session. [startSignIn] uses it, and unit tests use
     * it to exercise [exchangeCode] without opening a Custom Tab.
     */
    internal fun rememberPendingSignIn(verifier: String, state: String) {
        prefs.edit()
            .putString(KEY_VERIFIER, verifier)
            .putString(KEY_STATE, state)
            .putLong(KEY_STARTED, System.currentTimeMillis())
            .apply()
    }

    /** Accounts consent URL; `redirect_uri` must match [BuildConfig.OAUTH_REDIRECT_URI]. */
    internal fun authorizeUrl(scope: String, state: String, codeChallenge: String): Uri =
        Uri.parse("${accountsBaseUrl}oauth/authorize").buildUpon()
            .appendQueryParameter("client_id", BuildConfig.OAUTH_CLIENT_ID)
            .appendQueryParameter("redirect_uri", BuildConfig.OAUTH_REDIRECT_URI)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", scope)
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .build()

    /**
     * @return true when the deep link belonged to the OAuth callback.
     *
     * Only the current `kiwistore` scheme (plus the legacy `gothwadstore`
     * scheme for the transition window) is accepted; anything else is ignored.
     */
    fun handleRedirect(uri: Uri): Boolean {
        if (!isOAuthCallback(uri)) return false

        val error = uri.getQueryParameter("error")
        if (error != null) {
            _lastError.value = "Gothwad sign-in was cancelled ($error)."
            return true
        }
        val code = uri.getQueryParameter("code") ?: run {
            _lastError.value = "Gothwad sign-in returned no authorization code."
            return true
        }
        val returnedState = uri.getQueryParameter("state")
        val expectedState = prefs.getString(KEY_STATE, null)
        if (expectedState != null && returnedState != expectedState) {
            _lastError.value = "Sign-in could not be verified (state mismatch). Please try again."
            return true
        }
        _state.value = AuthState.Loading
        AppGraphHolder.scope.launchCompat {
            runCatching { exchangeCode(code) }
                .onSuccess { fetchMe(silent = false) }
                .onFailure { error ->
                    _lastError.value = error.message ?: "Gothwad sign-in failed."
                    _state.value = AuthState.SignedOut
                }
        }
        return true
    }

    /**
     * True only for `kiwistore://oauth/callback` and the transition-only
     * `gothwadstore://oauth/callback`. The host and path are checked too, so a
     * look-alike scheme or a different deep link never reaches the token code.
     */
    internal fun isOAuthCallback(uri: Uri): Boolean =
        uri.host == "oauth" &&
            uri.path.orEmpty().startsWith("/callback") &&
            uri.scheme in OAUTH_SCHEMES

    suspend fun exchangeCode(code: String) {
        val verifier = prefs.getString(KEY_VERIFIER, null)
            ?: error("Sign-in session expired. Please tap Sign in again.")
        val body = JSONObject()
            .put("grant_type", "authorization_code")
            .put("code", code)
            .put("redirect_uri", BuildConfig.OAUTH_REDIRECT_URI)
            .put("client_id", BuildConfig.OAUTH_CLIENT_ID)
            .put("code_verifier", verifier)
            .toString()
        val json = postJson("${accountsBaseUrl}oauth/token", body, null)
        val access = json.optString("access_token").takeIf { it.isNotBlank() }
            ?: error("Gothwad Accounts returned no access token.")
        val refresh = json.optString("refresh_token").takeIf { it.isNotBlank() }
        val expiresIn = json.optLong("expires_in", 3600L)
        prefs.edit()
            .putString(KEY_ACCESS, access)
            .putString(KEY_REFRESH, refresh ?: prefs.getString(KEY_REFRESH, null))
            .putLong(KEY_EXPIRES_AT, System.currentTimeMillis() + expiresIn * 1000L)
            .remove(KEY_VERIFIER)
            .remove(KEY_STATE)
            .apply()
    }

    // ── Session ────────────────────────────────────────────────────────────
    suspend fun restoreSession() {
        if (prefs.getString(KEY_REFRESH, null) == null && prefs.getString(KEY_ACCESS, null) == null) {
            _state.value = AuthState.SignedOut
            return
        }
        _state.value = AuthState.Loading
        fetchMe(silent = true)
    }

    suspend fun fetchMe(silent: Boolean) {
        try {
            val userInfo = fetchUserInfo()
            val me = try {
                StoreNetwork.api.me()
            } catch (_: Throwable) {
                null
            }
            val user = me?.user ?: userInfo
            if (user == null) {
                _state.value = AuthState.SignedOut
                return
            }
            _state.value = AuthState.SignedIn(user, me?.developer, me?.isAdmin == true)
        } catch (error: Throwable) {
            if (!silent) _lastError.value = error.message ?: "Could not load your Gothwad profile."
            _state.value = AuthState.SignedOut
        }
    }

    suspend fun signOut() {
        prefs.edit().clear().apply()
        _state.value = AuthState.SignedOut
    }

    fun clearError() {
        _lastError.value = null
    }

    // ── Tokens ─────────────────────────────────────────────────────────────
    private fun accessTokenBlocking(): String? {
        val token = prefs.getString(KEY_ACCESS, null)
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
        val needsRefresh = token.isNullOrBlank() || System.currentTimeMillis() > expiresAt - 60_000
        if (!needsRefresh) return token
        val refresh = prefs.getString(KEY_REFRESH, null) ?: return token
        return kotlinx.coroutines.runBlocking {
            refreshMutex.withLock {
                val current = prefs.getString(KEY_ACCESS, null)
                val currentExpiry = prefs.getLong(KEY_EXPIRES_AT, 0L)
                if (!current.isNullOrBlank() && System.currentTimeMillis() < currentExpiry - 60_000) {
                    current
                } else {
                    runCatching { refreshTokens(refresh) }.getOrNull() ?: current
                }
            }
        }
    }

    private suspend fun refreshTokens(refreshToken: String): String {
        val body = JSONObject()
            .put("grant_type", "refresh_token")
            .put("refresh_token", refreshToken)
            .put("client_id", BuildConfig.OAUTH_CLIENT_ID)
            .toString()
        val json = postJson("${accountsBaseUrl}oauth/token", body, null)
        val access = json.optString("access_token").takeIf { it.isNotBlank() }
            ?: error("Token refresh failed.")
        val newRefresh = json.optString("refresh_token").takeIf { it.isNotBlank() } ?: refreshToken
        prefs.edit()
            .putString(KEY_ACCESS, access)
            .putString(KEY_REFRESH, newRefresh)
            .putLong(KEY_EXPIRES_AT, System.currentTimeMillis() + json.optLong("expires_in", 3600L) * 1000L)
            .apply()
        return access
    }

    private suspend fun fetchUserInfo(): GothwadUser? {
        val token = accessTokenBlocking() ?: return null
        val request = Request.Builder()
            .url("${accountsBaseUrl}oauth/userinfo")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .build()
        return withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val json = JSONObject(response.body?.string().orEmpty())
                GothwadUser(
                    id = json.optString("sub"),
                    username = json.optString("preferred_username"),
                    name = json.optString("name"),
                    email = json.optString("email"),
                    picture = json.optString("picture").takeIf { it.isNotBlank() && it != "null" },
                )
            }
        }
    }

    private suspend fun postJson(url: String, body: String, token: String?): JSONObject =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .apply { token?.let { header("Authorization", "Bearer $it") } }
                .build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val message = runCatching { JSONObject(text).optString("error_description") }
                        .getOrNull()?.takeIf { it.isNotBlank() }
                        ?: runCatching { JSONObject(text).optString("error") }
                            .getOrNull()?.takeIf { it.isNotBlank() }
                    throw IllegalStateException(message ?: "Gothwad Accounts answered ${response.code}.")
                }
                JSONObject(text)
            }
        }

    private fun randomUrlSafe(bytes: Int): String {
        val buffer = ByteArray(bytes)
        SecureRandom().nextBytes(buffer)
        return base64Url(buffer)
    }

    private fun base64Url(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

    internal companion object {
        /** Current OAuth redirect scheme; must match `BuildConfig.OAUTH_REDIRECT_URI`. */
        const val OAUTH_SCHEME = "kiwistore"

        /**
         * Transition only: builds released before the rename sent Accounts back
         * with this scheme. It is accepted so their in-flight sign-ins can
         * finish after the update. Remove it together with the legacy intent
         * filter in `AndroidManifest.xml` once no such build is in circulation.
         */
        const val LEGACY_OAUTH_SCHEME = "gothwadstore"

        private val OAUTH_SCHEMES = setOf(OAUTH_SCHEME, LEGACY_OAUTH_SCHEME)

        internal const val PREFS_NAME = "gothwad_accounts"
        internal const val KEY_ACCESS = "access_token"
        internal const val KEY_REFRESH = "refresh_token"
        internal const val KEY_EXPIRES_AT = "expires_at"
        internal const val KEY_VERIFIER = "pkce_verifier"
        internal const val KEY_STATE = "oauth_state"
        internal const val KEY_STARTED = "started_at"

        /** The real scope set is stored when signing in; kept for readable signing. */
        internal const val DEFAULT_SCOPE = "profile email offline_access"
    }
}

/** Small bridge so auth can launch work without pulling in the whole graph. */
internal object AppGraphHolder {
    val scope: CoroutineScope get() = com.gothwad.store.core.AppGraph.appScope
}

internal fun CoroutineScope.launchCompat(block: suspend () -> Unit) {
    launch { block() }
}
