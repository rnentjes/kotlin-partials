package nl.astraeus.tmpl.web

import java.security.SecureRandom

val idChars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
val random = SecureRandom()

fun generateId(length: Int = 8): String {
  val id = StringBuilder()

  repeat(length) {
    id.append(idChars[random.nextInt(idChars.length)])
  }

  return id.toString()
}
