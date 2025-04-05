package fr.louisvolat.utils

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface

object ImageUtils {
    /**
     * Vérifie si l'image référencée par le [uri] contient des coordonnées GPS.
     *
     * @param context Le contexte Android requis pour accéder au ContentResolver.
     * @param uri L'URI de l'image à analyser.
     * @return `true` si des coordonnées GPS sont présentes, sinon `false`.
     */
    fun hasGpsCoordinates(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val exif = ExifInterface(inputStream)
                // La méthode getLatLong remplit le tableau si les coordonnées existent et retourne true.
                val latLong = exif.latLong
                latLong != null && latLong.isNotEmpty()
            } ?: false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
