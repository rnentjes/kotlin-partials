package nl.astraeus.partials.web

import io.undertow.io.Sender
import io.undertow.server.HttpServerExchange
import kotlinx.html.HtmlBlockTag
import kotlinx.html.consumers.DelayedConsumer
import kotlinx.html.div
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

val partialConnections = ConcurrentHashMap<String, PartialsConnection>()

class PartialsConnection(
  val id: String,
  val exchange: HttpServerExchange,
  val sender: Sender,
) {
  var isOpen = AtomicBoolean(true)
  val eventQueue = LinkedBlockingQueue<String>()
  var lastSendTime = System.currentTimeMillis()
  val eventId = AtomicLong(1)

  init {
    partialConnections[id] = this
  }

  // send html content for the partial
  fun sendPartials(vararg partials: String) {
    if (!isOpen.get()) {
      partialConnections.remove(id)
      return
    }
    for (partial in partials) {
      eventQueue.add(partial)
    }
  }

  // send html content for the partial
  fun sendPartials(vararg partials: HtmlBlockTag.() -> Unit) {
    for (partial in partials) {
      val bldr = HtmlBuilder(prettyPrint = true, xhtmlCompatible = true)

      val consumer = DelayedConsumer(bldr)

      consumer.div { partial() }

      val result = consumer.finalize()

      sendPartials(result)
    }
  }
}
