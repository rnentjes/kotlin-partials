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

val PARTIALS_SESSION_ID = "partials-session"

fun <S : Serializable> HttpServerExchange.getPartialsSession(): S? {
  return getSession().getPartialsSession()
}

fun <S : Serializable> HttpServerExchange.setPartialsSession(session: S) {
  getSession().setPartialsSession(session)
}

fun <S : Serializable> Session.getPartialsSession(): S? {
  return this.getAttribute(PARTIALS_SESSION_ID) as S?
}

fun <S : Serializable> Session.setPartialsSession(session: S) {
  this.setAttribute(PARTIALS_SESSION_ID, session)
}
