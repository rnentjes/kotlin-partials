package nl.astraeus.partials.test

import io.undertow.server.HttpServerExchange
import kotlinx.html.a
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.hr
import kotlinx.html.span
import kotlinx.html.style
import kotlinx.html.unsafe
import nl.astraeus.partials.web.Builder
import nl.astraeus.partials.web.dragSource
import nl.astraeus.partials.web.dragTarget
import nl.astraeus.partials.web.partial
import java.io.Serializable

class DragData(
  var location: Array<Int> = Array(4) { it + 1 }
) : Serializable

class DragPage : HeadPage<DragData>({ DragData() }) {

  override fun process(): String? {
    if (request.get("drag") == "drop") {
      val source = request.getInt("dragSource")
      val target = request.getInt("dragTarget")

      if (source != null && target != null) {
        val sourceValue = data.location[source]
        data.location[source] = data.location[target]
        data.location[target] = sourceValue

        update(pageContainer)
      }
    }

    return null
  }

  override fun Builder.content(exchange: HttpServerExchange) {
    div {
      style {
        unsafe {
          +"""
          .block {
            margin: 2rem;
            padding: 3rem;
            background-color: hsl(0deg 0% 25%  / 25%);
          }
        """.trimIndent()
        }
      }
      style {
        unsafe {
          +"""
          .drop-target {
            background-color: hsl(0deg 50% 25%  / 50%);
          }
        """.trimIndent()
        }
      }
      style {
        unsafe {
          +"""
          .dragging {
            background-color: hsl(90deg 50% 25%  / 50%);
          }
        """.trimIndent()
        }
      }
      partial(pageContainer)
    }
  }

  val pageContainer by partial { _, _ ->
    div {
      div {
        h1 {
          +"Drag & drop"
        }
      }

      div {
        style = "display: flex"

        for ((index, value) in data.location.withIndex()) {
          span(classes = "block") {
            dragSource("$index")
            dragTarget("$index", "drag" to "drop")

            +"DRAG/DROP ME ${value}!"
          }
        }
      }

      hr {}
      a {
        href = "/index"
        +"Index"
      }
    }
  }
}