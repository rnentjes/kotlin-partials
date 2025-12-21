package nl.astraeus.partials.test

import io.undertow.server.HttpServerExchange
import kotlinx.html.*
import nl.astraeus.partials.web.Builder
import nl.astraeus.partials.web.NoData
import nl.astraeus.partials.web.Request
import nl.astraeus.partials.web.onClick
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
      renderTimePartial(session.timezone)
    }
  }

}

fun HtmlBlockTag.renderTimePartial(zoneId: ZoneId) {
  div {
    id = "show-timer"

    +"The time is ${timestamp(zoneId)} (${zoneId.id})"
  }
}

fun timestamp(zoneId: ZoneId): String {
  val now = LocalDateTime.now(zoneId)
  val formatted = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
  return formatted
}
