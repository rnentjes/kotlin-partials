package nl.astraeus.tmpl.web

import kotlinx.html.body
import kotlinx.html.head
import kotlinx.html.html
import kotlinx.html.meta
import kotlinx.html.script
import kotlinx.html.stream.appendHTML
import kotlinx.html.title
import nl.astraeus.tmpl.REPO_NAME
import nl.astraeus.tmpl.itemUrl
import nl.astraeus.tmpl.pageTitle
import nl.astraeus.tmpl.repoName

fun generateIndex(patch: String?): String {
  val result = StringBuilder();

  if (patch == null) {
    result.appendHTML(true).html {
      head {
        title(pageTitle)
        //link("/css/all.min.css", "stylesheet")
      }
      body {
        script(src = "/$repoName.js") {}
      }
    }
  } else {
    result.appendHTML(true).html {
      head {
        title(pageTitle)
        meta {
          httpEquiv = "refresh"
          content = "0; url=/$itemUrl/$patch"
        }
      }
      body {
        +"Redirecting to $itemUrl $patch..."
      }
    }
  }

  return result.toString()
}
