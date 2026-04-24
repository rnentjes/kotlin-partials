package nl.astraeus.partials.test

import io.undertow.server.HttpServerExchange
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.br
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.hr
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.role
import kotlinx.html.span
import kotlinx.html.style
import nl.astraeus.partials.web.Builder
import nl.astraeus.partials.web.onChange
import nl.astraeus.partials.web.onClick
import nl.astraeus.partials.web.onEnter
import nl.astraeus.partials.web.onFileDrop
import nl.astraeus.partials.web.partial
import java.io.Serializable

class DashboardData(
  var title: String = "Clicker! [click me]",
  var count: Int = 0,
  var pageTitle: String = "Partials v.1.0.0",
  var inputValue: String = "",
  var myCompValue: String = ""
) : Serializable {

  companion object {
    val serialVersionUID = 1L
  }
}

class DashboardPage : HeadPage<DashboardData>({ DashboardData() }) {
  var filesDropped: String = ""

  override fun process(): String? {
    refresh(droppedFiles)
    refresh(sessionInfo, "Mamaloe", id = 2)

    request.value("action") { value ->
      when (value) {
        "hello" -> {
          data.count++
          data.title = "Clicker! [clicked me ${data.count} times!]"

          pageTitle = "Click ${data.count}"

          refresh(helloPartial)
          refresh(outsideInfo, "Test F dat")
        }

        "input-change" -> {
          data.inputValue = request.data["input-value"] ?: ""
          refresh(pageContainer)
        }

        "drop-file", "upload" -> {
          filesDropped = ""
          for ((name, _) in request.files) {
            filesDropped += "$name "
          }
        }
      }
    }

    return null
  }

  override fun Builder.content(exchange: HttpServerExchange) {
    partial(pageContainer)
  }

  val pageContainer by partial { _, _ ->
    div {
      h1 {
        +"Dashboard"
      }

      div {
        partial(helloPartial)

        span {
          +"Some extra blaat"
        }
      }

      div {
        input {
          id = "input-test"
          name = "input-value"
          value = data.inputValue

          onChange("action" to "input-change")
          onEnter("action" to "input-change")
        }

        span {
          +"Input value: ${data.inputValue}"
        }
      }
      br
      div {
        onFileDrop("action" to "drop-file")

        style =
          "border: 2px dashed #ccc; padding: 3em; text-align: center; background: var(--pico-card-background-color);"

        +"Drop a file here"
      }
      div {
        input {
          type = InputType.file
          name = "filex"
          multiple = true
        }
        span {
          role = "button"

          onClick("action" to "upload")

          +"Upload"
        }
      }
      hr {}
      partial(droppedFiles)
      hr {}
      partial(sessionInfo, 1, 1)
      partial(sessionInfo, "Pipo!", 2)
      hr {}
      a {
        href = "/index"
        +"Index"
      }
      hr {}
      partial(outsideInfo, "outside info init")
    }
  }

  val droppedFiles by partial { _, _ ->
    div {
      if (filesDropped.isNotEmpty()) {
        +"Files dropped/uploaded: $filesDropped"
      } else {
        +"No dropped files"
      }
    }
  }

  val helloPartial by partial { _, _ ->
    div {
      role = "button"

      onClick("action" to "hello")

      +data.title
    }
  }

  val sessionInfo by partial { dat, _ ->
    div {
      +"Session id: ${session.id} - ${dat ?: "No data"}"
    }
  }

}

val outsideInfo by partial { dat, _ ->
  div {
    +"Outside info, data: ${dat ?: "No data"}"
  }
}
