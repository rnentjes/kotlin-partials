package nl.astraeus.partials.tag

import kotlinx.html.Entities
import kotlinx.html.Tag
import kotlinx.html.TagConsumer
import kotlinx.html.Unsafe
import kotlinx.html.org.w3c.dom.events.Event


class IdInjectConsumer(val downstream: TagConsumer<String>) : TagConsumer<String> {
  private var delayed: Tag? = null
  private var injectId: String? = null

  fun inject(id: String) {
    processDelayedTag()
    this.injectId = id
  }

  override fun onTagStart(tag: Tag) {
    processDelayedTag()
    delayed = tag
  }

  override fun onTagAttributeChange(tag: Tag, attribute: String, value: String?) {
    if (delayed == null || delayed != tag) {
      throw IllegalStateException("You can't change tag attribute because it was already passed to the downstream")
    }
  }

  override fun onTagEvent(tag: Tag, event: String, value: (Event) -> Unit) {
    if (delayed == null || delayed != tag) {
      throw IllegalStateException("You can't change tag attribute because it was already passed to the downstream")
    }
  }

  override fun onTagEnd(tag: Tag) {
    processDelayedTag()
    downstream.onTagEnd(tag)
  }

  override fun onTagContent(content: CharSequence) {
    processDelayedTag()
    downstream.onTagContent(content)
  }

  override fun onTagContentEntity(entity: Entities) {
    processDelayedTag()
    downstream.onTagContentEntity(entity)
  }

  override fun onTagComment(content: CharSequence) {
    processDelayedTag()
    downstream.onTagComment(content)
  }

  override fun finalize(): String {
    processDelayedTag()
    return downstream.finalize()
  }

  override fun onTagContentUnsafe(block: Unsafe.() -> Unit) {
    processDelayedTag()
    return downstream.onTagContentUnsafe(block)
  }

  private fun processDelayedTag() {
    delayed?.let { tag ->
      injectId?.also {
        tag.attributes["id"] = it
      }
      injectId = null
      delayed = null
      downstream.onTagStart(tag)
    }
  }
}