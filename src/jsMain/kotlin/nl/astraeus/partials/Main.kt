package nl.astraeus.partials

import kotlinx.browser.document
import kotlinx.browser.window

fun main() {
  window.onload = {
    PartialsHandler.updateHtml(document.body!!)
  }
}
