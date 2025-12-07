package nl.astraeus.tmpl.db

import nl.astraeus.tmpl.log
import java.sql.Connection
import java.sql.SQLException
import java.sql.Timestamp
import java.util.concurrent.atomic.AtomicBoolean

sealed class Migration {
  class Query(
    val query: String
  ) : Migration() {
    override fun toString(): String {
      return query
    }
  }

  class Code(
    val code: (Connection) -> Unit
  ) : Migration() {
    override fun toString(): String {
      return code.toString()
    }
  }
}

val DATABASE_MIGRATIONS = arrayOf<Migration>(
  Migration.Query(
    """
  CREATE TABLE DATABASE_VERSION (
    ID INTEGER PRIMARY KEY,
    QUERY TEXT,
    EXECUTED TIMESTAMP
  )
  """.trimIndent()
  ),
  Migration.Query("SELECT sqlite_version()"),
)

object Migrations {
  var databaseVersionTableCreated = AtomicBoolean(false)

  fun updateDatabaseIfNeeded() {
    try {
      transaction { con ->
        con.prepareStatement(
          """
            SELECT MAX(ID) FROM DATABASE_VERSION
          """.trimIndent()
        ).use { ps ->
          ps.executeQuery().use { rs ->
            databaseVersionTableCreated.compareAndSet(false, true)

            if(rs.next()) {
              val maxId = rs.getInt(1)

              for (index in maxId + 1..<DATABASE_MIGRATIONS.size) {
                executeMigration(index)
              }
            }
          }
        }
      }
    } catch (e: SQLException) {
      if (databaseVersionTableCreated.compareAndSet(false, true)) {
        executeMigration(0)
        updateDatabaseIfNeeded()
      } else {
        throw e
      }
    }
  }

  private fun executeMigration(index: Int) {
    transaction { con ->
      log.debug {
        "Executing migration index - [DATABASE_MIGRATIONS[index]]"
      }
      val description = when(
        val migration = DATABASE_MIGRATIONS[index]
      ) {
        is Migration.Query -> {
          con.prepareStatement(migration.query).use { ps ->
            ps.execute()
          }

          migration.query
        }
        is Migration.Code -> {
          migration.code(con)

          migration.code.toString()
        }
      }
      con.prepareStatement("INSERT INTO DATABASE_VERSION VALUES (?, ?, ?)").use { ps ->
        ps.setInt(1, index)
        ps.setString(2, description)
        ps.setTimestamp(3, Timestamp(System.currentTimeMillis()))

        ps.execute()
      }
    }
  }

}
