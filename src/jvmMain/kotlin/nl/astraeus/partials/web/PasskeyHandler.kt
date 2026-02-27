package nl.astraeus.partials.web

import com.yubico.webauthn.AssertionRequest
import com.yubico.webauthn.FinishAssertionOptions
import com.yubico.webauthn.FinishRegistrationOptions
import com.yubico.webauthn.RelyingParty
import com.yubico.webauthn.StartAssertionOptions
import com.yubico.webauthn.StartRegistrationOptions
import com.yubico.webauthn.data.AuthenticatorSelectionCriteria
import com.yubico.webauthn.data.ByteArray
import com.yubico.webauthn.data.PublicKeyCredential
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions
import com.yubico.webauthn.data.RelyingPartyIdentity
import com.yubico.webauthn.data.ResidentKeyRequirement
import com.yubico.webauthn.data.UserIdentity
import com.yubico.webauthn.data.UserVerificationRequirement
import io.undertow.server.HttpHandler
import io.undertow.server.HttpServerExchange
import io.undertow.util.Headers
import io.undertow.util.StatusCodes

class PasskeyHandler(
  private val next: HttpHandler,
  private val cr: PartialsCredentialRepository,
  private val registerBegin: String,
  private val registerFinish: String,
  private val loginBegin: String,
  private val loginFinish: String,
) : HttpHandler {
  val rpIdentity = RelyingPartyIdentity.builder()
    .id(cr.domain)       // Must match your domain
    .name(cr.applicationName)
    .build()

  val rp = RelyingParty.builder()
    .identity(rpIdentity)
    .credentialRepository(cr) // You implement this interface
    .origins(cr.origins)
    .build()

  override fun handleRequest(exchange: HttpServerExchange) {
    val path = exchange.requestPath

    when (path) {
      registerBegin -> {
        beginRegistration(exchange)
      }

      registerFinish -> {
        finishRegistration(exchange)
      }

      loginBegin -> {
        beginAuthentication(exchange)
      }

      loginFinish -> {
        finishAuthentication(exchange)
      }

      else -> {
        next.handleRequest(exchange)
      }
    }
  }

  private fun beginRegistration(exchange: HttpServerExchange) {
    if (exchange.isInIoThread) {
      exchange.dispatch(this)
      return
    }

    val request = exchange.request()
    val username = request.get("passkey-username")
    val userHandle = ByteArray(ByteArray(32).also { random.nextBytes(it) })

    if (username == null) {
      // return error
      exchange.statusCode = StatusCodes.BAD_REQUEST
    } else {
      val user = UserIdentity.builder()
        .name(username)
        .displayName(username)
        .id(userHandle)  // random user handle
        .build()

      val options = rp.startRegistration(
        StartRegistrationOptions.builder()
          .user(user)
          .authenticatorSelection(
            AuthenticatorSelectionCriteria.builder()
              .residentKey(ResidentKeyRequirement.REQUIRED)  // enables passkey
              .userVerification(UserVerificationRequirement.PREFERRED)
              .build()
          )
          .build()
      )

      val json = options.toCredentialsCreateJson()
      exchange.getSession().getPartialsSession<PartialsSession>()?.passkeyOptions = options.toJson()

      exchange.responseHeaders.put(Headers.CONTENT_TYPE, "application/json")
      exchange.responseSender.send(json, Charsets.UTF_8)
    }
    exchange.endExchange()
  }

  private fun finishRegistration(
    exchange: HttpServerExchange
  ) {
    val request = exchange.request()
    val json = request.get("json")
    val pkc = PublicKeyCredential.parseRegistrationResponseJson(json)

    val options = PublicKeyCredentialCreationOptions.fromJson(
      exchange.getSession().getPartialsSession<PartialsSession>()?.passkeyOptions ?: error("passkeyOptions not found")
    )

    val result = rp.finishRegistration(
      FinishRegistrationOptions.builder()
        .request(options)   // the options from begin step
        .response(pkc)
        .build()
    )

    cr.addCredential(
      username = options.user.name,
      userHandle = options.user.id,
      credentialId = result.keyId.id,
      publicKeyCose = result.publicKeyCose,
      signatureCount = result.signatureCount
    )
  }

  private fun beginAuthentication(
    exchange: HttpServerExchange
  ) {
    val options = rp.startAssertion(
      StartAssertionOptions.builder()
        .userVerification(UserVerificationRequirement.PREFERRED)
        .build()
    )

    // Store `options` in session/cache
    val json = options.toCredentialsGetJson()

    exchange.getSession().getPartialsSession<PartialsSession>()?.assertionRequest = options.toJson()

    exchange.responseHeaders.put(Headers.CONTENT_TYPE, "application/json")
    exchange.responseSender.send(json, Charsets.UTF_8)
  }

  private fun finishAuthentication(
    exchange: HttpServerExchange
  ) {
    val request = exchange.request()
    val json = request.get("json")
    val pkc = PublicKeyCredential.parseAssertionResponseJson(json)
    val partialsSession = exchange.getSession().getPartialsSession<PartialsSession>()
    val options = AssertionRequest.fromJson(
      partialsSession?.assertionRequest ?: error("assertionRequest not found")
    )

    val result = rp.finishAssertion(
      FinishAssertionOptions.builder()
        .request(options)
        .response(pkc)
        .build()
    )

    if (result.isSuccess) {
      // Update signatureCount in your DB
      // Create a session for result.username
      cr.updateSignatureCount(result.credential.userHandle, result.signatureCount)
      partialsSession.passkeyUsername = result.username
    }
  }

}
