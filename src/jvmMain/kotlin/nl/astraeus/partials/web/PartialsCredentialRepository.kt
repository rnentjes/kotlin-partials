package nl.astraeus.partials.web

import com.yubico.webauthn.CredentialRepository
import com.yubico.webauthn.data.ByteArray

abstract class PartialsCredentialRepository : CredentialRepository {
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
    publicKeyCose: ByteArray,
    signatureCount: Long,
  )

  abstract fun updateSignatureCount(credentialId: ByteArray, newCount: Long)
}
