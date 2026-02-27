package nl.astraeus.partials

import kotlinx.browser.document
import kotlinx.browser.window
import org.khronos.webgl.Uint8Array
import org.w3c.dom.HTMLInputElement
import org.w3c.fetch.Headers
import org.w3c.fetch.INCLUDE
import org.w3c.fetch.RequestCredentials
import org.w3c.fetch.RequestInit
import kotlin.js.json

object PasskeyHandler {

  private fun enc(s: String): String = (js("encodeURIComponent")(s) as String)

  // --- Helpers: WebAuthn requires ArrayBuffer/Uint8Array for binary fields ---
  private fun base64UrlToUint8Array(base64url: String): Uint8Array {
    // Replace URL-safe chars and add padding
    var base64 = base64url.replace('-', '+').replace('_', '/')
    val pad = (4 - base64.length % 4) % 4
    base64 += "=".repeat(pad)

    val binary = window.atob(base64)
    val bytes = Uint8Array(binary.length)
    for (i in 0 until binary.length) {
      bytes.asDynamic()[i] = (binary[i].code and 0xFF)
    }
    return bytes
  }

  private fun transformPublicKeyCredentialRequestOptions(options: dynamic): dynamic {
    val pk = if (options.publicKey != null) options.publicKey else options
    val out = js("Object.assign({}, pk)")

    if (out.challenge != null) out.challenge = base64UrlToUint8Array(out.challenge as String)
    if (out.allowCredentials != null) {
      val allowCredentials = out.allowCredentials.slice()
      for (i in 0 until allowCredentials.length as Int) {
        val cred = js("Object.assign({}, allowCredentials[i])")
        if (cred.id != null) cred.id = base64UrlToUint8Array(cred.id as String)
        allowCredentials[i] = cred
      }
      out.allowCredentials = allowCredentials
    }
    return json("publicKey" to out)
  }

  private fun transformPublicKeyCredentialCreationOptions(options: dynamic): dynamic {
    val pk = if (options.publicKey != null) options.publicKey else options
    val out = js("Object.assign({}, pk)")

    if (out.challenge != null) out.challenge = base64UrlToUint8Array(out.challenge as String)
    if (out.user != null && out.user.id != null) {
      out.user = js("Object.assign({}, out.user)")
      out.user.id = base64UrlToUint8Array(out.user.id as String)
    }
    if (out.excludeCredentials != null) {
      val excludeCredentials = out.excludeCredentials.slice()
      for (i in 0 until excludeCredentials.length as Int) {
        val cred = js("Object.assign({}, excludeCredentials[i])")
        if (cred.id != null) cred.id = base64UrlToUint8Array(cred.id as String)
        excludeCredentials[i] = cred
      }
      out.excludeCredentials = excludeCredentials
    }
    return json("publicKey" to out)
  }

  fun register() {
    console.log("Register passkey")

    // 1. Get options from your server
    val headers = Headers().apply {
      append("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
    }

    val usernames = document.getElementsByName("passkey-username")
    if (usernames.length > 0) {
      val username = (usernames.item(0) as? HTMLInputElement)?.value ?: error("No username provided")
      val requestInit = RequestInit(
        method = "POST",
        headers = headers,
        body = "passkey-username=" + enc(username)
      )

      window.fetch("/partials/passkey/register/begin", requestInit)
        .then { resp ->
          resp.json().then { options ->
            // Convert binary fields and wrap into { publicKey: ... }
            val createOptions = transformPublicKeyCredentialCreationOptions(options)
            val promise = window.navigator.asDynamic().credentials.create(createOptions)

            promise.then { credential ->
              // 3. Send the result back to your server (form-encoded "json" field)
              val finishHeaders = Headers().apply {
                append("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
              }

              val finishRequestInit = RequestInit(
                method = "POST",
                headers = finishHeaders,
                body = "json=" + enc(JSON.stringify(credential))
              )

              window.fetch("/partials/passkey/register/finish", finishRequestInit)
            }
          }
        }
        .catch { err ->
          console.error("Passkey registration failed", err)
        }
    } else {
      error("No username provided")
    }
  }

  fun login() {
    console.log("Login passkey")

    // 1. Get options from your server
    val headers = Headers().apply {
      append("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
    }

    val requestInit = RequestInit(
      method = "POST",
      credentials = RequestCredentials.INCLUDE,
      headers = headers
    )

    window.fetch("/partials/passkey/login/begin", requestInit)
      .then { resp ->
        resp.json().then { options ->
          // 2. Browser/OS handles authentication
          val getOptions = transformPublicKeyCredentialRequestOptions(options)
          console.log("webauthn get options", getOptions)
          val promise = window.navigator.asDynamic().credentials.get(getOptions)

          promise.then { credential ->
            // 3. Send the result back to your server
            val finishHeaders = Headers().apply {
              append("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            }

            val finishRequestInit = RequestInit(
              method = "POST",
              credentials = RequestCredentials.INCLUDE,
              headers = finishHeaders,
              body = "json=" + enc(JSON.stringify(credential))
            )

            window.fetch("/partials/passkey/login/finish", finishRequestInit)
          }
        }
      }
      .catch { err ->
        console.error("Passkey login failed", err)
      }
  }

}
