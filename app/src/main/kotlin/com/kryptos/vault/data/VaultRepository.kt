package com.kryptos.vault.data

import kotlinx.coroutines.flow.Flow

class VaultRepository(private val dao: VaultDao) {
    fun observeAll(): Flow<List<VaultEntry>> = dao.observeAll()
    suspend fun get(id: Long) = dao.getById(id)
    suspend fun upsert(entry: VaultEntry): Long =
        if (entry.id == 0L) dao.insert(entry)
        else { dao.update(entry.copy(updatedAt = System.currentTimeMillis())); entry.id }
    suspend fun delete(entry: VaultEntry) = dao.delete(entry)
}
