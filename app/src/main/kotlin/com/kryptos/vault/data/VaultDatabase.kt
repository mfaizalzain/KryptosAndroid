package com.kryptos.vault.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.TypeConverters
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(entities = [VaultEntry::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class VaultDatabase : androidx.room.RoomDatabase() {
    abstract fun vaultDao(): VaultDao

    companion object {
        fun build(context: Context, userId: String? = null): VaultDatabase {
            val passphrase = KeyManager.getDatabasePassphrase(context, userId)
            val factory = SupportOpenHelperFactory(passphrase)
            val sanitizedId = userId?.replace(Regex("[^a-zA-Z0-9]"), "_")
            val dbName = if (sanitizedId == null) "kryptos.db" else "kryptos_$sanitizedId.db"
            return Room.databaseBuilder(context, VaultDatabase::class.java, dbName)
                .openHelperFactory(factory)
                .build()
        }
    }
}
