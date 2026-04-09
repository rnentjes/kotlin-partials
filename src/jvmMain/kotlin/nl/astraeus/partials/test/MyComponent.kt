package nl.astraeus.partials.test

import io.undertow.server.HttpServerExchange
import kotlinx.html.div
import kotlinx.html.input
import nl.astraeus.partials.web.Builder
import nl.astraeus.partials.web.PartialsComponent
import nl.astraeus.partials.web.PartialsPage
import nl.astraeus.partials.web.onClick

class MyComponent(
  page: PartialsPage<*, *>
) : PartialsComponent(page) {
  var myComponentValue: String = ""

  override fun process(id: String): String? {
    myComponentValue = page.request.get("my-component-value") ?: ""

    if (page.request.get("action") == "redirect-from-my-component") {
      return "/index"
    }

    if (page.request.get("action") == "update-my-component-value") {
      myComponentValue += myComponentValue.length.toString()
      refresh(id)
    }

    return null
  }

  override fun Builder.content(exchange: HttpServerExchange) {
    div {
      div {
        div {
          onClick("action" to "update-my-component-value")

          +"Click to update my Component"
        }

        input {
          name = "my-component-value"
          value = myComponentValue
        }

        div {
          onClick("action" to "redirect-from-my-component")

          +"Click to redirect from my Component"
        }
      }
    }
  }
}
