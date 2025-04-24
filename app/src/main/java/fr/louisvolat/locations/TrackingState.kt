package fr.louisvolat.locations

import kotlin.math.max

// État du tracking
data class TrackingState(
    val isTracking: Boolean = false,
    val isPaused: Boolean = false,
    val currentTravelId: Long? = null,
    val startTimeMillis: Long = 0,
    val pausedAtMillis: Long = 0,
    val totalPausedDurationMillis: Long = 0,
    val lastLocationTime: Long = 0,
    val lastUpdateTime: Long = System.currentTimeMillis()
) {
    /**
     * Calcule la durée totale du tracking en millisecondes
     * Cette méthode garantit que la durée ne sera jamais négative
     */
    fun getTotalDurationMillis(): Long {
        if (!isTracking) return 0

        val currentTime = System.currentTimeMillis()
        val endTime = if (isPaused) pausedAtMillis else currentTime

        // Garantit que l'heure de fin est toujours après ou égale à l'heure de début
        val safeEndTime = max(endTime, startTimeMillis)
        val safeTotalPaused = max(0L, totalPausedDurationMillis)

        // Garantit une durée non négative
        return max(0L, safeEndTime - startTimeMillis - safeTotalPaused)
    }

    /**
     * Retourne la durée du tracking formatée (HH:MM:SS)
     */
    fun getFormattedDuration(): String {
        val durationMillis = getTotalDurationMillis()

        val hours = durationMillis / (1000 * 60 * 60)
        val minutes = (durationMillis % (1000 * 60 * 60)) / (1000 * 60)
        val seconds = (durationMillis % (1000 * 60)) / 1000

        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }
}

/**
 * Actions possibles pour le tracking
 */
sealed class TrackingAction {
    data class Start(val travelId: Long) : TrackingAction()
    data class UpdateState(val state: TrackingState) : TrackingAction()
    object Stop : TrackingAction()
    object Pause : TrackingAction()
    object Resume : TrackingAction()
    data class UpdateLocation(
        val latitude: Double,
        val longitude: Double,
        val altitude: Double,
        val time: Long
    ) : TrackingAction()
    object TimerTick : TrackingAction()
}