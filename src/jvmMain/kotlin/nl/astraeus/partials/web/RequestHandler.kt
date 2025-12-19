package nl.astraeus.partials.web

import io.undertow.server.HttpHandler
import io.undertow.server.HttpServerExchange
import io.undertow.util.HttpString
import kotlin.reflect.KClass
import kotlin.reflect.KFunction

class RequestHandler<S : PartialsSession>(
  val defaultPage: KClass<*>,
  val session: () -> S,
  val next: HttpHandler? = null,
  vararg val mappings: Pair<String, KClass<*>>
) : HttpHandler {
  init {
    // todo: check mappings for types constructors and data class types
    // cache constructors
  }

  override fun handleRequest(exchange: HttpServerExchange) {
    val path = exchange.relativePath

    val request = exchange.request()
    var session = exchange.getPartialsSession<S>()
    if (session == null) {
      session = session()
      exchange.setPartialsSession(session)
    }

    if (path == "/partials-sse") {
      val handler = PartialsSSEHandler(request)
      handler.handleRequest(exchange)
      return
    }

    var clazz: KClass<*>? = null
    if (path == "/") {
      clazz = defaultPage
    } else {
      for (mapping in mappings) {
        if (path.startsWith(mapping.first)) {
          clazz = mapping.second
        }
      }
    }

    if (clazz != null) {
      if (exchange.requestMethod == HttpString("POST") && exchange.isInIoThread) {
        exchange.dispatch(this)
        return
      }

      val constructor = getConstructor(clazz)
      val dataType = constructor.parameters[2].type.classifier as KClass<*>
      val data = if (request.pageData == null) {
        dataType.constructors.first().callBy(emptyMap())
      } else {
        (request.pageData as String).decode()
      }

      val handler = constructor.call(request, session, data) as HttpHandler

      handler.handleRequest(exchange)
    } else {
      next?.handleRequest(exchange) ?: error("No handler found for path $path")
    }
  }

  private fun getConstructor(clazz: KClass<*>): KFunction<Any> {
    val tp = clazz.typeParameters
    val constr = clazz.constructors

    if (constr.size != 1) {
      error("Expected exactly one constructor for a PartialsPage, found ${constr.size} in ${clazz.qualifiedName}")
    }

    val constructor = constr.first()

    if (constructor.parameters.size != 3) {
      error("Expected exactly three parameters for a PartialsPage constructor (request, session and data), found ${constructor.parameters.size} in ${clazz.qualifiedName}")
    }

    return constructor
  }
}
