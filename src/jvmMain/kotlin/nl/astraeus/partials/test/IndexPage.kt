package nl.astraeus.partials.test

import io.undertow.server.HttpServerExchange
import kotlinx.html.*
import nl.astraeus.partials.web.Builder
import nl.astraeus.partials.web.NoData
import nl.astraeus.partials.web.Request
import nl.astraeus.partials.web.onClick
import java.util.*

class IndexPage(
  request: Request,
  session: TestSession,
  data: NoData,
) : HeadPage<NoData>(
  request,
  session,
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
      hr()
      renderTimePartial()
    }
  }

}

fun HtmlBlockTag.renderTimePartial() {
  div {
    id = "show-timer"

    +"The time is ${Date()}"
  }
}