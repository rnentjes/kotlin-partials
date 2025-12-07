package nl.astraeus.partials

import io.undertow.Undertow
import io.undertow.UndertowOptions
import io.undertow.predicate.Predicates
import io.undertow.server.handlers.CanonicalPathHandler
import io.undertow.server.handlers.encoding.ContentEncodingRepository
import io.undertow.server.handlers.encoding.EncodingHandler
import io.undertow.server.handlers.encoding.GzipEncodingProvider
import nl.astraeus.partials.web.RequestHandler
import kotlin.reflect.KClass

fun createPartialsServer(
  port: Int = 8080,
  defaultPage: KClass<*>,
  vararg mapping: Pair<String, KClass<*>>
): Undertow {
  val compressionHandler =
    EncodingHandler(
      ContentEncodingRepository()
        .addEncodingHandler(
          "gzip",
          GzipEncodingProvider(), 50,
          Predicates.parse("max-content-size(5)")
        )
    ).setNext(
      RequestHandler(
        defaultPage,
        *mapping
      )
    )

  val canonicalPathHandler = CanonicalPathHandler(compressionHandler)

  val server = Undertow.builder()
    .addHttpListener(port, "localhost")
    .setIoThreads(4)
    .setHandler(canonicalPathHandler)
    .setServerOption(UndertowOptions.SHUTDOWN_TIMEOUT, 1000)
    .build()

  return server
}
