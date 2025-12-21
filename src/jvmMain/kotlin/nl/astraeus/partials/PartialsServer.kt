package nl.astraeus.partials

import io.undertow.Undertow
import io.undertow.UndertowOptions
import io.undertow.predicate.Predicates
import io.undertow.server.handlers.CanonicalPathHandler
import io.undertow.server.handlers.encoding.ContentEncodingRepository
import io.undertow.server.handlers.encoding.EncodingHandler
import io.undertow.server.handlers.encoding.GzipEncodingProvider
import io.undertow.server.session.InMemorySessionManager
import io.undertow.server.session.SessionAttachmentHandler
import io.undertow.server.session.SessionCookieConfig
import io.undertow.server.session.SessionManager
import nl.astraeus.partials.web.NotFoundPage
import nl.astraeus.partials.web.PartialsSession
import nl.astraeus.partials.web.RequestHandler
import nl.astraeus.partials.web.StaticResourceHandler
import kotlin.reflect.KClass

var partialsLogger: PartialsLogger = DefaultPartialsLogger()

fun <S : PartialsSession> createPartialsServer(
  port: Int = 8080,
  session: () -> S,
  vararg mapping: Pair<String, KClass<*>>,
  logger: PartialsLogger = DefaultPartialsLogger(),
  sessionManager: SessionManager = InMemorySessionManager("SESSION_MANAGER"),
  sessionConfig: SessionCookieConfig = SessionCookieConfig(),
  resourceBasePath: String = "static",
  resourceUrlPrefix: String = "/static",
): Undertow {
  partialsLogger = logger

  val resourceHandler = StaticResourceHandler(
    resourceBasePath,
    resourceUrlPrefix,
  )

  val defaultPage = mapping.firstOrNull()?.second ?: NotFoundPage::class
  val sessionHandler = SessionAttachmentHandler(
    RequestHandler(
      defaultPage,
      session,
      resourceHandler,
      *mapping
    ),
    sessionManager,
    sessionConfig
  )

  val compressionHandler =
    EncodingHandler(
      ContentEncodingRepository()
        .addEncodingHandler(
          "gzip",
          GzipEncodingProvider(), 50,
          Predicates.parse("max-content-size(5)")
        )
    ).setNext(sessionHandler)

  val canonicalPathHandler = CanonicalPathHandler(compressionHandler)

  val server = Undertow.builder()
    .addHttpListener(port, "localhost")
    .setHandler(canonicalPathHandler)
    .setServerOption(UndertowOptions.IDLE_TIMEOUT, 30000)
    .setServerOption(UndertowOptions.SHUTDOWN_TIMEOUT, 1000)
    .build()

  return server
}
