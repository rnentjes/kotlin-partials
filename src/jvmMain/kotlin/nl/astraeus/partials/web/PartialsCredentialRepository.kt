package nl.astraeus.partials.web

import com.webauthn4j.authenticator.Authenticator

typealias WebAuthByteArray = ByteArray

abstract class PartialsCredentialRepository {
  abstract val domain: String
  abstract val applicationName: String
  abstract val origins: Set<String>

  /**
   * Call this after a successful registration to persist the new credential.
   */
  abstract fun addCredential(
    username: String,
    userHandle: WebAuthByteArray,
    credentialId: WebAuthByteArray,
    authenticator: Authenticator,
  )

  abstract fun getCredentialsForUsername(username: String): Set<WebAuthByteArray>

  abstract fun getUsernameForUserHandle(userHandle: WebAuthByteArray): String?

  abstract fun lookup(credentialId: WebAuthByteArray): StoredCredential?

  abstract fun updateSignatureCount(credentialId: WebAuthByteArray, newCount: Long)
}

data class StoredCredential(
  val credentialId: WebAuthByteArray,
  val userHandle: WebAuthByteArray,
  val username: String,
  val authenticator: Authenticator,
)
