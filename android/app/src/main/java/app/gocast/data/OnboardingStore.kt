package app.gocast.data

import android.content.Context

object OnboardingStore {
    private const val PREFS = "gocast_onboarding"
    private const val KEY_DONE = "walkthrough_done"

    fun isDone(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_DONE, false)

    fun markDone(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DONE, true)
            .apply()
    }
}
