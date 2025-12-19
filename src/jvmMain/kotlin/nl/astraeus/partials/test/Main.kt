package nl.astraeus.partials.test

import io.undertow.server.HttpServerExchange
import io.undertow.server.session.InMemorySessionManager
import io.undertow.server.session.Session
import io.undertow.server.session.SessionListener
import io.undertow.server.session.SessionManager
import kotlinx.html.HtmlBlockTag
import nl.astraeus.partials.createPartialsServer
import nl.astraeus.partials.web.PartialsConnections.partialConnections
import nl.astraeus.partials.web.PartialsSession
import java.io.Serializable
import java.util.concurrent.CopyOnWriteArraySet

data class TestSession(
  var user: String = "anon",
) : PartialsSession(), Serializable

fun main() {
  val sessionManager: SessionManager = InMemorySessionManager("SESSION_MANAGER")
  sessionManager.registerSessionListener(testSessionListener)

  val servers = createPartialsServer(
    2500,
    { TestSession() },
    "/index" to IndexPage::class,
    "/dashboard" to DashboardPage::class,
    sessionManager = sessionManager
  )

  println("Start server op poort 2500")
  servers.start()

  var done = false

  while (!done) {
    Thread.sleep(999)
    println("We have ${sessions.size} sessions and are sending partials to ${partialConnections.size} connections")
    val timePartial: HtmlBlockTag.() -> Unit = { renderTimePartial() }
    for (connection in partialConnections.values) {
      connection.sendPartials(timePartial)
    }
  }
}

val sessions = CopyOnWriteArraySet<Session>()

val testSessionListener = object : SessionListener {
  override fun sessionCreated(
    session: Session,
    exchange: HttpServerExchange,
  ) {
    sessions.add(session)
  }

  override fun sessionDestroyed(
    session: Session,
    exchange: HttpServerExchange,
    reason: SessionListener.SessionDestroyedReason?
  ) {
    sessions.remove(session)
  }
}
