package nl.astraeus.partials.test

import kotlinx.html.head
import kotlinx.html.id
import kotlinx.html.link
import kotlinx.html.title
import nl.astraeus.partials.web.Builder
import nl.astraeus.partials.web.PartialsPage
import nl.astraeus.partials.web.partial
import java.io.Serializable

abstract class HeadPage<T : Serializable>(
  initialData: () -> T
) : PartialsPage<TestSession, T>(initialData) {
  open var pageTitle: String = "Partials"

  override fun Builder.headContent() {
    head {
      id = "page-head"
      partial(titlePartial)
      link {
        rel = "stylesheet"
        href = "/static/pico.fluid.classless.min.css"
      }
    }
  }

  val titlePartial by partial { _, _ ->
    title {
      id = "page-title"
      +pageTitle
    }
  }

}
