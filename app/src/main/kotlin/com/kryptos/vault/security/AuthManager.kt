package com.kryptos.vault.security

import android.content.Context
import androidx.annotation.Keep
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.fmz.kryptos.R

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Google Sign-In via Credential Manager. The ID token is persisted in an encrypted prefs file
 * (Android Keystore-backed) and is later usable to authorise Drive AppData backups.
 */
@Keep
class AuthManager(private val context: Context) {

    @Keep
    data class Account(val id: String, val email: String?, val displayName: String?, val photoUrl: String?)

    private val _currentAccount = MutableStateFlow<Account?>(null)
    val accountFlow: StateFlow<Account?> = _currentAccount.asStateFlow()

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "kryptos_auth_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    init {
        // Initialize flow from disk on creation
        val id = prefs.getString(KEY_ID, null)
        if (id != null) {
            _currentAccount.value = Account(
                id = id,
                email = prefs.getString(KEY_EMAIL, null),
                displayName = prefs.getString(KEY_NAME, null),
                photoUrl = prefs.getString(KEY_PHOTO, null),
            )
        }
    }

    val currentAccount: Account?
        get() = accountFlow.value

    suspend fun signIn(activityContext: Context): Result<Account> {
        val manager = CredentialManager.create(activityContext)
        val webClientId = context.getString(R.string.google_web_client_id)
        
        val googleIdOption = GetGoogleIdOption.Builder()
            .setServerClientId(webClientId)
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .build()
            
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        var lastException: Exception? = null
        // Try up to 2 times to handle the "No credential" issue on first launch.
        repeat(2) { attempt ->
            try {
                val response = manager.getCredential(activityContext, request)
                val cred = response.credential
                
                if (cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val gid = GoogleIdTokenCredential.createFrom(cred.data)
                    
                    // Debug logging to understand the issue
                    val uri = gid.profilePictureUri
                    val idToken = gid.idToken
                    val photoFromUri = uri?.toString()
                    val photoFromJwt = idToken?.let { extractPictureFromIdToken(it) }
                    
                    android.util.Log.d("AuthManager", "=== PHOTO DEBUG ===")
                    android.util.Log.d("AuthManager", "profilePictureUri=$uri")
                    android.util.Log.d("AuthManager", "idToken present=${idToken != null} (len=${idToken?.length})")
                    android.util.Log.d("AuthManager", "photoFromUri=$photoFromUri")
                    android.util.Log.d("AuthManager", "photoFromJwt=$photoFromJwt")
                    android.util.Log.d("AuthManager", "displayName=${gid.displayName}")
                    android.util.Log.d("AuthManager", "id=${gid.id}")
                    
                    // Use first non-null source for photo
                    val photoUrl = photoFromUri ?: photoFromJwt ?: ""
                    
                    android.util.Log.d("AuthManager", "Final photoUrl selected: '$photoUrl'")
                    if (photoUrl.isEmpty()) {
                        android.util.Log.w("AuthManager", "WARNING: No photo URL found in any source (URI or JWT)")
                    }
                    
                    val account = Account(
                        id = gid.id,
                        email = gid.id.takeIf { it.contains('@') },
                        displayName = gid.displayName,
                        photoUrl = photoUrl,
                    )
                    android.util.Log.d("AuthManager", "Final photoUrl=$photoUrl")
                    prefs.edit()
                        .putString(KEY_ID, account.id)
                        .putString(KEY_EMAIL, account.email)
                        .putString(KEY_NAME, account.displayName)
                        .putString(KEY_PHOTO, account.photoUrl)
                        .putString(KEY_TOKEN, gid.idToken)
                        .commit()
                    _currentAccount.value = account
                    return Result.success(account)
                }
            } catch (e: NoCredentialException) {
                lastException = e
                android.util.Log.w("AuthManager", "No credential found (attempt ${attempt + 1})")
                if (attempt == 0) {
                    kotlinx.coroutines.delay(500) // Brief pause before retry
                }
            } catch (e: GetCredentialException) {
                android.util.Log.e("AuthManager", "Sign-in error", e)
                return Result.failure(Exception("Sign-in failed: ${e.message}"))
            } catch (e: Exception) {
                android.util.Log.e("AuthManager", "Unexpected error", e)
                return Result.failure(e)
            }
        }
        
        return Result.failure(lastException ?: Exception("No credentials available. Please ensure you have a Google account on this device."))
    }

    suspend fun signOut() {
        prefs.edit().clear().apply()
        _currentAccount.value = null
        runCatching {
            CredentialManager.create(context).clearCredentialState(
                androidx.credentials.ClearCredentialStateRequest()
            )
        }
    }

    private companion object {
        const val KEY_ID = "id"
        const val KEY_EMAIL = "email"
        const val KEY_NAME = "name"
        const val KEY_PHOTO = "photo"
        const val KEY_TOKEN = "id_token"
    }

    /** Extract profile picture URL from the ID token JWT payload. */
    private fun extractPictureFromIdToken(idToken: String): String? {
        return try {
            val parts = idToken.split(".")
            if (parts.size < 2) return null
            val payload = parts[1]
            val decoded = android.util.Base64.decode(
                payload.replace('-', '+').replace('_', '/'),
                android.util.Base64.DEFAULT
            )
            val jsonString = String(decoded, Charsets.UTF_8)
            android.util.Log.d("AuthManager", "Decoded JWT Payload: $jsonString")
            val json = org.json.JSONObject(jsonString)
            
            // Try different possible keys for the picture
            val pic = json.optString("picture", "")
                .ifBlank { json.optString("photo", "") }
                .ifBlank { json.optString("thumbnail", "") }
                
            pic.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            android.util.Log.w("AuthManager", "Failed to extract picture from ID token", e)
            null
        }
    }
}
