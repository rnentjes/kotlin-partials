package nl.astraeus.tmpl

import com.zaxxer.hikari.HikariConfig
import io.undertow.Undertow
import io.undertow.UndertowOptions
import io.undertow.predicate.Predicates
import io.undertow.server.handlers.encoding.ContentEncodingRepository
import io.undertow.server.handlers.encoding.EncodingHandler
import io.undertow.server.handlers.encoding.GzipEncodingProvider
import nl.astraeus.logger.Logger
import nl.astraeus.tmpl.db.Database
import nl.astraeus.tmpl.web.RequestHandler

val log = Logger()

fun main() {
  Thread.currentThread().uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { t, e ->
    log.warn(e) {
      e.message
    }
  }

  Runtime.getRuntime().addShutdownHook(
    object : Thread() {
      override fun run() {
        Database.vacuumDatabase()
        Database.closeDatabase()
      }
    }
  )

  Class.forName("nl.astraeus.jdbc.Driver")
  Database.initialize(HikariConfig().apply {
    driverClassName = "nl.astraeus.jdbc.Driver"
    jdbcUrl = "jdbc:stat:webServerPort=$JDBC_PORT:jdbc:sqlite:data/$repoName.db"
    username = "sa"
    password = ""
    maximumPoolSize = 25
    isAutoCommit = false

    validate()
  })

  val compressionHandler =
    EncodingHandler(
      ContentEncodingRepository()
        .addEncodingHandler(
          "gzip",
          GzipEncodingProvider(), 50,
          Predicates.parse("max-content-size(5)")
        )
    ).setNext(RequestHandler)

  val server = Undertow.builder()
    .addHttpListener(SERVER_PORT, "localhost")
    .setIoThreads(4)
    .setHandler(compressionHandler)
    .setServerOption(UndertowOptions.SHUTDOWN_TIMEOUT, 1000)
    .build()

  println("Starting undertow server at port $SERVER_PORT...")
  server?.start()

}
