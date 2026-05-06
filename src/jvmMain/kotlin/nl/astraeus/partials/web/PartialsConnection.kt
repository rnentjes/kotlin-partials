package nl.astraeus.partials.web

import io.undertow.io.Sender
import io.undertow.server.HttpServerExchange
import kotlinx.html.div
import nl.astraeus.partials.tag.HtmlBuilder
import nl.astraeus.partials.web.PartialsConnections.partialConnections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class PartialsConnection(
  val id: String,
  val exchange: HttpServerExchange,
  val sender: Sender,
) {
  var isOpen = AtomicBoolean(true)
  val eventQueue = LinkedBlockingQueue<String>()
  var lastSendTime = System.currentTimeMillis()
  val eventId = AtomicLong(1)
  val request: Request = exchange.request()

  init {
    partialConnections[id] = this
  }

  fun getSession(): PartialsSession? = exchange.getPartialsSession()

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
  fun sendPartial(partial: RegisteredRenderFunction, data: Any? = null, id: Long = 0) {
    val bldr = HtmlBuilder(prettyPrint = true, xhtmlCompatible = true)

    val consumer = Builder(bldr)

    consumer.div {
      consumer.renderPartialFunction(
        UpdateFunction(
          partial,
          data,
          id
        )
      )
    }

    val result = consumer.finalize()

    sendPartials(result)
  }
}

object PartialsConnections {
  val partialConnections = ConcurrentHashMap<String, PartialsConnection>()
}
