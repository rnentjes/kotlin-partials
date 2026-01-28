package nl.astraeus.partials.web

import io.undertow.server.HttpHandler
import io.undertow.server.HttpServerExchange
import io.undertow.util.HttpString
import nl.astraeus.partials.partialsLogger
import java.io.Serializable
import java.lang.reflect.InvocationTargetException
import kotlin.reflect.KClass
import kotlin.reflect.cast

interface PageFactory<S : PartialsSession, T : Serializable> {
  val dataClass: KClass<T>
  fun create(): PartialsPage<S, T>
}

fun <S : PartialsSession, T : Serializable> pageFactory(
  dataClass: KClass<T>,
  create: () -> PartialsPage<S, T>
): PageFactory<S, T> = object : PageFactory<S, T> {
  override val dataClass: KClass<T> = dataClass
  override fun create(): PartialsPage<S, T> = create()
}

inline fun <S : PartialsSession, reified T : Serializable> pageFactory(
  noinline create: () -> PartialsPage<S, T>
): PageFactory<S, T> = pageFactory(T::class, create)

class PartialsHandler<S : PartialsSession>(
  val defaultPage: PageFactory<S, *>,
  val session: () -> S,
  val next: HttpHandler? = null,
  vararg val mappings: Pair<String, PageFactory<S, *>>
) : HttpHandler {

  init {
    // todo: check mappings for types constructors and data class types
    // cache constructors
  }

  override fun handleRequest(exchange: HttpServerExchange) {
    val path = exchange.relativePath

    if (exchange.requestMethod == HttpString("POST") && exchange.isInIoThread) {
      exchange.dispatch(this)
      return
    }

    val request = exchange.request()
    if (path == "/partials-sse") {
      val handler = PartialsSSEHandler(request)
      handler.handleRequest(exchange)
      return
    }

    var factory: PageFactory<S, *>? = null
    if (path == "/") {
      factory = defaultPage
    } else {
      for (mapping in mappings) {
        if (path.startsWith(mapping.first)) {
          factory = mapping.second
        }
      }
    }

    if (factory != null) {
      var session = exchange.getPartialsSession<S>()
      if (session == null) {
        partialsLogger.trace("Creating new session in path: ${exchange.requestPath} - ID: ${exchange.getSession().id}")
        session = session()
        exchange.setPartialsSession(session)
      }

      try {
        @Suppress("UNCHECKED_CAST")
        val typedFactory = factory as PageFactory<S, Serializable>
        val handler = typedFactory.create()

        val data = if (request.pageData == null) {
          handler.initialData.invoke()
        } else {
          (request.pageData as String).decode()
        }

        handler.request = request
        handler.session = session
        handler.data = typedFactory.dataClass.cast(data)

        handler.onInit()

        handler.handleRequest(exchange)
      } catch (e: InvocationTargetException) {
        partialsLogger.error(
          "Failed to initialize PartialsPage for path $path with factory ${factory::class.simpleName}. " +
              "Make sure you don't access request, session or data at construction time of the class, use onInit() instead.",
          e
        )
        throw e
      }
    } else {
      next?.handleRequest(exchange) ?: error("No handler found for path $path")
    }
  }
}
