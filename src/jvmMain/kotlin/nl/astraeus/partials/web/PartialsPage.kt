package nl.astraeus.partials.web

import io.undertow.server.HttpHandler
import io.undertow.server.HttpServerExchange
import io.undertow.util.Headers
import kotlinx.html.*
import kotlinx.html.consumers.DelayedConsumer
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.nio.ByteBuffer
import java.time.ZoneId
import kotlin.io.encoding.Base64

typealias Builder = DelayedConsumer<String>

fun String.decode(): Serializable {
  val bytes = Base64.decode(this)
  ObjectInputStream(bytes.inputStream()).use { ois ->
    return ois.readObject() as Serializable
  }
}

fun <D : Serializable> D.encode(): String {
  val byteArrayOutputStream = ByteArrayOutputStream()
  ObjectOutputStream(byteArrayOutputStream).use { oos ->
    oos.writeObject(this)
  }
  val bytes = byteArrayOutputStream.toByteArray()
  return Base64.encode(bytes)
}

private fun CoreAttributeGroupFacade.doPost(
  eventName: String,
  vararg parameters: Pair<String, String>
) {
  this.attributes["data-p-${eventName}"] = parameters.joinToString("&") { pair ->
    "${pair.first}=${pair.second}"
  }
}

private fun CoreAttributeGroupFacade.doClickPost(
  eventName: String,
  vararg parameters: Pair<String, String>
) {
  classes += "partials-cursor"
  doPost(eventName, *parameters)
}

fun CoreAttributeGroupFacade.onClick(vararg parameters: Pair<String, String>) = doClickPost("click", *parameters)

fun CoreAttributeGroupFacade.onDoubleClick(vararg parameters: Pair<String, String>) =
  doClickPost("dblclick", *parameters)

fun CoreAttributeGroupFacade.onSubmit(vararg parameters: Pair<String, String>) = doClickPost("submit", *parameters)

fun CoreAttributeGroupFacade.onChange(vararg parameters: Pair<String, String>) = doPost("change", *parameters)

fun CoreAttributeGroupFacade.onBlur(vararg parameters: Pair<String, String>) = doPost("blur", *parameters)

fun CoreAttributeGroupFacade.onKeyUp(vararg parameters: Pair<String, String>) = doPost("keyup", *parameters)

fun CoreAttributeGroupFacade.onFileDrop(vararg parameters: Pair<String, String>) = doPost("file-drop", *parameters)

fun INPUT.onEnter(vararg parameters: Pair<String, String>) {
  doPost("enter", *parameters)
}

fun HttpServerExchange.isPartialsRequest(): Boolean {
  return requestHeaders.getFirst(PARTIALS_REQUEST_HEADER) == "true"
}

abstract class PartialsSession : Serializable {
  val id = System.currentTimeMillis().toString() + System.nanoTime().toString()
  var timezone: ZoneId = ZoneId.systemDefault()
}

class NoData : PartialsSession()

abstract class PartialsPage<S : PartialsSession, T : Serializable>(
  val request: Request,
  val session: S,
  val data: T,
  val staticBasePath: String = "/partials"
) : HttpHandler {
  val connectionId = if (request.exchange.isPartialsRequest()) {
    request.get(PARTIALS_CONNECTION_ID_HEADER) ?: error("No connection id found in partials request!")
  } else {
    generateToken()
  }

  private val partials = mutableSetOf("page-data")

  fun getPartialsConnection() = PartialsConnections.partialConnections[connectionId]

  open fun process(): String? {
    return null
  }

  fun refresh(vararg parts: String) {
    partials.addAll(parts)
  }

  override fun handleRequest(exchange: HttpServerExchange) {
    request.value(PARTIALS_TIMEZONE_ID) { id ->
      session.timezone = ZoneId.of(id)
    }

    val redirectUrl = process()

    if (redirectUrl != null) {
      if (exchange.isPartialsRequest()) {
        exchange.responseHeaders.put(Headers.CONTENT_TYPE, "plain/html")
        exchange.responseSender.send("location: $redirectUrl")
      } else {
        exchange.statusCode = 302
        exchange.responseHeaders.put(Headers.LOCATION, redirectUrl)
      }
    } else {
      exchange.responseHeaders.put(Headers.CONTENT_TYPE, "text/html; charset=UTF-8")
      val content = generateContent(exchange)
      exchange.responseSender.send(content)
    }
  }

  private fun Builder.renderDataInput(update: Boolean) {
    input {
      type = InputType.hidden
      id = "page-data"
      name = "page-data"
      value = data.encode()
    }
  }

  abstract fun Builder.content(exchange: HttpServerExchange)

  fun Builder.render(exchange: HttpServerExchange) {
    html {
      head()
      body {
        main {
          form(method = FormMethod.post, encType = FormEncType.multipartFormData) {
            id = "page-form"
            classes += ""

            this@render.content(exchange)

            renderDataInput(false)
            input {
              type = InputType.hidden
              id = PARTIALS_CONNECTION_ID_HEADER
              name = PARTIALS_CONNECTION_ID_HEADER
              value = connectionId
            }
            input {
              type = InputType.hidden
              id = PARTIALS_TIMEZONE_ID
              name = PARTIALS_TIMEZONE_ID
              value = "0"
            }
            script {
              src = "$staticBasePath/kotlin-partials.mjs"
              type = "module"
            }
          }
        }
      }
    }
  }

  open fun HTML.head() {
    head {
      id = "page-head"
      title {
        id = "page-title"
        +"simple-kotlin-pages"
      }
    }
  }

  fun generateContent(exchange: HttpServerExchange): ByteBuffer {
    val bldr = if (exchange.isPartialsRequest()) {
      PartialsBuilder(partials, prettyPrint = true, xhtmlCompatible = true)
    } else {
      HtmlBuilder(prettyPrint = true, xhtmlCompatible = true)
    }
    val consumer = DelayedConsumer(bldr)

    consumer.render(exchange)

    val result = consumer.finalize()
    val concat = StringBuilder()

    if (exchange.isPartialsRequest()) {
      concat.append("<div>\n")
      concat.append(result)
      concat.append("\n</div>")
    } else {
      concat.append("<!DOCTYPE html>\n")
      concat.append(result)
    }

    return ByteBuffer.wrap(concat.toString().toByteArray(Charsets.UTF_8))
  }
}
