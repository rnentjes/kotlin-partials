package nl.astraeus.partials.test

import io.undertow.server.HttpServerExchange
import kotlinx.html.*
import nl.astraeus.partials.web.*
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
  var filesDropped: String = ""

  override fun process(): String? {
    refresh("dropped-files")

    request.value("action") { value ->
      when (value) {
        "hello" -> {
          data.count++
          data.title = "Clicker! [clicked me ${data.count} times!]"

          pageTitle = "Click ${data.count}"

          refresh("page-title")
          refresh("hello")
        }

        "update-title" -> {
          pageTitle = "Update title"
          refresh("page-title")
        }

        "input-change" -> {
          data.inputValue = request.data["input-value"] ?: ""
          refresh("page-container")
        }

        "drop-file", "upload" -> {
          filesDropped = ""
          for ((name, _) in request.files) {
            filesDropped += "$name "
          }
          refresh("page-container")
        }
      }
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
      br
      div {
        id = "dropped-files"

        if (filesDropped.isNotEmpty()) {
          +"Files dropped/uploaded: $filesDropped"
        } else {
          +""
        }
      }
      br
      a {
        href = "/index"
        +"Index"
      }

      hr()

      include("my-component", MyComponent::class)
    }
  }

}
