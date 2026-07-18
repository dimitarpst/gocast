package app.gocast.domain

import app.gocast.domain.model.JoinSession
import app.gocast.network.LocalIpResolver
import app.gocast.trust.DesktopIdentityStore
import java.util.UUID

class JvmSessionFactory(
    private val identityStore: DesktopIdentityStore = DesktopIdentityStore(),
) : SessionFactory {
    override fun create(port: Int): JoinSession {
        val identity = identityStore.loadOrCreateIdentity()
        return JoinSession(
            host = LocalIpResolver.resolve(),
            port = port,
            token = UUID.randomUUID().toString(),
            shortCode = ShortCodeGenerator.generate(),
            instanceId = identity.instanceId,
            displayName = identity.displayName,
        )
    }
}
