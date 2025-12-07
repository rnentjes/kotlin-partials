package nl.astraeus.partials.web

import io.undertow.server.HttpServerExchange
import kotlinx.html.*
import java.io.Serializable

open class NotFoundPage<S : Serializable, T : Serializable>(
  request: Request,
  session: S,
  data: T,
) : PartialsPage<S, T>(
  request,
  session,
  data,
) {
  open var pageTitle: String = "Not found"

  override fun Builder.content(exchange: HttpServerExchange) {
    html {
      head()
      body {
        h1 { +"Not found" }
        p {
          +"The requested page could not be found."
        }
      }
    }
  }

  override fun HTML.head() {
    head {
      id = "page-head"
      title {
        id = "page-title"

        +pageTitle
      }
    }
  }
}
