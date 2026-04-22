package nl.astraeus.partials.web

import io.undertow.server.HttpHandler
import io.undertow.server.HttpServerExchange
import io.undertow.util.Headers
import kotlinx.html.CoreAttributeGroupFacade
import kotlinx.html.FormEncType
import kotlinx.html.FormMethod
import kotlinx.html.HTML
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
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.nio.ByteBuffer
import java.time.ZoneId
import kotlin.io.encoding.Base64
import kotlin.reflect.KClass

typealias Builder = IdInjectConsumer
typealias PartialRenderer = Builder.(Any?, Long) -> Unit
typealias RenderFunction = Builder.(PartialsPage<*, *, *>, Any?, Long) -> Unit

abstract class PartialComponent<S : PartialsSession, T : Serializable> {
  internal val partialsToRefresh = mutableSetOf<RefreshPartial>()

  fun refresh(key: PartialKey, data: Any? = null, id: Long = 0) {
    partialsToRefresh.add(RefreshPartial(key, data, id))
  }

  abstract fun process(request: Request, session: S, pageData: T)

  fun render(consumer: Builder, session: S, pageData: T, data: Any?, id: Long) {
    consumer.content(session, pageData, data, id)
  }

  abstract fun Builder.content(session: S, pageData: T, data: Any?, id: Long)
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

interface PartialKey {
  val name: String

  fun id(): String = this.name.lowercase().replace('_', '-')
}

data class RefreshPartial(
  val key: PartialKey,
  val data: Any? = null,
  val id: Long = 0
)

data class RefreshFunction(
  val func: RenderFunction,
  val data: Any? = null,
  val id: Long = 0,
  val last: Boolean = false,
)

enum class PageDataKey : PartialKey {
  PAGE_DATA,
  ;
}

abstract class PartialsPage<S : PartialsSession, T : Serializable, K : PartialKey>(
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

  internal val partialsToRefresh = mutableSetOf<RefreshPartial>()
  internal val functionToRefresh = mutableSetOf<RefreshFunction>()
  internal val partials = mutableMapOf<PartialKey, PartialRenderer>()
  internal val partialComponents = mutableMapOf<PartialKey, PartialComponent<S, T>>()

  fun getPartialsConnection() = PartialsConnections.partialConnections[connectionId]

  open fun onInit() {}

  fun partial(name: PartialKey, renderer: PartialRenderer) {
    check(!partials.containsKey(name)) { "Partial $name already define for ${this::class.simpleName}" }

    partials[name] = renderer
  }

  fun partial(name: PartialKey, renderer: PartialComponent<S, T>) {
    check(!partials.containsKey(name)) { "Partial $name already define for ${this::class.simpleName}" }

    partialComponents[name] = renderer
  }

  open fun process(): String? {
    return null
  }

  fun refresh(key: PartialKey, data: Any? = null, id: Long = 0) {
    check(partials.containsKey(key)) {
      "Partial `$key` not defined in ${this@PartialsPage::class.simpleName}"
    }
    partialsToRefresh.add(RefreshPartial(key, data, id))
  }

  fun refresh(func: RenderFunction, data: Any? = null, id: Long = 0, last: Boolean = false) {
    functionToRefresh.add(RefreshFunction(func, data, id, last))
  }

  override fun handleRequest(exchange: HttpServerExchange) {
    request.value(PARTIALS_TIMEZONE_ID) { id ->
      session.timezone = ZoneId.of(id)
    }

    val redirectUrl = runProcessing()

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

  private fun runProcessing(): String? {
    for ((_, component) in partialComponents) {
      component.process(request, this.session, this.data)
    }

    return process()
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

  fun Builder.partial(key: PartialKey, data: Any? = null, id: Long = 0) {
    check(partials.containsKey(key) || partialComponents.containsKey(key)) {
      "Partial `$key` not defined in ${this@PartialsPage::class.simpleName}"
    }
    partials[key]?.also { partial ->
      renderPartial(key, data, id, partial)
    }
    partialComponents[key]?.also { partial ->
      renderPartial(key, session, this@PartialsPage.data, data, id, partial)
    }
  }

  fun Builder.partial(func: RenderFunction, data: Any? = null, id: Long = 0) {
    renderPartialFunction(
      this@partial,
      RefreshFunction(func, data, id)
    )
  }

  fun KClass<*>.nameId(): String {
    return Hasher.stableShortId(this.toString())
  }

  fun renderPartialFunction(consumer: Builder, rf: RefreshFunction) {
    val functionName = rf.func::class.nameId()

    val elementId = functionName + if (rf.id > 0L) {
      "-${rf.id}"
    } else {
      ""
    }

    consumer.inject(elementId)
    rf.func.invoke(consumer, this@PartialsPage, rf.data, rf.id)
  }

  open fun Builder.render(exchange: HttpServerExchange) {
    html {
      head()
      body {
        main {
          form(method = FormMethod.post, encType = FormEncType.multipartFormData) {
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
    val bldr = HtmlBuilder(prettyPrint = true, xhtmlCompatible = true)
    val consumer = Builder(bldr)

    if (exchange.isPartialsRequest()) {
      consumer.div {
        title = "Partials container"
        val refreshers = mutableSetOf<RefreshPartial>()
        for ((key, component) in partialComponents) {
          refreshers.addAll(component.partialsToRefresh)
        }
        refreshers.addAll(partialsToRefresh)
        for (refresh in refreshers) {
          partials[refresh.key]?.also { prt ->
            consumer.renderPartial(
              refresh.key,
              refresh.data,
              refresh.id,
              prt
            )
          }
          partialComponents[refresh.key]?.also { partial ->
            consumer.renderPartial(
              refresh.key,
              session,
              this@PartialsPage.data,
              refresh.data,
              refresh.id,
              partial
            )
          }
        }

        for (rf in functionToRefresh) {
          if (!rf.last) {
            renderPartialFunction(consumer, rf)
          }
        }
        for (rf in functionToRefresh) {
          if (rf.last) {
            renderPartialFunction(consumer, rf)
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

  private fun Builder.renderPartial(
    key: PartialKey,
    data: Any?,
    id: Long,
    prt: PartialRenderer
  ) {
    val elementId = key.id() + if (id > 0) {
      "-${id}"
    } else {
      ""
    }

    this@renderPartial.inject(elementId)
    this@renderPartial.prt(data, id)
  }

  private fun Builder.renderPartial(
    key: PartialKey,
    session: S,
    pageData: T,
    data: Any?,
    id: Long,
    prt: PartialComponent<S, T>
  ) {
    val elementId = key.id() + if (id > 0) {
      "-${id}"
    } else {
      ""
    }

    this@renderPartial.inject(elementId)
    prt.render(this@renderPartial, session, pageData, data, id)
  }
}
