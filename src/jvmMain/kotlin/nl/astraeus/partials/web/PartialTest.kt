package nl.astraeus.partials.web

import kotlinx.html.div
import nl.astraeus.partials.tag.HtmlBuilder

typealias FunctionTest = Builder.() -> Unit

abstract class PartialTest {

  val functionsToRender = mutableSetOf<FunctionTest>()

  fun Builder.partial(name: String, test: FunctionTest) {

  }

  fun Builder.refresh(test: FunctionTest) {
    test()
  }

  fun render() {
    val consumer = Builder(HtmlBuilder())

    for (func in functionsToRender) {
      consumer.func()
    }
  }
}

val partial: FunctionTest = {
  div {
    +"Hello!"
  }
}

class TestPartial : PartialTest() {

  fun Builder.test() {
    partial("Blaat") {
      div {
        +"Hello!"
      }
    }
  }

}
