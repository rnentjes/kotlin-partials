package nl.astraeus.partials.tag

import kotlinx.html.Entities
import kotlinx.html.Tag
import kotlinx.html.TagConsumer
import kotlinx.html.Unsafe
import kotlinx.html.org.w3c.dom.events.Event

class PartialsProcessor() : TagConsumer<String> {
  override fun onTagStart(tag: Tag) {}

  override fun onTagAttributeChange(tag: Tag, attribute: String, value: String?) {
    throw UnsupportedOperationException("tag attribute can't be changed as it was already written to the stream. Use with DelayedConsumer to be able to modify attributes")
  }

  override fun onTagEvent(tag: Tag, event: String, value: (Event) -> Unit) {
    throw UnsupportedOperationException("you can't assign lambda event handler when building text")
  }

  override fun onTagEnd(tag: Tag) {}

  override fun onTagContent(content: CharSequence) {}

  override fun onTagContentEntity(entity: Entities) {}

  override fun finalize(): String {
    return ""
  }

  override fun onTagContentUnsafe(block: Unsafe.() -> Unit) {}

  override fun onTagComment(content: CharSequence) {}

  private val unsafeImpl = object : Unsafe {
    override fun String.unaryPlus() {
    }
  }

  private fun appendln() {}

  private fun indent() {}
}
