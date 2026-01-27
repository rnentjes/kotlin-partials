package nl.astraeus.partials.tag

import kotlinx.html.TagConsumer
import kotlinx.html.consumers.delayed
import kotlinx.html.consumers.onFinalizeMap
import kotlinx.html.stream.HTMLStreamBuilder

private const val AVERAGE_PAGE_SIZE = 32768

fun createHTML(prettyPrint: Boolean = true, xhtmlCompatible: Boolean = false): TagConsumer<String> =
  HTMLStreamBuilder(
    StringBuilder(AVERAGE_PAGE_SIZE),
    prettyPrint,
    xhtmlCompatible
  ).onFinalizeMap { sb, _ -> sb.toString() }.delayed()

fun <O : Appendable> O.appendHTML(prettyPrint: Boolean = true, xhtmlCompatible: Boolean = false): TagConsumer<O> =
  HTMLStreamBuilder(this, prettyPrint, xhtmlCompatible).delayed()

@Deprecated("Should be resolved to the previous implementation", level = DeprecationLevel.HIDDEN)
fun <O : Appendable> O.appendHTML(prettyPrint: Boolean = true): TagConsumer<O> =
  appendHTML(prettyPrint, false)

private val escapeMap = mapOf(
  '<' to "&lt;",
  '>' to "&gt;",
  '&' to "&amp;",
  '\"' to "&quot;"
).let { mappings ->
  val maxCode = mappings.keys.maxOfOrNull { it.code } ?: -1

  Array(maxCode + 1) { mappings[it.toChar()] }
}

private val letterRangeLowerCase = 'a'..'z'
private val letterRangeUpperCase = 'A'..'Z'
private val digitRange = '0'..'9'

private fun Char._isLetter() = this in letterRangeLowerCase || this in letterRangeUpperCase
private fun Char._isDigit() = this in digitRange

internal fun String.isValidXmlAttributeName() =
  !startsWithXml()
      && this.isNotEmpty()
      && (this[0]._isLetter() || this[0] == '_')
      && this.all { it._isLetter() || it._isDigit() || it in "._:-" }

private fun String.startsWithXml() = length >= 3
    && (this[0].let { it == 'x' || it == 'X' })
    && (this[1].let { it == 'm' || it == 'M' })
    && (this[2].let { it == 'l' || it == 'L' })

internal fun Appendable.escapeAppend(value: CharSequence) {
  var lastIndex = 0
  val mappings = escapeMap
  val size = mappings.size

  var currentIndex = 0
  while (currentIndex < value.length) {
    val code = value[currentIndex].code

    if (code == '\\'.code && currentIndex + 1 < value.length && value[currentIndex + 1] == '&') {
      append(value.substring(lastIndex, currentIndex))
      check(currentIndex + 1 < value.length) { "String must not end with '\\'." }
      append(value[currentIndex + 1])
      lastIndex = currentIndex + 2
      currentIndex += 2
      continue
    }

    if (code < 0 || code >= size) {
      currentIndex++
      continue
    }

    val escape = mappings[code]
    if (escape != null) {
      append(value.substring(lastIndex, currentIndex))
      append(escape)
      lastIndex = currentIndex + 1
    }

    currentIndex++
  }

  if (lastIndex < value.length) {
    append(value.substring(lastIndex, value.length))
  }
}

internal fun Appendable.escapeComment(s: CharSequence) {
  var start = 0
  while (start < s.length) {
    val index = s.indexOf("--")
    if (index == -1) {
      if (start == 0) {
        append(s)
      } else {
        append(s, start, s.length)
      }
      break
    }

    append(s, start, index)
    start += 2
  }
}