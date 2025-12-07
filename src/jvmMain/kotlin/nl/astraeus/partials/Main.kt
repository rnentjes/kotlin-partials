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
import nl.astraeus.partials.web.RequestHandler
import java.io.Serializable
import kotlin.reflect.KClass

fun <S : Serializable> createPartialsServer(
  port: Int = 8080,
  session: () -> S,
  vararg mapping: Pair<String, KClass<*>>,
  sessionManager: SessionManager = InMemorySessionManager("SESSION_MANAGER"),
  sessionConfig: SessionCookieConfig = SessionCookieConfig(),
): Undertow {
  val defaultPage = mapping.firstOrNull()?.second ?: NotFoundPage::class
  val sessionHandler = SessionAttachmentHandler(
    RequestHandler(
      defaultPage,
      session,
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
    .setIoThreads(4)
    .setHandler(canonicalPathHandler)
    .setServerOption(UndertowOptions.SHUTDOWN_TIMEOUT, 1000)
    .build()

  return server
}
