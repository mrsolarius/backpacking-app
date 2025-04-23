package fr.louisvolat.locations

import java.time.Duration

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
    // Durée totale de tracking en millisecondes (incluant le temps de pause)
    fun getTotalDurationMillis(): Long {
        val now = System.currentTimeMillis()
        if (!isTracking) return 0

        // Si en pause, utiliser le moment de mise en pause comme référence
        val endReferenceTime = if (isPaused) pausedAtMillis else now

        // Durée = temps écoulé - temps total en pause
        return endReferenceTime - startTimeMillis - totalPausedDurationMillis
    }

    // Obtenir la durée formatée (HH:MM:SS)
    fun getFormattedDuration(): String {
        val durationMillis = getTotalDurationMillis()
        val duration = Duration.ofMillis(durationMillis)

        val hours = duration.toHours()
        val minutes = duration.toMinutesPart()
        val seconds = duration.toSecondsPart()

        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }
}

// Actions pour le tracking
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
}