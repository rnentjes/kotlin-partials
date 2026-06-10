package nl.astraeus.partials.test

import io.undertow.server.HttpServerExchange
import io.undertow.server.session.InMemorySessionManager
import io.undertow.server.session.Session
import io.undertow.server.session.SessionListener
import io.undertow.server.session.SessionManager
import nl.astraeus.partials.createPartialsServer
import nl.astraeus.partials.web.PartialConfig
import nl.astraeus.partials.web.PartialsConnections.partialConnections
import nl.astraeus.partials.web.PartialsSession
import java.io.Serializable
import java.time.ZoneId
import java.util.*
import java.util.concurrent.CopyOnWriteArraySet

data class TestSession(
  var user: String = "anon",
) : PartialsSession(), Serializable

fun main() {
  PartialConfig.debug = false
  TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
  val sessionManager: SessionManager = InMemorySessionManager("SESSION_MANAGER")
  sessionManager.registerSessionListener(testSessionListener)

  val servers = createPartialsServer<TestSession>(
    2500,
    { TestSession() },
    "/index" to IndexPage::class,
    "/dashboard" to DashboardPage::class,
    "/drag" to DragPage::class,
    sessionManager = sessionManager,
    credentialRepo = InMemoryCredentialRepo(),
  )

  println("Start server op poort 2500")
  servers.start()

  var done = false

  while (!done) {
    Thread.sleep(999)
    println("We have ${sessions.size} sessions and are sending partials to ${partialConnections.size} connections")
    for (connection in partialConnections.values) {
      if (connection.path == "/index") {
        connection.sendPartial(renderTimePartial, connection.getSession()?.timezone ?: ZoneId.systemDefault())
      }
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
