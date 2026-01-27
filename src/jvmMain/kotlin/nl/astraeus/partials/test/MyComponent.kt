package nl.astraeus.partials.test

import io.undertow.server.HttpServerExchange
import kotlinx.html.div
import kotlinx.html.input
import nl.astraeus.partials.web.Builder
import nl.astraeus.partials.web.PartialsComponent
import nl.astraeus.partials.web.Request
import nl.astraeus.partials.web.onClick

class MyComponent(
  id: String,
  request: Request,
  session: TestSession,
  data: DashboardData,
) : PartialsComponent<TestSession, DashboardData>(
  id,
  request,
  session,
  data,
) {

  override fun process(id: String): String? {
    data.myCompValue = request.get("my-component-value") ?: ""

    if (request.get("action") == "redirect-from-my-component") {
      return "/index"
    }

    if (request.get("action") == "update-my-component-value") {
      data.myCompValue += data.myCompValue.length.toString()
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
          value = data.myCompValue
        }

        div {
          onClick("action" to "redirect-from-my-component")

          +"Click to redirect from my Component"
        }

      }
    }
  }

}
