package nl.astraeus.partials

import kotlinx.browser.document
import kotlinx.browser.window
import nl.astraeus.partials.web.PARTIALS_CONNECTION_ID_HEADER
import org.w3c.dom.EventSource
import org.w3c.dom.HTMLInputElement

fun main() {
  window.onload = {
    PartialsHandler.updateHtml(document.body!!)

    connectToEventSource()
  }
}

private fun connectToEventSource() {
  val connectionIdInput = document.getElementById(PARTIALS_CONNECTION_ID_HEADER) as? HTMLInputElement
  if (connectionIdInput != null) {
    val eventSource = EventSource(
      "/partials-sse?${PARTIALS_CONNECTION_ID_HEADER}=${connectionIdInput.value}"
    )

    eventSource.onmessage = { me ->
      (me.data as? String)?.also {
        if (it.startsWith("<div>")) {
          PartialsHandler.handleHtmlResponse(it)
        }
      }
    }

    eventSource.onerror = { me ->
      console.error("✗ EventSource error:", me)
      console.error("✗ Ready state:", eventSource.readyState) // 0=CONNECTING, 1=OPEN, 2=CLOSED
      eventSource.close()
      window.setTimeout({ connectToEventSource() }, 1000)
    }

    window.addEventListener("beforeunload", { event ->
      eventSource.close()
    })
  } else {
    window.setTimeout({ connectToEventSource() }, 1000)
  }
}
