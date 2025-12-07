package nl.astraeus.tmpl.web

import io.undertow.server.HttpHandler
import io.undertow.server.HttpServerExchange
import io.undertow.server.handlers.PathHandler
import io.undertow.server.handlers.resource.PathResourceManager
import io.undertow.server.handlers.resource.ResourceHandler
import io.undertow.util.Headers
import nl.astraeus.tmpl.itemUrl
import java.nio.file.Paths
import kotlin.text.startsWith

object IndexHandler : HttpHandler {
  override fun handleRequest(exchange: HttpServerExchange) {
    exchange.responseHeaders.put(Headers.CONTENT_TYPE, "text/html")
    if (exchange.requestPath.startsWith("/$itemUrl/")) {
      exchange.responseSender.send(generateIndex(null))
    } else {
      val itemId = generateId()

      exchange.responseSender.send(generateIndex(itemId))
    }
  }
}

object RequestHandler : HttpHandler {
  val resourceHandler = ResourceHandler(PathResourceManager(Paths.get("web")))
  val pathHandler = PathHandler(resourceHandler)

  init {
    pathHandler.addExactPath("/", IndexHandler)
    pathHandler.addExactPath("/index.html", IndexHandler)
    pathHandler.addPrefixPath("/$itemUrl", IndexHandler)
    pathHandler.addExactPath("/ws", WebsocketConnectHandler)
  }

  override fun handleRequest(exchange: HttpServerExchange) {
    pathHandler.handleRequest(exchange)
  }
}
