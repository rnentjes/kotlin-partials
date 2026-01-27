package nl.astraeus.partials

import kotlinx.browser.document
import kotlinx.browser.window
import nl.astraeus.partials.web.PARTIALS_REQUEST_HEADER
import org.w3c.dom.*
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.url.URLSearchParams
import org.w3c.fetch.*
import org.w3c.files.FileList
import org.w3c.xhr.FormData

object PartialsHandler {
  private val attributeNames = listOf(
    "click",
    "blur",
    "change",
    "keyup",
    "submit",
    "dblclick",
    "enter",
    "file-drop",
  )

  private var activeElement: Element? = null

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

    attributeNames.forEach { eventName ->
      if (element.getAttribute("data-p-${eventName}") != null) {
        addEventToPartialsElement(element, eventName)
      }

      val list = element.querySelectorAll("[data-p-${eventName}]") ?: return

      repeat(list.length) { index ->
        val element = list.item(index) as Element
        addEventToPartialsElement(element, eventName)
      }
    }

    activeElement?.id?.also { id ->
      if (id.isNotBlank()) {
        val elementById = document.getElementById(id)
        (elementById as? HTMLElement)?.focus()
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
    when (eventName) {
      "enter" -> element.addEventListener("keydown", { event ->
        if (event is KeyboardEvent) {
          if (event.key == "Enter") {
            event.preventDefault()

            sendPartialEvent(element.getAttribute("data-p-${eventName}") ?: "")
          }
        }
      })

      "file-drop" -> {
        element.addEventListener("dragover", { event ->
          event.preventDefault()
          event.stopPropagation()
        })

        element.addEventListener("drop", { event ->
          event.preventDefault()
          event.stopPropagation()

          if (event is DragEvent) {
            val dt = event.dataTransfer
            val files = dt!!.files

            sendPartialEvent(
              element.getAttribute("data-p-${eventName}") ?: "",
              files
            )
          }
        })
      }

      else -> {
        element.addEventListener(eventName, {
          activeElement = document.activeElement

          sendPartialEvent(element.getAttribute("data-p-${eventName}") ?: "")
        })
      }
    }
  }

  fun sendPartialEvent(
    parameters: String = "",
    fileList: FileList? = null
  ) {
    showSplash()

    val form = document.getElementById("page-form") as? HTMLFormElement
    val formData = if (form != null) {
      FormData(form)
    } else {
      FormData()
    }

    if (parameters.isNotBlank()) {
      val params = URLSearchParams(parameters)
      params.asDynamic().keys().forEach { key -> formData.append(key, params.get(key) ?: "") }
    }

    fileList?.let {
      for (index in 0 until it.length) {
        formData.append("partial-files", it.item(index)!!)
      }
    }

    val headers = Headers().also {
      it.append(PARTIALS_REQUEST_HEADER, "true")
    }

    val requestInit = RequestInit(
      method = "POST",
      headers = headers,
      body = formData,
      credentials = RequestCredentials.SAME_ORIGIN
    )

    window.fetch(window.location.href, requestInit)
      .then { response ->
        response.text().then { bodyText ->
          handleServerResponse(response, bodyText)
        }
      }
      .catch { error ->
        console.error("Request failed", error)

        window.alert("An error occurred while processing your request. Please try again later.")
        window.location.reload()
      }
      .finally {
        hideSplash()
      }
  }

  private fun handleServerResponse(response: Response, bodyText: String) {
    if (response.status == 200.toShort()) {
      if (bodyText.startsWith("location: ")) {
        val location = bodyText.substringAfter("location: ")
        window.location.href = location
      } else {
        handleHtmlResponse(bodyText)
      }
    } else {
      console.error("Error: ${response.status}", response)
      window.alert("An error occurred while processing your request. Please try again later.")
      window.location.reload()
    }
  }

  fun handleHtmlResponse(html: String) {
    val div = document.createElement("div")
    div.innerHTML = html

    if (div.children.length == 1) {
      val newScripts = extractScripts(div)

      replaceElements(div)

      for (script in newScripts) {
        document.body?.appendChild(script)
      }
    } else {
      console.error("Received html does not contain exactly one root element.", html)
    }
  }

  private fun replaceElements(div: Element) {
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
  }

  private fun extractScripts(div: Element): MutableList<HTMLScriptElement> {
    // Execute embedded scripts so they are executed
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

        newScript.addEventListener("load", {
          newScript.remove()
        })
        newScript.addEventListener("error", {
          console.error("Error loading script", newScript)
          newScript.remove()
        })
      }
    }

    return newScripts
  }

  private fun showSplash() {
    val splash = document.createElement("div") as HTMLDivElement
    with(splash.style) {
      position = "fixed"
      left = "0"
      top = "0"
      width = "100%"
      height = "100%"
      backgroundColor = "transparent"
      zIndex = "1000"
    }
    splash.id = "partials-splash"
    document.body?.appendChild(splash)
  }

  private fun hideSplash() {
    document.getElementById("partials-splash")?.remove()
  }
}
