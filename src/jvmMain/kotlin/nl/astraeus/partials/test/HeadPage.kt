package nl.astraeus.partials.test

import kotlinx.html.*
import nl.astraeus.partials.web.PartialsPage
import nl.astraeus.partials.web.Request
import java.io.Serializable

abstract class HeadPage<T : Serializable>(
  request: Request,
  session: TestSession,
  data: T,
) : PartialsPage<TestSession, T>(
  request,
  session,
  data,
) {
  open var pageTitle: String = "Partials"

  override fun HTML.head() {
    head {
      id = "page-head"
      title {
        id = "page-title"
        +pageTitle
      }
      link {
        rel = "stylesheet"
        href = "/static/pico.fluid.classless.min.css"
      }
    }
  }

}
