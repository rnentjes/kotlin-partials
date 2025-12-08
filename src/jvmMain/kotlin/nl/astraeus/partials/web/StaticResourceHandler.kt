package nl.astraeus.partials.web

import io.undertow.server.HttpHandler
import io.undertow.server.HttpServerExchange
import io.undertow.util.Headers
import io.undertow.util.StatusCodes
import java.nio.ByteBuffer

/**
 * HTTP handler that serves static files from classpath resources.
 *
 * This handler is designed to serve the compiled JavaScript files and other static assets
 * that are bundled in the JAR file under the resources directory.
 *
 * @param resourceBasePath The base path in the classpath where resources are located (e.g., "static")
 * @param urlPrefix The URL prefix to strip from incoming requests (e.g., "/static")
 */
class StaticResourceHandler(
  private val resourceBasePath: String,
  private val urlPrefix: String,
) : HttpHandler {

  override fun handleRequest(exchange: HttpServerExchange) {
    val requestPath = exchange.relativePath

    var contentType = "application/javascript"
    val resourceStream = if (requestPath.startsWith("/partials")) {
      contentType = getContentType(requestPath)
      this::class.java.classLoader.getResourceAsStream(requestPath.substring(1))
    } else {
      // Check if the request path starts with our URL prefix
      if (!requestPath.startsWith(urlPrefix)) {
        exchange.statusCode = StatusCodes.NOT_FOUND
        exchange.endExchange()
        return
      }

      // Strip the URL prefix to get the resource path
      val resourcePath = requestPath.removePrefix(urlPrefix).removePrefix("/")
      val fullResourcePath = if (resourceBasePath.isNotEmpty()) {
        "$resourceBasePath/$resourcePath"
      } else {
        resourcePath
      }

      contentType = getContentType(resourcePath)
      // Load resource from classpath
      this::class.java.classLoader.getResourceAsStream(fullResourcePath)
    }

    if (resourceStream == null) {
      exchange.statusCode = StatusCodes.NOT_FOUND
      exchange.endExchange()
      return
    }

    // Set content type based on file extension
    exchange.responseHeaders.put(Headers.CONTENT_TYPE, contentType)

    // Read and send the resource
    resourceStream.use { stream ->
      val bytes = stream.readBytes()
      exchange.responseSender.send(ByteBuffer.wrap(bytes))
    }
  }

  private fun getContentType(path: String): String {
    return when {
      path.endsWith(".js") -> "application/javascript"
      path.endsWith(".js.map") -> "application/json"
      path.endsWith(".css") -> "text/css"
      path.endsWith(".html") -> "text/html"
      path.endsWith(".json") -> "application/json"
      path.endsWith(".mjs") -> "application/javascript"
      else -> "application/octet-stream"
    }
  }
}
