package nl.astraeus.partials

interface PartialsLogger {

  fun log(message: String)
  fun error(message: String, e: Throwable? = null)

}

class DefaultPartialsLogger : PartialsLogger {
  override fun log(message: String) {
    println(message)
  }

  override fun error(message: String, e: Throwable?) {
    System.err.println(message)
    e?.printStackTrace(System.err)
  }
}
