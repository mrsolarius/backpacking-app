package fr.louisvolat.lifecycle

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.Display

class AppStateManagerImpl(private val context: Context) : AppStateManager {
    private val listeners = mutableListOf<AppStateManager.AppStateListener>()
    private var isInForeground = false
    private var isScreenOn = true

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    notifyListeners()
                }
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOn = true
                    notifyListeners()
                }
            }
        }
    }

    private val activityLifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
        private var numStarted = 0

        override fun onActivityStarted(activity: Activity) {
            numStarted++
            if (numStarted == 1) {
                isInForeground = true
                notifyListeners()
            }
        }

        override fun onActivityStopped(activity: Activity) {
            numStarted--
            if (numStarted == 0) {
                isInForeground = false
                notifyListeners()
            }
        }

        // Implémentation vide des autres méthodes du callback
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
        override fun onActivityResumed(activity: Activity) {}
        override fun onActivityPaused(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}
    }

    override fun startMonitoring() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        context.registerReceiver(screenStateReceiver, filter)

        val app = context.applicationContext as Application
        app.registerActivityLifecycleCallbacks(activityLifecycleCallbacks)

        // Vérifier l'état initial de l'écran
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        isScreenOn = displayManager.displays.any { it.state == Display.STATE_ON }
    }

    override fun stopMonitoring() {
        context.unregisterReceiver(screenStateReceiver)
        val app = context.applicationContext as Application
        app.unregisterActivityLifecycleCallbacks(activityLifecycleCallbacks)
    }

    override fun isAppInForeground(): Boolean = isInForeground

    override fun isScreenOn(): Boolean = isScreenOn

    override fun registerAppStateListener(listener: AppStateManager.AppStateListener) {
        listeners.add(listener)
    }

    override fun unregisterAppStateListener(listener: AppStateManager.AppStateListener) {
        listeners.remove(listener)
    }

    private fun notifyListeners() {
        listeners.forEach { it.onAppStateChanged(isInForeground, isScreenOn) }
    }
}