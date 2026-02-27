package nl.astraeus.partials.test

import com.yubico.webauthn.RegisteredCredential
import com.yubico.webauthn.data.ByteArray
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor
import nl.astraeus.partials.web.PartialsCredentialRepository
import java.util.*

data class CredentialStore(
  val credentialId: ByteArray,
  val userHandle: ByteArray,
  val username: String,
  val publicKeyCose: ByteArray,
  val signatureCount: Long,
)

class InMemoryCredentialRepo : PartialsCredentialRepository() {
  override val domain: String = "localhost"
  override val applicationName: String = "Partials Test"
  override val origins: Set<String> = setOf("http://localhost:2500")

  // username → user handle
  private val userHandles = mutableMapOf<String, ByteArray>()

  // username → set of stored credentials
  private val credentialsByUsername = mutableMapOf<String, MutableSet<CredentialStore>>()

  // credentialId (base64) → credential store (for fast lookup)
  private val credentialsById = mutableMapOf<String, CredentialStore>()

  /**
   * Call this after a successful registration to persist the new credential.
   */
  override fun addCredential(
    username: String,
    userHandle: ByteArray,
    credentialId: ByteArray,
    publicKeyCose: ByteArray,
    signatureCount: Long,
  ) {
    val store = CredentialStore(
      credentialId = credentialId,
      userHandle = userHandle,
      username = username,
      publicKeyCose = publicKeyCose,
      signatureCount = signatureCount,
    )

    userHandles[username] = userHandle
    credentialsByUsername.getOrPut(username) { mutableSetOf() }.add(store)
    credentialsById[credentialId.base64Url] = store
  }

  /**
   * Call this after a successful authentication to update the signature counter.
   */
  override fun updateSignatureCount(credentialId: ByteArray, newCount: Long) {
    val key = credentialId.base64Url
    val existing = credentialsById[key] ?: return
    val updated = existing.copy(signatureCount = newCount)

    credentialsById[key] = updated
    credentialsByUsername[existing.username]?.let { set ->
      set.remove(existing)
      set.add(updated)
    }
  }

  override fun getCredentialIdsForUsername(username: String): Set<PublicKeyCredentialDescriptor> {
    return credentialsByUsername[username]?.map { cred ->
      PublicKeyCredentialDescriptor.builder()
        .id(cred.credentialId)
        .build()
    }?.toSet() ?: emptySet()
  }

  override fun getUserHandleForUsername(username: String?): Optional<ByteArray> {
    if (username == null) return Optional.empty()
    return Optional.ofNullable(userHandles[username])
  }

  override fun getUsernameForUserHandle(userHandle: ByteArray): Optional<String> {
    val username = userHandles.entries.firstOrNull { it.value == userHandle }?.key
    return Optional.ofNullable(username)
  }

  override fun lookup(
    credentialId: ByteArray,
    userHandle: ByteArray
  ): Optional<RegisteredCredential> {
    val store = credentialsById[credentialId.base64Url] ?: return Optional.empty()

    return Optional.of(
      RegisteredCredential.builder()
        .credentialId(store.credentialId)
        .userHandle(store.userHandle)
        .publicKeyCose(store.publicKeyCose)
        .signatureCount(store.signatureCount)
        .build()
    )
  }

  override fun lookupAll(credentialId: ByteArray): Set<RegisteredCredential> {
    val store = credentialsById[credentialId.base64Url] ?: return emptySet()

    return setOf(
      RegisteredCredential.builder()
        .credentialId(store.credentialId)
        .userHandle(store.userHandle)
        .publicKeyCose(store.publicKeyCose)
        .signatureCount(store.signatureCount)
        .build()
    )
  }
}
