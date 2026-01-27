package nl.astraeus.partials.tag

import kotlinx.html.Entities
import kotlinx.html.Tag
import kotlinx.html.TagConsumer
import kotlinx.html.Unsafe
import kotlinx.html.org.w3c.dom.events.Event

class PartialsBuilder(
  private val partials: Set<String>,
  private val prettyPrint: Boolean = true,
  private val xhtmlCompatible: Boolean = true
) : TagConsumer<String> {
  val out = StringBuilder()
  var outputting = false

  private var ln = true
  private var level: Int = 0
  private var outputtingLevel = 0
  private var outputtingId = ""

  override fun onTagStart(tag: Tag) {
    if (prettyPrint && !tag.inlineTag) {
      indent()
    }
    level++

    val id = tag.attributes["id"]
    if (id != null && partials.contains(id) && !outputting) {
      outputting = true
      outputtingId = id
    }

    if (outputting) {
      outputtingLevel++

      out.append("<")
      out.append(tag.tagName)

      if (tag.namespace != null) {
        out.append(" xmlns=\"")
        out.append(tag.namespace)
        out.append("\"")
      }

      if (tag.attributes.isNotEmpty()) {
        tag.attributesEntries.forEachIndexed { _, e ->
          if (!e.key.isValidXmlAttributeName()) {
            throw IllegalArgumentException("Tag ${tag.tagName} has invalid attribute name ${e.key}")
          }

          out.append(' ')
          out.append(e.key)
          out.append("=\"")
          out.escapeAppend(e.value)
          out.append('\"')
        }
      }

      if (xhtmlCompatible && tag.emptyTag) {
        out.append("/")
      }

      out.append(">")
      ln = false
    }
  }

  override fun onTagAttributeChange(tag: Tag, attribute: String, value: String?) {
    throw UnsupportedOperationException("tag attribute can't be changed as it was already written to the stream. Use with DelayedConsumer to be able to modify attributes")
  }

  override fun onTagEvent(tag: Tag, event: String, value: (Event) -> Unit) {
    throw UnsupportedOperationException("you can't assign lambda event handler when building text")
  }

  override fun onTagEnd(tag: Tag) {
    level--
    if (outputting) {
      if (ln) {
        indent()
      }

      if (!tag.emptyTag) {
        out.append("</")
        out.append(tag.tagName)
        out.append(">")
      }

      if (prettyPrint && !tag.inlineTag) {
        appendln()
      }

      outputtingLevel--
      if (outputtingLevel == 0) {
        outputting = false
        outputtingId = ""
      }
    }
  }

  override fun onTagContent(content: CharSequence) {
    if (outputting) {
      out.escapeAppend(content)
      ln = false
    }
  }

  override fun onTagContentEntity(entity: Entities) {
    if (outputting) {
      out.append(entity.text)
      ln = false
    }
  }

  override fun finalize(): String {
    return out.toString()
  }

  override fun onTagContentUnsafe(block: Unsafe.() -> Unit) {
    if (outputting) {
      unsafeImpl.block()
    }
  }

  override fun onTagComment(content: CharSequence) {
    if (outputting) {
      if (prettyPrint) {
        indent()
      }

      out.append("<!--")
      out.escapeComment(content)
      out.append("-->")

      ln = false
    }
  }

  private val unsafeImpl = object : Unsafe {
    override operator fun String.unaryPlus() {
      out.append(this)
    }
  }

  private fun appendln() {
    if (outputting) {
      if (prettyPrint && !ln) {
        out.append("\n")
        ln = true
      }
    }
  }

  private fun indent() {
    if (outputting) {
      if (prettyPrint) {
        if (!ln) {
          out.append("\n")
        }
        var remaining = level
        while (remaining >= 4) {
          out.append("        ")
          remaining -= 4
        }
        while (remaining >= 2) {
          out.append("    ")
          remaining -= 2
        }
        if (remaining > 0) {
          out.append("  ")
        }
        ln = false
      }
    }
  }
}
