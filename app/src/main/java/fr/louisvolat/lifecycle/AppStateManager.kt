package fr.louisvolat.lifecycle

interface AppStateManager {
    fun startMonitoring()
    fun stopMonitoring()
    fun isAppInForeground(): Boolean
    fun isScreenOn(): Boolean
    fun registerAppStateListener(listener: AppStateListener)
    fun unregisterAppStateListener(listener: AppStateListener)

    interface AppStateListener {
        fun onAppStateChanged(isInForeground: Boolean, isScreenOn: Boolean)
    }
}