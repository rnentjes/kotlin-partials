package nl.astraeus.partials.web

import io.undertow.server.HttpHandler
import io.undertow.server.HttpServerExchange
import io.undertow.util.Headers
import kotlinx.html.CoreAttributeGroupFacade
import kotlinx.html.FormEncType
import kotlinx.html.FormMethod
import kotlinx.html.INPUT
import kotlinx.html.InputType
import kotlinx.html.body
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.head
import kotlinx.html.html
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.main
import kotlinx.html.script
import kotlinx.html.title
import nl.astraeus.partials.tag.HtmlBuilder
import nl.astraeus.partials.tag.IdInjectConsumer
import nl.astraeus.partials.util.Hasher
import nl.astraeus.partials.util.camelToDash
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.nio.ByteBuffer
import java.time.ZoneId
import kotlin.io.encoding.Base64
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

typealias Builder = IdInjectConsumer
typealias RenderFunction = Builder.(Any?, Long) -> Unit

class RegisteredRenderFunction(
  val id: String,
  val renderFunction: RenderFunction,
)

class RenderFunctionDelegate(
  private val func: RenderFunction
) {
  operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): ReadOnlyProperty<Any?, RegisteredRenderFunction> {
    val ownerName = thisRef?.javaClass?.simpleName ?: "top-level"
    val key = "$ownerName-${property.name}".camelToDash()
    val registered = RegisteredRenderFunction(
      if (PartialConfig.debug) {
        key
      } else {
        Hasher.stableShortId(key)
      },
      func
    )
    return ReadOnlyProperty { _, _ -> registered }
  }
}

fun partial(func: RenderFunction) = RenderFunctionDelegate(func)

fun Builder.partial(rrf: RegisteredRenderFunction, data: Any? = null, id: Long = 0) {
  renderPartialFunction(UpdateFunction(rrf, data, id))
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

fun CoreAttributeGroupFacade.onPasskeyRegister(vararg parameters: Pair<String, String>) = doPost("passkey-register", *parameters)

fun CoreAttributeGroupFacade.onPasskeyLogin(vararg parameters: Pair<String, String>) = doPost("passkey-login", *parameters)

fun CoreAttributeGroupFacade.updateClass(clzz: String) {
  this.attributes["data-p-class"] = clzz
}

fun INPUT.onEnter(vararg parameters: Pair<String, String>) {
  doPost("enter", *parameters)
}

fun HttpServerExchange.isPartialsRequest(): Boolean {
  return requestHeaders.getFirst(PARTIALS_REQUEST_HEADER) == "true"
}

abstract class PartialsSession : Serializable {
  val id = System.currentTimeMillis().toString() + System.nanoTime().toString()
  var timezone: ZoneId = ZoneId.systemDefault()
  var passkeyOptions: String? = null
  var assertionRequest: String? = null
  var passkeyUsername: String? = null
}

class NoData : Serializable

data class UpdateFunction(
  val rrf: RegisteredRenderFunction,
  val data: Any? = null,
  val id: Long = 0,
  val last: Boolean = false,
)

object PartialConfig {
  var debug = false
  val hashBytes: Int = 8
}

fun Builder.renderPartialFunction(rf: UpdateFunction) {
  val elementId = rf.rrf.id + if (rf.id > 0L) {
    "-${rf.id}"
  } else {
    ""
  }

  this.inject(elementId)
  rf.rrf.renderFunction.invoke(this, rf.data, rf.id)
}

abstract class PartialsPage<S : PartialsSession, T : Serializable>(
  val initialData: () -> T
) : HttpHandler {
  lateinit var request: Request
  lateinit var session: S
  lateinit var data: T
  var staticBasePath: String = "/partials"

  val connectionId by lazy {
    if (request.exchange.isPartialsRequest()) {
      request.get(PARTIALS_CONNECTION_ID_HEADER) ?: error("No connection id found in partials request!")
    } else {
      generateToken()
    }
  }

  internal val functionToUpdate = mutableSetOf<UpdateFunction>()

  val partialsConnection: PartialsConnection?
    get() = PartialsConnections.partialConnections[connectionId]

  open fun onInit() {}

  open fun process(): String? {
    return null
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

  private fun Builder.renderDataInput() {
    div {
      id = "page-data"
      input {
        type = InputType.hidden
        name = "page-data"
        value = encodeData()
      }
    }
  }

  open fun encodeData(): String {
    val byteArrayOutputStream = ByteArrayOutputStream()
    ObjectOutputStream(byteArrayOutputStream).use { oos ->
      oos.writeObject(data)
    }
    val bytes = byteArrayOutputStream.toByteArray()
    return Base64.encode(bytes)
  }

  open fun decodeData(data: String) {
    val bytes = Base64.decode(data)
    ObjectInputStream(bytes.inputStream()).use { ois ->
      this.data = ois.readObject() as T
    }
  }

  abstract fun Builder.content(exchange: HttpServerExchange)

  fun update(rrf: RegisteredRenderFunction, data: Any? = null, id: Long = 0, last: Boolean = false) {
    functionToUpdate.add(UpdateFunction(rrf, data, id, last))
  }

  open fun Builder.render(exchange: HttpServerExchange) {
    html {
      headContent()
      body {
        main {
          form(method = FormMethod.post, encType = FormEncType.multipartFormData) {
            acceptCharset = "UTF-8"
            id = "page-form"
            classes += ""

            this@render.content(exchange)

            renderDataInput()
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

  open fun Builder.headContent() {
    head {
      id = "page-head"
      title {
        id = "page-title"
        +"simple-kotlin-pages"
      }
    }
  }

  fun generateContent(exchange: HttpServerExchange): ByteBuffer {
    val bldr = HtmlBuilder(prettyPrint = true, xhtmlCompatible = true)
    val consumer = Builder(bldr)

    if (exchange.isPartialsRequest()) {
      consumer.div {
        title = "Partials container"

        for (rf in functionToUpdate) {
          if (!rf.last) {
            consumer.renderPartialFunction(rf)
          }
        }
        for (rf in functionToUpdate) {
          if (rf.last) {
            consumer.renderPartialFunction(rf)
          }
        }

        consumer.renderDataInput()
      }
    } else {
      consumer.render(exchange)
    }

    val result = consumer.finalize()
    val concat = StringBuilder()

    if (exchange.isPartialsRequest()) {
      concat.append(result)
    } else {
      concat.append("<!DOCTYPE html>\n")
      concat.append(result)
    }

    return ByteBuffer.wrap(concat.toString().toByteArray(Charsets.UTF_8))
  }
}
