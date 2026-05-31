package nl.astraeus.partials.web

import com.webauthn4j.authenticator.Authenticator

abstract class PartialsCredentialRepository {
  abstract val domain: String
  abstract val applicationName: String
  abstract val origins: Set<String>

  /**
   * Call this after a successful registration to persist the new credential.
   */
  abstract fun addCredential(
    username: String,
    userHandle: ByteArray,
    credentialId: ByteArray,
    authenticator: Authenticator,
  )

  abstract fun getCredentialsForUsername(username: String): Set<ByteArray>

  abstract fun getUsernameForUserHandle(userHandle: ByteArray): String?

  abstract fun lookup(credentialId: ByteArray): StoredCredential?

  abstract fun updateSignatureCount(credentialId: ByteArray, newCount: Long)
}

data class StoredCredential(
  val credentialId: ByteArray,
  val userHandle: ByteArray,
  val username: String,
  val authenticator: Authenticator,
)
