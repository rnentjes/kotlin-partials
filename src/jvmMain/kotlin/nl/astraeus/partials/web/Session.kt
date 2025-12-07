package nl.astraeus.partials.web

import io.undertow.server.HttpServerExchange
import io.undertow.server.session.Session
import io.undertow.server.session.SessionConfig
import io.undertow.server.session.SessionManager
import java.io.Serializable

fun HttpServerExchange.getSession(): Session {
  val sessionManager = getAttachment(SessionManager.ATTACHMENT_KEY)
  val sessionConfig = getAttachment(SessionConfig.ATTACHMENT_KEY)
  var session: Session? = sessionManager.getSession(this, sessionConfig)

  if (session == null) {
    session = sessionManager.createSession(this, sessionConfig)
  }

  return session
}

fun <S : Serializable> HttpServerExchange.getPartialsSession(): S? {
  val session = getSession()
  return session.getAttribute("partials-session") as S?
}

fun <S : Serializable> HttpServerExchange.setPartialsSession(session: S) {
  val httpSession = getSession()
  httpSession.setAttribute("partials-session", session)
}
