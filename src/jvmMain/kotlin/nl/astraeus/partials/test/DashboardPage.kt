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
import nl.astraeus.partials.web.PartialComponent
import nl.astraeus.partials.web.PartialKey
import nl.astraeus.partials.web.RenderFunction
import nl.astraeus.partials.web.Request
import nl.astraeus.partials.web.onChange
import nl.astraeus.partials.web.onClick
import nl.astraeus.partials.web.onEnter
import nl.astraeus.partials.web.onFileDrop
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

enum class DashboardKey : PartialKey {
  PAGE_CONTAINER,
  DROPPED_FILES,
  HELLO,
  SESSION_INFO,
  PAGE_TITLE,
  MY_COMPONENT,
  ;
}

class MyComponent : PartialComponent<TestSession, DashboardData>() {

  override fun process(
    request: Request,
    session: TestSession,
    pageData: DashboardData
  ) {
    println("MyComponent.process")

    if (request.get("action") == "click_component") {
      pageData.count++
      pageData.title = "Clicker! [clicked me ${pageData.count} times!] <MyComponent action>"

      refresh(DashboardKey.HELLO)
    }
  }

  override fun Builder.content(
    session: TestSession,
    pageData: DashboardData,
    data: Any?,
    id: Long
  ) {
    div {
      div {
        +"This is my component: $data"
      }
      div {
        input {
          type = InputType.button
          name = "action"
          value = "Click"

          onClick("action" to "click_component")
        }
      }
    }
  }

}

class DashboardPage : HeadPage<DashboardData, DashboardKey>({ DashboardData() }) {
  var filesDropped: String = ""

  override fun onInit() {
    partial(DashboardKey.PAGE_CONTAINER, { _, _ -> this.pageContainer() })
    partial(DashboardKey.DROPPED_FILES) { _, _ -> this.droppedFiles() }
    partial(DashboardKey.HELLO) { _, _ -> this.hello() }
    partial(DashboardKey.SESSION_INFO) { data, id -> this.sessionInfo(data) }
    partial(DashboardKey.MY_COMPONENT, MyComponent())
  }

  override fun process(): String? {
    refresh(DashboardKey.DROPPED_FILES)
    refresh(DashboardKey.SESSION_INFO, "Mamaloe", id = 2)

    request.value("action") { value ->
      when (value) {
        "hello" -> {
          data.count++
          data.title = "Clicker! [clicked me ${data.count} times!]"

          pageTitle = "Click ${data.count}"

          //refresh("page-title")
          refresh(DashboardKey.HELLO)
          refresh(functionTest, "From Process!")
        }

        "input-change" -> {
          data.inputValue = request.data["input-value"] ?: ""
          refresh(DashboardKey.PAGE_CONTAINER)
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
    partial(DashboardKey.PAGE_CONTAINER)
  }

  fun Builder.pageContainer() {
    div {
      h1 {
        +"Dashboard"
      }

      div {
        partial(DashboardKey.HELLO)

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
      partial(DashboardKey.DROPPED_FILES)
      hr {}
      partial(DashboardKey.SESSION_INFO, 1, 1)
      partial(DashboardKey.SESSION_INFO, "Pipo!", 2)
      hr {}
      a {
        href = "/index"
        +"Index"
      }
      hr {}
      partial(DashboardKey.MY_COMPONENT, "MyCompDat")
      hr {}
      partial(functionTest, "Func Test!")
    }
  }

  val functionTest: RenderFunction = { page, data, id ->
    div {
      +"Function!! $data"
    }
  }

  fun Builder.droppedFiles() {
    div {
      if (filesDropped.isNotEmpty()) {
        +"Files dropped/uploaded: $filesDropped"
      } else {
        +"No dropped files"
      }
    }
  }

  fun Builder.hello() {
    div {
      role = "button"

      onClick("action" to "hello")

      +data.title
    }
  }

  fun Builder.sessionInfo(data: Any?) {
    div {
      id = "pipo"
      +"Session id: ${session.id} - ${data ?: "No data"}"
    }
  }

}
