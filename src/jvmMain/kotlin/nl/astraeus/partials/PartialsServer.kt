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
import nl.astraeus.partials.web.*
import kotlin.reflect.KClass

var partialsLogger: PartialsLogger = DefaultPartialsLogger()

fun <S : PartialsSession> createPartialsServer(
  port: Int = 8080,
  session: () -> S,
  vararg mapping: Pair<String, KClass<*>>,
  logger: PartialsLogger = DefaultPartialsLogger(),
  sessionManager: SessionManager = InMemorySessionManager("SESSION_MANAGER"),
  sessionConfig: SessionCookieConfig = createSessionCookieConfig(),
  resourceBasePath: String = "static",
  resourceUrlPrefix: String = "/static",
  maxRequestSize: Long = 100 * 1024 * 1024 // 100 MB limit
): Undertow {
  partialsLogger = logger

  val defaultPage = mapping.firstOrNull()?.second ?: NotFoundPage::class

  val resourceHandler = createStaticResourceHandler(resourceBasePath, resourceUrlPrefix)
  val partialsHandler = createPartialsHandler(defaultPage, session, resourceHandler, mapping)
  val sessionHandler = createSessionHandler(partialsHandler, sessionManager, sessionConfig)
  val compressionHandler = createCompressionHandler(sessionHandler)
  val canonicalPathHandler = CanonicalPathHandler(compressionHandler)
  val limiter = RequestLimiter(canonicalPathHandler, maxRequestSize)

  val server = Undertow.builder()
    .addHttpListener(port, "localhost")
    .setHandler(limiter)
    .setServerOption(UndertowOptions.IDLE_TIMEOUT, 30000)
    .setServerOption(UndertowOptions.SHUTDOWN_TIMEOUT, 1000)
    .build()

  return server
}

fun createSessionCookieConfig(): SessionCookieConfig = SessionCookieConfig().apply {
  isSecure = true
  isHttpOnly = true
  path = "/"
}

fun createStaticResourceHandler(
  resourceBasePath: String,
  resourceUrlPrefix: String
): StaticResourceHandler = StaticResourceHandler(
  resourceBasePath,
  resourceUrlPrefix,
)

fun <S : PartialsSession> createPartialsHandler(
  defaultPage: KClass<out Any>,
  session: () -> S,
  next: StaticResourceHandler,
  mapping: Array<out Pair<String, KClass<*>>>
): PartialsHandler<S> = PartialsHandler(
  defaultPage,
  session,
  next,
  *mapping
)

fun <S : PartialsSession> createSessionHandler(
  next: PartialsHandler<S>,
  sessionManager: SessionManager,
  sessionConfig: SessionCookieConfig
): SessionAttachmentHandler = SessionAttachmentHandler(
  next,
  sessionManager,
  sessionConfig
)

fun createCompressionHandler(next: SessionAttachmentHandler): EncodingHandler? = EncodingHandler(
  ContentEncodingRepository()
    .addEncodingHandler(
      "gzip",
      GzipEncodingProvider(), 50,
      Predicates.parse("max-content-size(5)")
    )
).setNext(next)
