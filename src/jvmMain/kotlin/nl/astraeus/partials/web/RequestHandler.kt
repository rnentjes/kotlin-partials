package nl.astraeus.partials.web

import io.undertow.server.HttpHandler
import io.undertow.server.HttpServerExchange
import io.undertow.server.handlers.PathHandler
import io.undertow.server.handlers.resource.PathResourceManager
import io.undertow.server.handlers.resource.ResourceHandler
import java.nio.file.Paths
import kotlin.reflect.KClass
import kotlin.reflect.KFunction

class RequestHandler(
  val defaultPage: KClass<*>,
  vararg val mappings: Pair<String, KClass<*>>
) : HttpHandler {
  val resourceHandler = ResourceHandler(PathResourceManager(Paths.get("web")))
  val pathHandler = PathHandler(resourceHandler)

  init {
    // todo: check mappings for types constructors and data class types
    // cache constructors
  }

  override fun handleRequest(exchange: HttpServerExchange) {
    val path = exchange.relativePath
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
      val request = exchange.request()
      val constructor = getConstructor(clazz)
      val dataType = constructor.parameters[1].type.classifier as KClass<*>
      val data = if (request.pageData == null) {
        dataType.constructors.first().callBy(emptyMap())
      } else {
        (request.pageData as String).decode()
      }

      val handler = constructor.call(request, data) as HttpHandler

      handler.handleRequest(exchange)
    } else {
      pathHandler.handleRequest(exchange)
    }
  }

  private fun getConstructor(clazz: KClass<*>): KFunction<Any> {
    val tp = clazz.typeParameters
    val constr = clazz.constructors

    if (constr.size != 1) {
      error("Expected exactly one constructor for a PartialsPage, found ${constr.size} in ${clazz.qualifiedName}")
    }

    val constructor = constr.first()

    if (constructor.parameters.size != 2) {
      error("Expected exactly two parameters for a PartialsPage constructor, found ${constructor.parameters.size} in ${clazz.qualifiedName}")
    }

    return constructor
  }
}
