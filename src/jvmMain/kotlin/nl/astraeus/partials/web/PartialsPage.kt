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

class NoData : Serializable

abstract class PartialsPage<T : Serializable>(
  val request: Request,
  val data: T,
) : HttpHandler {
  private val partials = mutableSetOf("page-data")

  open fun process(): String? {
    return null
  }

  fun refresh(partial: String) {
    partials.add(partial)
  }

  override fun handleRequest(exchange: HttpServerExchange) {
    val method = exchange.requestMethod.toString()

    val redirectUrl = when (method) {
      "POST" -> {
        if (exchange.isInIoThread) {
          exchange.dispatch(this)
          return
        }
        exchange.startBlocking() // Enable reading POST data
        process()
      }

      "GET" -> process()
      else -> null
    }

    if (redirectUrl != null) {
      exchange.responseHeaders.put(Headers.CONTENT_TYPE, "plain/html")
      exchange.responseSender.send("location: $redirectUrl")
      exchange.endExchange()
    } else {
      exchange.responseHeaders.put(Headers.CONTENT_TYPE, "text/html")
      exchange.responseSender.send(generateContent(exchange))
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
          form(method = FormMethod.post, encType = FormEncType.applicationXWwwFormUrlEncoded) {
            id = "page-form"

            this@render.content(exchange)

            renderDataInput(false)
            script {
              src = "/kotlin-kotlin-stdlib.js"
              type = "application/javascript"
            }
            script {
              src = "/kotlin-partials.js"
              type = "application/javascript"
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

  fun HTMLTag.doPost(
    eventName: String,
    vararg parameters: Pair<String, String>
  ) {
    this.attributes["data-p-${eventName}"] = parameters.joinToString("&") { pair ->
      "${pair.first}=${pair.second}"
    }
  }

  fun HTMLTag.onClick(vararg parameters: Pair<String, String>) = doPost("click", *parameters)

  fun HTMLTag.onChange(vararg parameters: Pair<String, String>) = doPost("change", *parameters)

  fun HTMLTag.onBlur(vararg parameters: Pair<String, String>) = doPost("blur", *parameters)

  fun HTMLTag.onKeyUp(vararg parameters: Pair<String, String>) = doPost("keyup", *parameters)

  protected fun isHtmxRequest(exchange: HttpServerExchange): Boolean {
    return exchange.requestHeaders.getFirst("XX-Request") == "true"
  }

  fun generateContent(exchange: HttpServerExchange): ByteBuffer {
    val bldr = if (isHtmxRequest(exchange)) {
      PartialsBuilder(partials, prettyPrint = true, xhtmlCompatible = true)
    } else {
      HtmlBuilder(prettyPrint = true, xhtmlCompatible = true)
    }
    val consumer = DelayedConsumer(bldr)

    consumer.render(exchange)

    val result = consumer.finalize()

    if (isHtmxRequest(exchange)) {
      val concat = StringBuilder("<div>\n")
      concat.append(result)
      concat.append("\n</div>")
      return ByteBuffer.wrap(concat.toString().toByteArray(Charsets.UTF_8))
    } else {
      return ByteBuffer.wrap(result.toByteArray(Charsets.UTF_8))
    }
  }
}
