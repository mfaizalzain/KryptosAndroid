package com.kryptos.vault.data

import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

class VaultRepository(private val db: VaultDatabase) {
    private val dao = db.vaultDao()
    
    fun observeAll(): Flow<List<VaultEntry>> = dao.observeAll()
    suspend fun get(id: Long) = dao.getById(id)
    suspend fun count(): Int = dao.count()
    suspend fun upsert(entry: VaultEntry): Long {
        android.util.Log.e("VaultRepository", "upsert: id=${entry.id}")
        return if (entry.id == 0L) {
            val newId = dao.insert(entry)
            android.util.Log.e("VaultRepository", "upsert: inserted new entry, newId=$newId")
            newId
        } else {
            dao.update(entry.copy(updatedAt = System.currentTimeMillis()))
            android.util.Log.e("VaultRepository", "upsert: updated existing entry, id=${entry.id}")
            entry.id
        }
    }
    suspend fun delete(entry: VaultEntry) = dao.delete(entry)

    suspend fun checkpoint() {
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").close()
    }
}
