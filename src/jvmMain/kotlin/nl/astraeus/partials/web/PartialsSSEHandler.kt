package nl.astraeus.partials.web

import io.undertow.server.HttpHandler
import io.undertow.server.HttpServerExchange
import io.undertow.util.HttpString
import nl.astraeus.partials.web.PartialsConnections.partialConnections
import java.nio.ByteBuffer

class PartialsSSEHandler(
  val request: Request,
) : HttpHandler {

  override fun handleRequest(exchange: HttpServerExchange) {
    // Dispatch to a worker thread to prevent blocking the IO thread
    if (exchange.isInIoThread) {
      exchange.dispatch(this)
      return
    }

    exchange.responseHeaders.apply {
      put(HttpString("Content-Type"), "text/event-stream; charset=UTF-8")
      put(HttpString("Cache-Control"), "no-cache")
      put(HttpString("Connection"), "keep-alive")
    }

    exchange.statusCode = 200

    val sender = exchange.responseSender
    val connection = PartialsConnection(
      exchange.queryParameters[PARTIALS_CONNECTION_ID_HEADER]?.firstOrNull()
        ?: error("EventSource init call is missing $PARTIALS_CONNECTION_ID_HEADER"),
      exchange,
      sender
    )
    SenderTask.start()

    exchange.addExchangeCompleteListener { _, _ ->
      connection.isOpen.set(false)
      //callback.isClosed.set(true)
    }

    exchange.dispatch(Runnable {
      sender.send(
        ByteBuffer.wrap("id: 1\ndata: Connected\n\n".toByteArray()),
        NoOpEventCallback(connection)
      )
    })
  }
}

fun HttpServerExchange.getPartialsSSEConnection(): PartialsConnection? {
  val connectionId = requestHeaders.getFirst(PARTIALS_CONNECTION_ID_HEADER) ?: return null
  return partialConnections[connectionId]
}
