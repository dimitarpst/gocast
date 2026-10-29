package app.gocast.data

import android.content.Context
import app.gocast.domain.TrustedPc
import app.gocast.protocol.RoomId
import org.json.JSONObject

class TrustedPcStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): TrustedPc? {
        val json = prefs.getString(KEY, null) ?: return null
        return runCatching {
            val obj = JSONObject(json)
            TrustedPc(
                displayName = obj.getString("displayName"),
                host = obj.getString("host"),
                port = obj.getInt("port"),
                instanceId = obj.getString("instanceId"),
                trustToken = obj.getString("trustToken"),
                // Saved before remote casting existed: no room, trust token still valid.
                roomId = obj.optString("roomId").takeIf { RoomId.isValid(it) },
            )
        }.getOrNull()
    }

    fun save(pc: TrustedPc) {
        val obj = JSONObject()
            .put("displayName", pc.displayName)
            .put("host", pc.host)
            .put("port", pc.port)
            .put("instanceId", pc.instanceId)
            .put("trustToken", pc.trustToken)
        pc.roomId?.let { obj.put("roomId", it) }
        prefs.edit().putString(KEY, obj.toString()).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    companion object {
        private const val PREFS = "gocast_trusted_pc"
        private const val KEY = "pc"
    }
}
