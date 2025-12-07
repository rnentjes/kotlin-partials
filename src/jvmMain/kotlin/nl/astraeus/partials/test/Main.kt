package nl.astraeus.partials.test

import nl.astraeus.partials.createPartialsServer

fun main() {
  val servers = createPartialsServer(
    2500,
    IndexPage::class,
    "/index" to IndexPage::class,
    "/dashboard" to DashboardPage::class,
  )

  println("Start server op poort 2500")
  servers.start()
}
