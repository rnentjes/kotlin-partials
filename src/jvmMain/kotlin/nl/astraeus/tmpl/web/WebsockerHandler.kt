package nl.astraeus.tmpl.web

import io.undertow.Handlers.websocket
import io.undertow.server.HttpHandler
import io.undertow.server.HttpServerExchange
import io.undertow.websockets.WebSocketConnectionCallback
import io.undertow.websockets.core.AbstractReceiveListener
import io.undertow.websockets.core.BufferedBinaryMessage
import io.undertow.websockets.core.BufferedTextMessage
import io.undertow.websockets.core.WebSocketChannel
import io.undertow.websockets.extensions.PerMessageDeflateHandshake
import io.undertow.websockets.spi.WebSocketHttpExchange
import kotlin.also

class WebsocketHandler : AbstractReceiveListener(), WebSocketConnectionCallback {
  // var user: String? = null

  override fun onConnect(exchange: WebSocketHttpExchange, channel: WebSocketChannel) {
    channel.receiveSetter.set(this)
    channel.resumeReceives()
  }

  override fun onFullTextMessage(channel: WebSocketChannel, message: BufferedTextMessage) {
    val data = message.data

    TODO("handle text message")
  }

  override fun onFullBinaryMessage(channel: WebSocketChannel, message: BufferedBinaryMessage?) {
    message?.data?.also { data ->
      var length = 0
      for (buffer in data.resource) {
        length += buffer.remaining()
      }
      val bytes = ByteArray(length)
      var offset = 0
      for (buffer in data.resource) {
        buffer.get(bytes, offset, buffer.remaining())
        offset += buffer.remaining()
      }

      TODO("handle binary message")
    }
  }
}

object WebsocketConnectHandler : HttpHandler {

  override fun handleRequest(exchange: HttpServerExchange) {
    val handshakeHandler = websocket(WebsocketHandler())
    handshakeHandler.addExtension(PerMessageDeflateHandshake())
    handshakeHandler.handleRequest(exchange)
  }

}
