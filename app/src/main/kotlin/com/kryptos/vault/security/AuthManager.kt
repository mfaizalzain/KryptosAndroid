package com.kryptos.vault.security

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
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
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(webClientId)
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()

        return try {
            val response = manager.getCredential(activityContext, request)
            val cred = response.credential
            if (cred.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                return Result.failure(IllegalStateException("Unexpected credential type: ${cred.type}"))
            }
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
            Result.success(account)
        } catch (e: GetCredentialException) {
            Result.failure(e)
        } catch (e: GoogleIdTokenParsingException) {
            Result.failure(e)
        }
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
