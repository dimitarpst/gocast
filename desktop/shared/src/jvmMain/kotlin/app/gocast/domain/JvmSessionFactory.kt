package app.gocast.domain

import app.gocast.domain.model.JoinSession
import app.gocast.network.LocalIpResolver
import java.util.UUID

class JvmSessionFactory : SessionFactory {
    override fun create(port: Int): JoinSession {
        return JoinSession(
            host = LocalIpResolver.resolve(),
            port = port,
            token = UUID.randomUUID().toString()
        )
    }
}