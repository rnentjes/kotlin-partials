package nl.astraeus.partials.test

import com.webauthn4j.authenticator.Authenticator
import nl.astraeus.partials.web.PartialsCredentialRepository
import nl.astraeus.partials.web.StoredCredential
import java.util.*

class InMemoryCredentialRepo(
  override val domain: String = "localhost",
  override val applicationName: String = "Partials Test",
  override val origins: Set<String> = setOf("http://localhost:2500"),
) : PartialsCredentialRepository() {
  // username → user handle
  private val userHandles = mutableMapOf<String, ByteArray>()

  // username → set of stored credentials
  private val credentialsByUsername = mutableMapOf<String, MutableSet<StoredCredential>>()

  // credentialId (base64) → credential store (for fast lookup)
  private val credentialsById = mutableMapOf<String, StoredCredential>()

  /**
   * Call this after a successful registration to persist the new credential.
   */
  override fun addCredential(
    username: String,
    userHandle: ByteArray,
    credentialId: ByteArray,
    authenticator: Authenticator,
  ) {
    val store = StoredCredential(
      credentialId = credentialId,
      userHandle = userHandle,
      username = username,
      authenticator = authenticator,
    )

    userHandles[username] = userHandle
    credentialsByUsername.getOrPut(username) { mutableSetOf() }.add(store)
    credentialsById[credentialId.base64Url()] = store
  }

  /**
   * Call this after a successful authentication to update the signature counter.
   */
  override fun updateSignatureCount(credentialId: ByteArray, newCount: Long) {
    val key = credentialId.base64Url()
    val existing = credentialsById[key] ?: return
    existing.authenticator.counter = newCount
  }

  override fun getCredentialsForUsername(username: String): Set<ByteArray> {
    return credentialsByUsername[username]?.map { it.credentialId }?.toSet() ?: emptySet()
  }

  override fun getUsernameForUserHandle(userHandle: ByteArray): String? {
    return userHandles.entries.firstOrNull { it.value.contentEquals(userHandle) }?.key
  }

  override fun lookup(credentialId: ByteArray): StoredCredential? {
    return credentialsById[credentialId.base64Url()]
  }

  private fun ByteArray.base64Url(): String {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(this)
  }
}
