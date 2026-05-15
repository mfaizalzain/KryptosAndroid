package com.kryptos.vault.security

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.fmz.kryptos.R

/**
 * Google Sign-In via Credential Manager. The ID token is persisted in an encrypted prefs file
 * (Android Keystore-backed) and is later usable to authorise Drive AppData backups.
 */
class AuthManager(private val context: Context) {

    data class Account(val id: String, val email: String?, val displayName: String?, val photoUrl: String?)

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

    val currentAccount: Account?
        get() {
            val id = prefs.getString(KEY_ID, null) ?: return null
            return Account(
                id = id,
                email = prefs.getString(KEY_EMAIL, null),
                displayName = prefs.getString(KEY_NAME, null),
                photoUrl = prefs.getString(KEY_PHOTO, null),
            )
        }

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
                    val account = Account(
                        id = gid.id,
                        email = gid.id.takeIf { it.contains('@') },
                        displayName = gid.displayName,
                        photoUrl = gid.profilePictureUri?.toString(),
                    )
                    prefs.edit()
                        .putString(KEY_ID, account.id)
                        .putString(KEY_EMAIL, account.email)
                        .putString(KEY_NAME, account.displayName)
                        .putString(KEY_PHOTO, account.photoUrl)
                        .putString(KEY_TOKEN, gid.idToken)
                        .apply()
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
}
