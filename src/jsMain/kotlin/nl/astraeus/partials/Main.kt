package nl.astraeus.partials

import kotlinx.browser.document
import kotlinx.browser.window
import nl.astraeus.partials.web.PARTIALS_REQUEST_HEADER
import org.w3c.dom.*
import org.w3c.dom.url.URLSearchParams
import org.w3c.xhr.FormData
import org.w3c.xhr.XMLHttpRequest

fun main() {
  window.onload = { updateHtml(document.body!!) }
}

val attributeMap = mapOf(
  "data-p-click" to "click",
  "data-p-blur" to "blur",
  "data-p-change" to "change",
  "data-p-keyup" to "keyup",
)

var activeElement: Element? = null

fun updateHtml(element: Element) {
  var inputSelectionStart = -1
  var taSelectionStart = -1

  val ae = activeElement
  if (ae is HTMLInputElement) {
    inputSelectionStart = ae.selectionStart ?: -1
  }
  if (ae is HTMLTextAreaElement) {
    taSelectionStart = ae.selectionStart ?: -1
  }

  for ((attribute, eventName) in attributeMap) {
    if (element.getAttribute(attribute) != null) {
      addEventToPartialsElement(element, eventName)
    }

    val list = element.querySelectorAll("[${attribute}]") ?: return

    for (index in 0..<list.length) {
      val element = list.item(index) as Element
      addEventToPartialsElement(element, eventName)
    }
  }
  activeElement?.id?.also { id ->
    if (id.isNotBlank()) {
      val elementById = document.getElementById(id)
      elementById?.asDynamic()?.focus()
      if (inputSelectionStart != -1) {
        (elementById as? HTMLInputElement)?.setSelectionRange(
          inputSelectionStart,
          inputSelectionStart
        )
      }
      if (taSelectionStart != -1) {
        (elementById as? HTMLTextAreaElement)?.setSelectionRange(
          taSelectionStart,
          taSelectionStart
        )
      }
    }
  }
}

private fun addEventToPartialsElement(element: Element, eventName: String) {
  element.addEventListener(eventName, {
    showSplash()
    activeElement = document.activeElement

    val form = document.getElementById("page-form") as? HTMLFormElement
    val formData = if (form != null) {
      FormData(form)
    } else {
      FormData()
    }
    val params = URLSearchParams(element.getAttribute("data-p-${eventName}") ?: "")
    params.asDynamic().keys().forEach { key -> formData.append(key, params.get(key) ?: "") }

    val xhr = XMLHttpRequest()
    xhr.open("POST", window.location.href, true)
    xhr.setRequestHeader("Content-Type", "application/x-www-form-urlencoded")
    xhr.setRequestHeader(PARTIALS_REQUEST_HEADER, "true")

    xhr.onload = {
      handleServerResponse(xhr)
      hideSplash()
    }

    xhr.onerror = {
      console.error("Request failed", xhr)
      hideSplash()
    }

    xhr.send(URLSearchParams(formData).toString())
  })
}

private fun handleServerResponse(xhr: XMLHttpRequest) {
  if (xhr.status == 200.toShort()) {
    val response = xhr.response
    if (response is String) {
      if (response.startsWith("location: ")) {
        val location = response.substringAfter("location: ")
        window.location.href = location
      } else {
        val div = document.createElement("div")
        div.innerHTML = xhr.responseText

        if (div.children.length == 1) {
          // Execute embedded scripts
          val scripts = div.querySelectorAll("script")
          val newScripts = mutableListOf<HTMLScriptElement>()
          for (index in 0..<scripts.length) {
            (scripts.item(index) as? HTMLScriptElement)?.also { oldScript ->
              val newScript: HTMLScriptElement = document.createElement("script") as HTMLScriptElement
              if (oldScript.src.isBlank() && oldScript.textContent?.isNotBlank() == true) {
                newScript.textContent = oldScript.textContent
              } else {
                newScript.src = oldScript.src
              }
              newScript.defer = oldScript.defer
              newScript.type = oldScript.type
              newScripts.add(newScript)
            }
          }

          val children = div.children.item(0)?.children
          while ((children?.length ?: 0) > 0) {
            val child = children?.item(0)
            if (child != null) {
              val id = child.id
              val elementToReplace = document.getElementById(child.id)
              if (elementToReplace != null) {
                elementToReplace.replaceWith(child)

                document.getElementById(id)?.let { elementToUpdate ->
                  updateHtml(elementToUpdate)
                }
              } else {
                console.warn("Could not find element in the DOM to replace with id $id")
                child.remove()
              }
            }
          }

          for (script in newScripts) {
            document.body?.appendChild(script)
          }
        } else {
          console.error("Received html does not contain exactly one root element.", xhr)
        }
      }
    }
  } else {
    console.error("Error: ${xhr.status}", xhr)
  }
}

fun showSplash() {
  val splash = document.createElement("div")
  splash.setAttribute(
    "style",
    "position: fixed; left: 0; top: 0; width: 100%; height: 100%; background-color: transparent; z-index: 1000;"
  )
  splash.setAttribute(
    "id",
    "page-xx-splash"
  )
  document.body?.appendChild(splash)
}

fun hideSplash() {
  document.getElementById("page-xx-splash")?.remove()
}
