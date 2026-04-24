package nl.astraeus.partials.test

import io.undertow.server.HttpServerExchange
import kotlinx.html.a
import kotlinx.html.br
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.hr
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.span
import nl.astraeus.partials.web.Builder
import nl.astraeus.partials.web.NoData
import nl.astraeus.partials.web.onClick
import nl.astraeus.partials.web.onPasskeyLogin
import nl.astraeus.partials.web.onPasskeyRegister
import nl.astraeus.partials.web.partial
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class IndexPage : HeadPage<NoData>({ NoData() }) {

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
      div {
        span {
          div {
            +"Passkey login test (credentials are stored in memory, so lost after restart)"
          }
          div {
            input {
              name = "passkey-username"
            }

            a {
              onPasskeyRegister("action" to "register")

              +"Register"
            }
          }
          div {
            a {
              onPasskeyLogin("action" to "login")

              +"Login"
            }
          }
        }
      }
      hr()
      partial(renderTimePartial, session.timezone)
    }
  }
}

val renderTimePartial by partial { zoneId, _ ->
  check(zoneId is ZoneId)

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
