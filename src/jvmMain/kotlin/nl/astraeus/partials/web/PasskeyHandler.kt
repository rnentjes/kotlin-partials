package nl.astraeus.partials.web

import com.webauthn4j.WebAuthnAuthenticationManager
import com.webauthn4j.WebAuthnRegistrationManager
import com.webauthn4j.converter.util.ObjectConverter
import com.webauthn4j.credential.CredentialRecordImpl
import com.webauthn4j.data.AuthenticationParameters
import com.webauthn4j.data.AuthenticatorSelectionCriteria
import com.webauthn4j.data.PublicKeyCredentialCreationOptions
import com.webauthn4j.data.PublicKeyCredentialDescriptor
import com.webauthn4j.data.PublicKeyCredentialParameters
import com.webauthn4j.data.PublicKeyCredentialRequestOptions
import com.webauthn4j.data.PublicKeyCredentialRpEntity
import com.webauthn4j.data.PublicKeyCredentialType
import com.webauthn4j.data.PublicKeyCredentialUserEntity
import com.webauthn4j.data.RegistrationParameters
import com.webauthn4j.data.ResidentKeyRequirement
import com.webauthn4j.data.UserVerificationRequirement
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier
import com.webauthn4j.data.client.Origin
import com.webauthn4j.data.client.challenge.Challenge
import com.webauthn4j.data.client.challenge.DefaultChallenge
import com.webauthn4j.server.ServerProperty
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
  private val objectConverter = ObjectConverter()
  private val registrationManager = WebAuthnRegistrationManager.createNonStrictWebAuthnRegistrationManager(objectConverter)
  private val authenticationManager = WebAuthnAuthenticationManager()

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
    val userHandle = ByteArray(32).also { random.nextBytes(it) }

    if (username == null) {
      // return error
      exchange.statusCode = StatusCodes.BAD_REQUEST
    } else {
      val options = PublicKeyCredentialCreationOptions(
        PublicKeyCredentialRpEntity(cr.applicationName, cr.domain),
        PublicKeyCredentialUserEntity(userHandle, username, username),
        DefaultChallenge(),
        publicKeyCredentialParameters(),
        null,
        cr.getCredentialsForUsername(username).map {
          PublicKeyCredentialDescriptor(PublicKeyCredentialType.PUBLIC_KEY, it, null)
        },
        AuthenticatorSelectionCriteria(null, ResidentKeyRequirement.REQUIRED, UserVerificationRequirement.PREFERRED),
        null,
        null,
      )

      val json = objectConverter.jsonConverter.writeValueAsString(options)
      exchange.getSession().getPartialsSession<PartialsSession>()?.passkeyOptions = json

      exchange.responseHeaders.put(Headers.CONTENT_TYPE, "application/json")
      exchange.responseSender.send(json, Charsets.UTF_8)
    }
    exchange.endExchange()
  }

  private fun finishRegistration(
    exchange: HttpServerExchange
  ) {
    val request = exchange.request()
    val json = request.get("json") ?: error("json not found")
    val optionsJson = exchange.getSession().getPartialsSession<PartialsSession>()?.passkeyOptions
      ?: error("passkeyOptions not found")
    val options = objectConverter.jsonConverter.readValue(
      optionsJson,
      PublicKeyCredentialCreationOptions::class.java,
    ) ?: error("passkeyOptions not found")
    val serverProperty = serverProperty(options.challenge)

    val registrationData = registrationManager.verify(
      json,
      RegistrationParameters(serverProperty, options.pubKeyCredParams, true, false)
    )
    val credentialRecord = CredentialRecordImpl(
      registrationData.attestationObject ?: error("Attestation object not found"),
      registrationData.collectedClientData,
      registrationData.clientExtensions,
      registrationData.transports,
    )

    cr.addCredential(
      username = options.user.name,
      userHandle = options.user.id,
      credentialId = credentialRecord.attestedCredentialData.credentialId,
      credentialRecord = credentialRecord,
    )
  }

  private fun beginAuthentication(
    exchange: HttpServerExchange
  ) {
    val options = PublicKeyCredentialRequestOptions(
      DefaultChallenge(),
      null,
      cr.domain,
      null,
      UserVerificationRequirement.PREFERRED,
      null,
    )

    // Store `options` in session/cache
    val json = objectConverter.jsonConverter.writeValueAsString(options)

    exchange.getSession().getPartialsSession<PartialsSession>()?.assertionRequest = json

    exchange.responseHeaders.put(Headers.CONTENT_TYPE, "application/json")
    exchange.responseSender.send(json, Charsets.UTF_8)
  }

  private fun finishAuthentication(
    exchange: HttpServerExchange
  ) {
    val request = exchange.request()
    val json = request.get("json") ?: error("json not found")
    val partialsSession = exchange.getSession().getPartialsSession<PartialsSession>()
    val optionsJson = partialsSession?.assertionRequest ?: error("assertionRequest not found")
    val options = objectConverter.jsonConverter.readValue(
      optionsJson,
      PublicKeyCredentialRequestOptions::class.java,
    ) ?: error("assertionRequest not found")
    val authenticationData = authenticationManager.parse(json)
    val credentialId = authenticationData.credentialId ?: error("Credential id not found")
    val credential = cr.lookup(credentialId) ?: error("Credential not found")

    authenticationManager.verify(
      authenticationData,
      AuthenticationParameters(
        serverProperty(options.challenge),
        credential.credentialRecord,
        null,
        true,
        false,
      )
    )

    cr.updateSignatureCount(credential.credentialId, authenticationData.authenticatorData?.signCount ?: 0)
    partialsSession.passkeyUsername = authenticationData.userHandle?.let { cr.getUsernameForUserHandle(it) } ?: credential.username
  }

  private fun publicKeyCredentialParameters(): List<PublicKeyCredentialParameters> {
    return listOf(
      PublicKeyCredentialParameters(PublicKeyCredentialType.PUBLIC_KEY, COSEAlgorithmIdentifier.ES256),
      PublicKeyCredentialParameters(PublicKeyCredentialType.PUBLIC_KEY, COSEAlgorithmIdentifier.RS256),
    )
  }

  private fun serverProperty(challenge: Challenge): ServerProperty {
    return ServerProperty(
      cr.origins.map { Origin(it) }.toSet(),
      cr.domain,
      challenge,
      null,
    )
  }

}
