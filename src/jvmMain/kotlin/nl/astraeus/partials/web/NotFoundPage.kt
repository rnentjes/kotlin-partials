package nl.astraeus.partials.web

import io.undertow.server.HttpServerExchange
import kotlinx.html.HTML
import kotlinx.html.body
import kotlinx.html.h1
import kotlinx.html.head
import kotlinx.html.html
import kotlinx.html.id
import kotlinx.html.p
import kotlinx.html.title

open class NotFoundPage<S : PartialsSession>() : PartialsPage<S, NoData, PartialKey>({ NoData() }) {
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
