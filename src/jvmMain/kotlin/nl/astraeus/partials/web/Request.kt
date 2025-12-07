package nl.astraeus.partials.web

import io.undertow.server.HttpServerExchange
import io.undertow.server.handlers.form.FormData
import io.undertow.server.handlers.form.FormParserFactory
import io.undertow.util.HttpString

abstract class Request(
  val exchange: HttpServerExchange
) {
  abstract val data: Map<String, String>
  var pageData: String? = null

  val path: String = exchange.relativePath

  fun getId(s: String): String = if (path.startsWith(s) && path.length > s.length) {
    path.substring(s.length)
  } else {
    ""
  }

  fun get(parameter: String): String? = data[parameter]

  fun value(parameter: String, action: (String) -> Unit) {
    get(parameter)?.let { v ->
      action(v)
    }
  }

  fun getInt(parameter: String): Int? = get(parameter)?.toInt()
  fun getLong(parameter: String): Long? = get(parameter)?.toLong()
  fun getCheckbox(parameter: String): Boolean = ("on" == get(parameter))
  fun parts(): List<String> = path.split("/").filter { it.isNotBlank() }
}

class QueryParametersRequest(
  exchange: HttpServerExchange
) : Request(
  exchange
) {
  override val data = mutableMapOf<String, String>()

  init {
    exchange.queryParameters.forEach { (key, values) ->
      if (key != "page-data") {
        data[key] = values.firstOrNull() ?: ""
      } else {
        pageData = values.firstOrNull()
      }
    }
  }
}

class FormDataRequest(
  exchange: HttpServerExchange
) : Request(exchange) {
  override val data = mutableMapOf<String, String>()

  init {
    val parser = FormParserFactory.builder().build().createParser(exchange)
    var formData = FormData(0)
    if (parser != null) {
      formData = parser.parseBlocking()
      formData.iterator().forEach { key ->
        if (key != "page-data") {
          data[key] = formData.get(key).firstOrNull()?.value ?: ""
        } else {
          pageData = formData.get(key).firstOrNull()?.value
        }
      }
    }
  }
}

fun HttpServerExchange.request(): Request {
  val request: Request = when (requestMethod) {
    HttpString("GET") -> {
      QueryParametersRequest(this)
    }

    HttpString("POST") -> {
      startBlocking()
      val parser = FormParserFactory.builder().build().createParser(this)
      var formData = FormData(0)
      if (parser != null) {
        formData = parser.parseBlocking()
      }
      FormDataRequest(this)
    }

    else -> {
      throw IllegalStateException("Unsupported request method: $requestMethod")
    }
  }
  return request
}
