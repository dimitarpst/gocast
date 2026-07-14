package app.gocast.domain

import app.gocast.domain.model.JoinSession

interface SessionFactory {
    fun create (port: Int = 8765): JoinSession
}