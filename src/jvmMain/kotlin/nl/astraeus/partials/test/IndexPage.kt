package nl.astraeus.partials.test

import io.undertow.server.HttpServerExchange
import kotlinx.html.a
import kotlinx.html.br
import kotlinx.html.div
import kotlinx.html.h1
import nl.astraeus.partials.web.Builder
import nl.astraeus.partials.web.NoData
import nl.astraeus.partials.web.Request

class IndexPage(
  request: Request,
  data: NoData,
) : HeadPage<NoData>(
  request,
  data,
) {

  override fun process(): String? {
    if (request.get("action") == "dashboard") {
      return "/dashboard"
    }

    return null
  }

  override fun Builder.content(exchange: HttpServerExchange) {
    div {
      h1 {
        +"Index"
      }
      div {
        onClick("action" to "dashboard")

        +"To Dashboard with action [CLICK]"
      }
      br
      a {
        href = "/dashboard"
        +"Dashboard"
      }
    }
  }

}