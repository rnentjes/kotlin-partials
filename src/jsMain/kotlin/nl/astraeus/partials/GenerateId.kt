package nl.astraeus.partials

import kotlin.js.Date
import kotlin.random.Random

val idChars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
val random = Random(Date().getTime().toLong())

fun generateId(length: Int = 4): String {
  val id = StringBuilder()

  for (i in 0 until 8) {
    id.append(idChars[random.nextInt(idChars.length)])
  }

  return id.toString()
}

fun generateToken(): String {
  val token = StringBuilder()

  repeat(32) {
    token.append(idChars[random.nextInt(idChars.length)])
  }

  return token.toString()
}
