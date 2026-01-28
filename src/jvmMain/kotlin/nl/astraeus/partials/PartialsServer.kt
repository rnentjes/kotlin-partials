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
import java.io.Serializable
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.memberProperties

var partialsLogger: PartialsLogger = DefaultPartialsLogger()
var maximumRequestSize: Long = 100 * 1024 * 1024

fun <S : PartialsSession> createPartialsServer(
  port: Int = 8080,
  session: () -> S,
  vararg mapping: Pair<String, PageFactory<S, *>>,
  logger: PartialsLogger = DefaultPartialsLogger(),
  sessionManager: SessionManager = InMemorySessionManager("SESSION_MANAGER"),
  sessionConfig: SessionCookieConfig = createSessionCookieConfig(),
  resourceBasePath: String = "static",
  resourceUrlPrefix: String = "/static",
  maxRequestSize: Long = maximumRequestSize // 100 MB limit
): Undertow {
  partialsLogger = logger
  maximumRequestSize = maxRequestSize

  val defaultPage = mapping.firstOrNull()?.second ?: pageFactory<S, NoData> { NotFoundPage() }

  val resourceHandler = createStaticResourceHandler(resourceBasePath, resourceUrlPrefix)
  val partialsHandler = createPartialsHandler(defaultPage, session, resourceHandler, mapping)
  val sessionHandler = createSessionHandler(partialsHandler, sessionManager, sessionConfig)
  val compressionHandler = createCompressionHandler(sessionHandler)
  val canonicalPathHandler = CanonicalPathHandler(compressionHandler)

  val server = Undertow.builder()
    .addHttpListener(port, "localhost")
    .setHandler(canonicalPathHandler)
    .setServerOption(UndertowOptions.IDLE_TIMEOUT, 30000)
    .setServerOption(UndertowOptions.SHUTDOWN_TIMEOUT, 1000)
    .setServerOption(UndertowOptions.MAX_ENTITY_SIZE, maximumRequestSize)
    .setServerOption(UndertowOptions.MULTIPART_MAX_ENTITY_SIZE, maximumRequestSize)
    .build()

  return server
}

@JvmName("createPartialsServerWithClasses")
fun <S : PartialsSession> createPartialsServer(
  port: Int = 8080,
  session: () -> S,
  vararg mapping: Pair<String, KClass<*>>,
  logger: PartialsLogger = DefaultPartialsLogger(),
  sessionManager: SessionManager = InMemorySessionManager("SESSION_MANAGER"),
  sessionConfig: SessionCookieConfig = createSessionCookieConfig(),
  resourceBasePath: String = "static",
  resourceUrlPrefix: String = "/static",
  maxRequestSize: Long = maximumRequestSize // 100 MB limit
): Undertow = createPartialsServer(
  port,
  session,
  *mapping.map { it.first to reflectivePageFactory<S>(it.second) }.toTypedArray(),
  logger = logger,
  sessionManager = sessionManager,
  sessionConfig = sessionConfig,
  resourceBasePath = resourceBasePath,
  resourceUrlPrefix = resourceUrlPrefix,
  maxRequestSize = maxRequestSize
)

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
  defaultPage: PageFactory<S, *>,
  session: () -> S,
  next: StaticResourceHandler,
  mapping: Array<out Pair<String, PageFactory<S, *>>>
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

private fun <S : PartialsSession> reflectivePageFactory(
  clazz: KClass<*>
): PageFactory<S, *> {
  val constructor = getNoArgConstructor(clazz)
  val dataProperty = clazz.memberProperties.firstOrNull { it.name == "data" }
    ?: error("Expected a data property on ${clazz.qualifiedName}")
  val dataClass = dataProperty.returnType.classifier as? KClass<*>
    ?: error("Type classifier is not a KClass (was: ${dataProperty.returnType.classifier})")

  @Suppress("UNCHECKED_CAST")
  return object : PageFactory<S, Serializable> {
    override val dataClass: KClass<Serializable> = dataClass as KClass<Serializable>
    override fun create(): PartialsPage<S, Serializable> = constructor.call() as PartialsPage<S, Serializable>
  }
}

private fun getNoArgConstructor(clazz: KClass<*>): KFunction<Any> {
  val constr = clazz.constructors.filter { it.parameters.isEmpty() }
  if (constr.size != 1) {
    error("Expected one no-arg constructor for a PartialsPage in ${clazz.qualifiedName}")
  }
  return constr.first()
}

fun createCompressionHandler(next: SessionAttachmentHandler): EncodingHandler? = EncodingHandler(
  ContentEncodingRepository()
    .addEncodingHandler(
      "gzip",
      GzipEncodingProvider(), 50,
      Predicates.parse("max-content-size(5)")
    )
).setNext(next)
