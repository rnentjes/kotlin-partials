package nl.astraeus.partials.web

import java.security.SecureRandom

val idChars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
val random = SecureRandom()

fun generateId(length: Int = 4): String {
  val id = StringBuilder()

  for (i in 0 until 8) {
    id.append(idChars[random.nextInt(idChars.length)])
  }

  return id.toString()
}

fun generateToken(): String {
  val token = StringBuilder(System.currentTimeMillis().toString(36))

  repeat(32) {
    token.append(idChars[random.nextInt(idChars.length)])
  }

  return token.toString()
}
