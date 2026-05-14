package com.kryptos.vault.`data`

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import javax.`annotation`.processing.Generated
import kotlin.ByteArray
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class VaultDao_Impl(
  __db: RoomDatabase,
) : VaultDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfVaultEntry: EntityInsertAdapter<VaultEntry>

  private val __converters: Converters = Converters()

  private val __deleteAdapterOfVaultEntry: EntityDeleteOrUpdateAdapter<VaultEntry>

  private val __updateAdapterOfVaultEntry: EntityDeleteOrUpdateAdapter<VaultEntry>
  init {
    this.__db = __db
    this.__insertAdapterOfVaultEntry = object : EntityInsertAdapter<VaultEntry>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `vault_entries` (`id`,`template`,`title`,`fieldsJson`,`attachment`,`createdAt`,`updatedAt`) VALUES (nullif(?, 0),?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: VaultEntry) {
        statement.bindLong(1, entity.id)
        val _tmp: String = __converters.fromTemplate(entity.template)
        statement.bindText(2, _tmp)
        statement.bindText(3, entity.title)
        statement.bindText(4, entity.fieldsJson)
        val _tmpAttachment: ByteArray? = entity.attachment
        if (_tmpAttachment == null) {
          statement.bindNull(5)
        } else {
          statement.bindBlob(5, _tmpAttachment)
        }
        statement.bindLong(6, entity.createdAt)
        statement.bindLong(7, entity.updatedAt)
      }
    }
    this.__deleteAdapterOfVaultEntry = object : EntityDeleteOrUpdateAdapter<VaultEntry>() {
      protected override fun createQuery(): String = "DELETE FROM `vault_entries` WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: VaultEntry) {
        statement.bindLong(1, entity.id)
      }
    }
    this.__updateAdapterOfVaultEntry = object : EntityDeleteOrUpdateAdapter<VaultEntry>() {
      protected override fun createQuery(): String =
          "UPDATE OR ABORT `vault_entries` SET `id` = ?,`template` = ?,`title` = ?,`fieldsJson` = ?,`attachment` = ?,`createdAt` = ?,`updatedAt` = ? WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: VaultEntry) {
        statement.bindLong(1, entity.id)
        val _tmp: String = __converters.fromTemplate(entity.template)
        statement.bindText(2, _tmp)
        statement.bindText(3, entity.title)
        statement.bindText(4, entity.fieldsJson)
        val _tmpAttachment: ByteArray? = entity.attachment
        if (_tmpAttachment == null) {
          statement.bindNull(5)
        } else {
          statement.bindBlob(5, _tmpAttachment)
        }
        statement.bindLong(6, entity.createdAt)
        statement.bindLong(7, entity.updatedAt)
        statement.bindLong(8, entity.id)
      }
    }
  }

  public override suspend fun insert(entry: VaultEntry): Long = performSuspending(__db, false, true)
      { _connection ->
    val _result: Long = __insertAdapterOfVaultEntry.insertAndReturnId(_connection, entry)
    _result
  }

  public override suspend fun delete(entry: VaultEntry): Unit = performSuspending(__db, false, true)
      { _connection ->
    __deleteAdapterOfVaultEntry.handle(_connection, entry)
  }

  public override suspend fun update(entry: VaultEntry): Unit = performSuspending(__db, false, true)
      { _connection ->
    __updateAdapterOfVaultEntry.handle(_connection, entry)
  }

  public override fun observeAll(): Flow<List<VaultEntry>> {
    val _sql: String = "SELECT * FROM vault_entries ORDER BY updatedAt DESC"
    return createFlow(__db, false, arrayOf("vault_entries")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfTemplate: Int = getColumnIndexOrThrow(_stmt, "template")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfFieldsJson: Int = getColumnIndexOrThrow(_stmt, "fieldsJson")
        val _columnIndexOfAttachment: Int = getColumnIndexOrThrow(_stmt, "attachment")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updatedAt")
        val _result: MutableList<VaultEntry> = mutableListOf()
        while (_stmt.step()) {
          val _item: VaultEntry
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpTemplate: Template
          val _tmp: String
          _tmp = _stmt.getText(_columnIndexOfTemplate)
          _tmpTemplate = __converters.toTemplate(_tmp)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpFieldsJson: String
          _tmpFieldsJson = _stmt.getText(_columnIndexOfFieldsJson)
          val _tmpAttachment: ByteArray?
          if (_stmt.isNull(_columnIndexOfAttachment)) {
            _tmpAttachment = null
          } else {
            _tmpAttachment = _stmt.getBlob(_columnIndexOfAttachment)
          }
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          _item =
              VaultEntry(_tmpId,_tmpTemplate,_tmpTitle,_tmpFieldsJson,_tmpAttachment,_tmpCreatedAt,_tmpUpdatedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getById(id: Long): VaultEntry? {
    val _sql: String = "SELECT * FROM vault_entries WHERE id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, id)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfTemplate: Int = getColumnIndexOrThrow(_stmt, "template")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfFieldsJson: Int = getColumnIndexOrThrow(_stmt, "fieldsJson")
        val _columnIndexOfAttachment: Int = getColumnIndexOrThrow(_stmt, "attachment")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updatedAt")
        val _result: VaultEntry?
        if (_stmt.step()) {
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpTemplate: Template
          val _tmp: String
          _tmp = _stmt.getText(_columnIndexOfTemplate)
          _tmpTemplate = __converters.toTemplate(_tmp)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpFieldsJson: String
          _tmpFieldsJson = _stmt.getText(_columnIndexOfFieldsJson)
          val _tmpAttachment: ByteArray?
          if (_stmt.isNull(_columnIndexOfAttachment)) {
            _tmpAttachment = null
          } else {
            _tmpAttachment = _stmt.getBlob(_columnIndexOfAttachment)
          }
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          _result =
              VaultEntry(_tmpId,_tmpTemplate,_tmpTitle,_tmpFieldsJson,_tmpAttachment,_tmpCreatedAt,_tmpUpdatedAt)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
