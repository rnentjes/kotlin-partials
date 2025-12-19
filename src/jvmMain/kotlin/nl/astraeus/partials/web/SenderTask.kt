package nl.astraeus.partials.web

import io.undertow.io.IoCallback
import io.undertow.io.Sender
import io.undertow.server.HttpServerExchange
import nl.astraeus.partials.web.PartialsConnections.partialConnections
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

object SenderTask : Runnable {
  private var thread = Thread(this)
  val running = AtomicBoolean(false)

  fun start() {
    if (thread.isAlive) return

    if (thread.state != Thread.State.TERMINATED) {
      thread = Thread(this)
    }

    thread.isDaemon = true
    thread.start()
  }

  override fun run() {
    running.set(true)

    while (running.get()) {
      try {
        var moreEvents = false
        partialConnections.values.forEach {
          if (!it.isOpen.get()) {
            partialConnections.remove(it.id)
          }

          try {
            val event = it.eventQueue.poll()

            if (event != null) {
              val msg = event.replace("\n", "")
              val payload = ByteBuffer.wrap("id: ${it.eventId.incrementAndGet()}\ndata: $msg\n\n".toByteArray())
              it.lastSendTime = System.currentTimeMillis()
              it.sender.send(payload, NoOpEventCallback(it))
              moreEvents = true
            } else if (it.lastSendTime + 10000 < System.currentTimeMillis()) {
              // Send keep-alive
              val keepAlive = ByteBuffer.wrap(": keep-alive\n\n".toByteArray())
              it.lastSendTime = System.currentTimeMillis()
              it.sender.send(keepAlive, NoOpEventCallback(it))
            }
          } catch (e: Throwable) {
            it.isOpen.set(false)
            e.printStackTrace()
          }
        }

        if (!moreEvents) {
          Thread.sleep(10)
        }
      } catch (e: Throwable) {
        e.printStackTrace()
      }
    }

  }
}

class NoOpEventCallback(
  val connection: PartialsConnection,
) : IoCallback {
  override fun onComplete(exchange: HttpServerExchange?, sender: Sender?) {
    // ignore
    //println("onComplete")
  }

  override fun onException(
    exchange: HttpServerExchange?,
    sender: Sender?,
    exception: IOException?
  ) {
    exception?.printStackTrace()
    connection.isOpen.set(false)
  }

}
