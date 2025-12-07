package nl.astraeus.tmpl

import kotlinx.browser.document
import kotlinx.html.div
import nl.astraeus.komp.HtmlBuilder
import nl.astraeus.komp.Komponent


class HelloKomponent : Komponent() {
  override fun HtmlBuilder.render() {
    div {
      + "Hello, world!"
    }
  }
}

fun main() {
  Komponent.create(document.body!!, HelloKomponent())
}
