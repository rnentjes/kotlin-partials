package nl.astraeus.partials.test

import nl.astraeus.partials.createPartialsServer
import java.io.Serializable

data class TestSession(
  var user: String = "anon"
) : Serializable

fun main() {
  val servers = createPartialsServer(
    2500,
    { TestSession() },
    "/index" to IndexPage::class,
    "/dashboard" to DashboardPage::class,
  )

  println("Start server op poort 2500")
  servers.start()
}
