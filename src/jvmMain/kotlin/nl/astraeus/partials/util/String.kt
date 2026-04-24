package nl.astraeus.partials.util

fun String.camelToDash(): String {
  val result = StringBuilder()
  var capital = false

  for ((index, ch) in this.withIndex()) {
    if (ch.isUpperCase() && !capital) {
      if (index > 0) {
        result.append("-")
      }
      result.append(ch.lowercase())
      capital = true
    } else if (ch.isLowerCase() && capital) {
      result.append(ch)
      capital = false
    } else {
      result.append(ch)
    }
  }

  return result.toString()
}