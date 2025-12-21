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
    println(message)
    e?.printStackTrace()
  }
}
