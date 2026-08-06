package dev.hamo.AnimeDubsLibrary.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.SecureRandom
import java.util.Base64

import dev.hamo.AnimeDubsLibrary.BuildConfig

object AuthManager {

    // AniList Configurations
    private val ANILIST_CLIENT_ID = BuildConfig.ANILIST_CLIENT_ID
    private const val ANILIST_AUTH_URL = "https://anilist.co/api/v2/oauth/authorize"

    // MAL Configurations
    private val MAL_CLIENT_ID = BuildConfig.MAL_CLIENT_ID
    private val MAL_CLIENT_SECRET = BuildConfig.MAL_CLIENT_SECRET
    private const val MAL_AUTH_URL = "https://myanimelist.net/v1/oauth2/authorize"
    private const val MAL_TOKEN_URL = "https://myanimelist.net/v1/oauth2/token"
    private const val MAL_REDIRECT_URI = "animedubslibrary://mal-auth"

    // State holding for PKCE
    private var currentCodeChallenge: String? = null
    
    private val client = OkHttpClient()

    /**
     * Starts the AniList Implicit Grant Flow. 
     * The token will be returned in the URL fragment.
     */
    fun startAnilistAuth(context: Context) {
        val url = "$ANILIST_AUTH_URL?client_id=$ANILIST_CLIENT_ID&response_type=token"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Starts the MAL OAuth 2.0 Flow with PKCE.
     */
    fun startMalAuth(context: Context) {
        val challenge = generateCodeChallenge()
        currentCodeChallenge = challenge // Store it temporarily for the token exchange

        val url = Uri.parse(MAL_AUTH_URL).buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", MAL_CLIENT_ID)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("state", "request123")
            // Optional: specify redirect_uri if your client is set to strict mode on MAL's side
            .appendQueryParameter("redirect_uri", MAL_REDIRECT_URI)
            .build()

        val intent = Intent(Intent.ACTION_VIEW, url).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Parses the incoming deep link.
     * Returns true if it was an auth deep link.
     */
    suspend fun handleIntent(
        intent: Intent?,
        onAnilistTokenReceived: (String) -> Unit,
        onMalTokenReceived: (String) -> Unit,
        onError: (String) -> Unit
    ): Boolean {
        val data = intent?.data ?: return false

        if (data.scheme == "animedubslibrary") {
            when (data.host) {
                "anilist-auth" -> {
                    // Implicit grant puts the token in the fragment: #access_token=...
                    val fragment = data.fragment
                    if (fragment != null && fragment.contains("access_token=")) {
                        val token = fragment.split("&")
                            .firstOrNull { it.startsWith("access_token=") }
                            ?.substringAfter("access_token=")
                        
                        if (token != null) {
                            onAnilistTokenReceived(token)
                            return true
                        }
                    }
                    onError("Failed to parse AniList token from response.")
                    return true
                }
                
                "mal-auth" -> {
                    val code = data.getQueryParameter("code")
                    if (code != null && currentCodeChallenge != null) {
                        exchangeMalCodeForToken(code, currentCodeChallenge!!, onMalTokenReceived, onError)
                        currentCodeChallenge = null // Reset
                        return true
                    } else if (data.getQueryParameter("error") != null) {
                        onError("MAL Auth Error: ${data.getQueryParameter("error")}")
                        return true
                    }
                    onError("Invalid MAL response or missing code challenge.")
                    return true
                }
            }
        }
        return false
    }

    private suspend fun exchangeMalCodeForToken(
        code: String,
        codeVerifier: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            try {
                val formBody = FormBody.Builder()
                    .add("client_id", MAL_CLIENT_ID)
                    .add("client_secret", MAL_CLIENT_SECRET)
                    .add("code", code)
                    .add("code_verifier", codeVerifier)
                    .add("grant_type", "authorization_code")
                    .add("redirect_uri", MAL_REDIRECT_URI)
                    .build()

                val request = Request.Builder()
                    .url(MAL_TOKEN_URL)
                    .post(formBody)
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                if (response.isSuccessful && responseBody != null) {
                    val json = JSONObject(responseBody)
                    val accessToken = json.optString("access_token")
                    if (accessToken.isNotEmpty()) {
                        withContext(Dispatchers.Main) { onSuccess(accessToken) }
                    } else {
                        withContext(Dispatchers.Main) { onError("Missing access token in MAL response") }
                    }
                } else {
                    withContext(Dispatchers.Main) { onError("MAL Token exchange failed: $responseBody") }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onError("Exception during MAL exchange: ${e.message}") }
            }
        }
    }

    private fun generateCodeChallenge(): String {
        val bytes = ByteArray(96) // 128 characters of base64
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
