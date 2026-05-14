package com.kryptos.vault.`data`

import androidx.room.InvalidationTracker
import androidx.room.RoomOpenDelegate
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.room.util.TableInfo
import androidx.room.util.TableInfo.Companion.read
import androidx.room.util.dropFtsSyncTriggers
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import javax.`annotation`.processing.Generated
import kotlin.Lazy
import kotlin.String
import kotlin.Suppress
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.MutableList
import kotlin.collections.MutableMap
import kotlin.collections.MutableSet
import kotlin.collections.Set
import kotlin.collections.mutableListOf
import kotlin.collections.mutableMapOf
import kotlin.collections.mutableSetOf
import kotlin.reflect.KClass

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class VaultDatabase_Impl : VaultDatabase() {
  private val _vaultDao: Lazy<VaultDao> = lazy {
    VaultDao_Impl(this)
  }

  protected override fun createOpenDelegate(): RoomOpenDelegate {
    val _openDelegate: RoomOpenDelegate = object : RoomOpenDelegate(1,
        "f1da0a6015b7bba7e5ff6787363f9f51", "19d490001d39a39d993f0ce458a7415a") {
      public override fun createAllTables(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `vault_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `template` TEXT NOT NULL, `title` TEXT NOT NULL, `fieldsJson` TEXT NOT NULL, `attachment` BLOB, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        connection.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'f1da0a6015b7bba7e5ff6787363f9f51')")
      }

      public override fun dropAllTables(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `vault_entries`")
      }

      public override fun onCreate(connection: SQLiteConnection) {
      }

      public override fun onOpen(connection: SQLiteConnection) {
        internalInitInvalidationTracker(connection)
      }

      public override fun onPreMigrate(connection: SQLiteConnection) {
        dropFtsSyncTriggers(connection)
      }

      public override fun onPostMigrate(connection: SQLiteConnection) {
      }

      public override fun onValidateSchema(connection: SQLiteConnection):
          RoomOpenDelegate.ValidationResult {
        val _columnsVaultEntries: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsVaultEntries.put("id", TableInfo.Column("id", "INTEGER", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsVaultEntries.put("template", TableInfo.Column("template", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsVaultEntries.put("title", TableInfo.Column("title", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsVaultEntries.put("fieldsJson", TableInfo.Column("fieldsJson", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsVaultEntries.put("attachment", TableInfo.Column("attachment", "BLOB", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsVaultEntries.put("createdAt", TableInfo.Column("createdAt", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsVaultEntries.put("updatedAt", TableInfo.Column("updatedAt", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysVaultEntries: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesVaultEntries: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoVaultEntries: TableInfo = TableInfo("vault_entries", _columnsVaultEntries,
            _foreignKeysVaultEntries, _indicesVaultEntries)
        val _existingVaultEntries: TableInfo = read(connection, "vault_entries")
        if (!_infoVaultEntries.equals(_existingVaultEntries)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |vault_entries(com.kryptos.vault.data.VaultEntry).
              | Expected:
              |""".trimMargin() + _infoVaultEntries + """
              |
              | Found:
              |""".trimMargin() + _existingVaultEntries)
        }
        return RoomOpenDelegate.ValidationResult(true, null)
      }
    }
    return _openDelegate
  }

  protected override fun createInvalidationTracker(): InvalidationTracker {
    val _shadowTablesMap: MutableMap<String, String> = mutableMapOf()
    val _viewTables: MutableMap<String, Set<String>> = mutableMapOf()
    return InvalidationTracker(this, _shadowTablesMap, _viewTables, "vault_entries")
  }

  public override fun clearAllTables() {
    super.performClear(false, "vault_entries")
  }

  protected override fun getRequiredTypeConverterClasses(): Map<KClass<*>, List<KClass<*>>> {
    val _typeConvertersMap: MutableMap<KClass<*>, List<KClass<*>>> = mutableMapOf()
    _typeConvertersMap.put(VaultDao::class, VaultDao_Impl.getRequiredConverters())
    return _typeConvertersMap
  }

  public override fun getRequiredAutoMigrationSpecClasses(): Set<KClass<out AutoMigrationSpec>> {
    val _autoMigrationSpecsSet: MutableSet<KClass<out AutoMigrationSpec>> = mutableSetOf()
    return _autoMigrationSpecsSet
  }

  public override
      fun createAutoMigrations(autoMigrationSpecs: Map<KClass<out AutoMigrationSpec>, AutoMigrationSpec>):
      List<Migration> {
    val _autoMigrations: MutableList<Migration> = mutableListOf()
    return _autoMigrations
  }

  public override fun vaultDao(): VaultDao = _vaultDao.value
}
