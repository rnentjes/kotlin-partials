package nl.astraeus.partials.test

import io.undertow.server.HttpServerExchange
import kotlinx.html.*
import nl.astraeus.partials.web.Builder
import nl.astraeus.partials.web.Request
import nl.astraeus.partials.web.onChange
import nl.astraeus.partials.web.onClick
import java.io.Serializable

class DashboardData(
  var title: String = "Clicker! [click me]",
  var count: Int = 0,
  var pageTitle: String = "Partials v.1.0.0",
  var inputValue: String = "",
) : Serializable {
  companion object {
    val serialVersionUID = 1L
  }
}

class DashboardPage(
  request: Request,
  session: TestSession,
  data: DashboardData,
) : HeadPage<DashboardData>(
  request,
  session,
  data,
) {
  override var pageTitle: String = data.pageTitle

  override fun process(): String? {
    if (request.data["action"] == "hello") {
      data.count++
      data.title = "Clicker! [clicked me ${data.count} times!]"

      pageTitle = "Click ${data.count}"

      refresh("page-title")
      refresh("hello")
    }
    if (request.data["action"] == "update-title") {
      pageTitle = "Update title"
      refresh("page-title")
    }
    if (request.data["action"] == "input-change") {
      data.inputValue = request.data["input-value"] ?: ""
      refresh("page-container")
    }
    return null
  }

  override fun Builder.content(exchange: HttpServerExchange) {
    div {
      id = "page-container"

      h1 {
        +"Dashboard"
      }

      div {
        div {
          id = "hello"
          role = "button"

          onClick("action" to "hello")

          +data.title
        }
        span {
          +"Some extra blaat"
        }
      }

      div {
        id = "update-title"
        role = "button"

        onClick("action" to "update-title")

        +"[Click to update page title]"
      }

      div {
        input {
          id = "input-test"
          name = "input-value"
          value = data.inputValue

          onChange("action" to "input-change")
        }

        span {
          +"Input value: ${data.inputValue}"
        }
      }
      br
      a {
        href = "/index"
        +"Index"
      }

    }
  }

}
