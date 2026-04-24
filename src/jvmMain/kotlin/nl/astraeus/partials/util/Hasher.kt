package nl.astraeus.partials.util

import nl.astraeus.partials.web.PartialConfig
import java.security.MessageDigest
import kotlin.io.encoding.Base64

object Hasher {

  fun stableShortId(
    input: String,
    prefix: String = "id-",
    bytes: Int = PartialConfig.hashBytes
  ): String {
    val digestInstance = MessageDigest.getInstance("SHA-256")
    val digest = digestInstance.digest(input.toByteArray())
    val shortened = digest.copyOf(bytes)
    val encoded = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(shortened)
    return prefix + encoded
  }

}
