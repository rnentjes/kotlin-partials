package nl.astraeus.partials

interface PartialsLogger {

  fun trace(message: String)
  fun log(message: String)
  fun error(message: String, e: Throwable? = null)

}

class DefaultPartialsLogger : PartialsLogger {
  var traceEnabled = false
  override fun trace(message: String) {
    if (traceEnabled) {
      println(message)
    }
  }

  override fun log(message: String) {
    println(message)
  }

  override fun error(message: String, e: Throwable?) {
    System.err.println("Error: $message")
    e?.printStackTrace(System.err)
  }
}
