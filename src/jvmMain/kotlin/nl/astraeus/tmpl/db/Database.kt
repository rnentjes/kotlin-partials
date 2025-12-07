package nl.astraeus.tmpl.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.Connection
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.collections.set
import kotlin.use

private val currentConnection = ThreadLocal<Connection>()

fun <T> transaction(
  block: (Connection) -> T
): T {
  val hasConnection = currentConnection.get() != null
  var oldConnection: Connection? = null

  if (!hasConnection) {
    currentConnection.set(Database.getConnection())
  }

  val connection = currentConnection.get()

  try {
    val result = block(connection)

    connection.commit()

    return result
  } finally {
    if (!hasConnection) {
      currentConnection.set(oldConnection)
      connection.close()
    }
  }
}

object Database {

  var ds: HikariDataSource? = null

  fun initialize(config: HikariConfig) {
    val properties = Properties()
    properties["journal_mode"] = "WAL"

    config.dataSourceProperties = properties
    config.addDataSourceProperty("cachePrepStmts", "true")
    config.addDataSourceProperty("prepStmtCacheSize", "250")
    config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048")

    ds = HikariDataSource(config)
    Migrations.databaseVersionTableCreated = AtomicBoolean(false)
    Migrations.updateDatabaseIfNeeded()
  }

  fun getConnection() = ds?.connection ?: error("Database has not been initialized!")

  fun vacuumDatabase() {
    getConnection().use {
      it.autoCommit = true

      it.prepareStatement("VACUUM").use { ps ->
        ps.executeUpdate()
      }
    }
  }

  fun closeDatabase() {
    ds?.close()
  }

}
