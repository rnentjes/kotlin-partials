package nl.astraeus.partials.test

import kotlinx.html.HTML
import kotlinx.html.head
import kotlinx.html.id
import kotlinx.html.link
import kotlinx.html.title
import nl.astraeus.partials.web.PartialKey
import nl.astraeus.partials.web.PartialsPage
import java.io.Serializable

abstract class HeadPage<T : Serializable, K : PartialKey>(
  initialData: () -> T
) : PartialsPage<TestSession, T, K>(initialData) {
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
