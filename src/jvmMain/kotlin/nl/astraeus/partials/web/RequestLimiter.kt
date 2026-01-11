package nl.astraeus.partials.web

import io.undertow.server.HttpHandler
import io.undertow.server.HttpServerExchange

class RequestLimiter(
  val next: HttpHandler,
  val maxRequestSize: Long,
) : HttpHandler {

  override fun handleRequest(exchange: HttpServerExchange) {
    exchange.maxEntitySize = maxRequestSize

    next.handleRequest(exchange)
  }
}