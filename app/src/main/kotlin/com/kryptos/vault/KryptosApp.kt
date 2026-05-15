package com.kryptos.vault

import android.app.Application
import com.kryptos.vault.backup.DriveBackupManager
import com.kryptos.vault.data.VaultDatabase
import com.kryptos.vault.data.VaultRepository
import com.kryptos.vault.security.AuthManager
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.File
import java.security.Security

class KryptosApp : Application() {
    private var currentDatabase: VaultDatabase? = null
    private var currentRepository: VaultRepository? = null
    private var currentUserId: String? = null

    val authManager: AuthManager by lazy { AuthManager(this) }
    val backupManager: DriveBackupManager by lazy { DriveBackupManager(this) }
    val billingManager: BillingManager by lazy { BillingManager(this) }

    /** Returns the repository for the given userId. Creates a new DB connection if the user changed. */
    fun getRepository(userId: String?): VaultRepository {
        if (currentRepository != null && currentUserId == userId) {
            return currentRepository!!
        }

        currentDatabase?.close()
        
        // If we have a userId but no per-user DB yet, check if there's a legacy global DB to migrate.
        if (userId != null) {
            val sanitizedId = userId.replace(Regex("[^a-zA-Z0-9]"), "_")
            val legacyDb = getDatabasePath("kryptos.db")
            val userDb = getDatabasePath("kryptos_$sanitizedId.db")
            if (legacyDb.exists() && !userDb.exists()) {
                legacyDb.renameTo(userDb)
                // Also move WAL/SHM if they exist
                File(legacyDb.path + "-wal").renameTo(File(userDb.path + "-wal"))
                File(legacyDb.path + "-shm").renameTo(File(userDb.path + "-shm"))
            }
        }

        val db = VaultDatabase.build(this, userId)
        val repo = VaultRepository(db.vaultDao())
        
        currentDatabase = db
        currentRepository = repo
        currentUserId = userId
        
        return repo
    }

    /** Closes the active database connection. Useful before restoring from backup. */
    fun closeDatabase() {
        currentDatabase?.close()
        currentDatabase = null
        currentRepository = null
    }

    /** Wipes the encrypted vault and all secrets. Caller is responsible for restarting the process. */
    fun nukeAllData() {
        runCatching { currentDatabase?.close() }
        
        // Clear auth state via AuthManager first
        MainScope().launch {
            authManager.signOut()
        }

        val dbDir = getDatabasePath("kryptos.db").parentFile
        dbDir?.listFiles()?.filter { 
            it.name.startsWith("kryptos") && (it.name.endsWith(".db") || it.name.endsWith(".db-wal") || it.name.endsWith(".db-shm"))
        }?.forEach { it.delete() }

        listOf(
            "kryptos_secure_prefs", "kryptos_auth_prefs", "kryptos_backup_prefs",
            "kryptos_billing_prefs", "kryptos_backup_prefs"
        ).forEach {
            deleteSharedPreferences(it)
        }
    }

    override fun onCreate() {
        super.onCreate()
        System.loadLibrary("sqlcipher")
        // JMRTD relies on full BouncyCastle for some MAC/ciphers Android's stripped BC lacks.
        // Re-insert at highest priority so JCE picks our provider first.
        Security.removeProvider("BC")
        Security.insertProviderAt(BouncyCastleProvider(), 1)
    }
}
