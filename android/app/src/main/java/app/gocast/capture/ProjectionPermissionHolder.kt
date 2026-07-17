package app.gocast.capture

import android.content.Intent

/**
 * Holds the MediaProjection consent Intent in-process.
 * Do not parcel it through Service start Intent extras — the Binder token
 * can be invalidated on some OEMs (including Xiaomi).
 */
object ProjectionPermissionHolder {
    @Volatile
    private var resultCode: Int = 0

    @Volatile
    private var data: Intent? = null

    fun set(resultCode: Int, data: Intent) {
        this.resultCode = resultCode
        this.data = data
    }

    fun take(): Intent? {
        val intent = data
        data = null
        resultCode = 0
        return intent
    }
}
