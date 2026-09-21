package de.artikelfinder.app.data

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Legt selbst gewählte Artikelbilder dauerhaft im App-Speicher ab.
 *
 * Der Fotopicker liefert nur einen kurzlebigen Lesezugriff auf das Originalbild; damit es die
 * App-Sitzung überlebt und offline verfügbar ist, wird es in den privaten Speicher kopiert.
 * Gespeichert wird als `file://`-URI — Coil und das Datenmodell behandeln es dann wie jede
 * andere Bildquelle.
 */
@Singleton
class Bildspeicher @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val ordner: File
        get() = File(context.filesDir, ORDNER).apply { mkdirs() }

    /** Kopiert die Bildquelle in den App-Speicher und gibt die `file://`-URI zurück. */
    suspend fun speichern(quelle: Uri, artikelId: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val ziel = File(ordner, "${artikelId}-${System.currentTimeMillis()}.jpg")
            val kopiert = context.contentResolver.openInputStream(quelle)?.use { ein ->
                ziel.outputStream().use { aus -> ein.copyTo(aus) }
                true
            } ?: false

            if (kopiert) Uri.fromFile(ziel).toString() else null
        }.getOrNull()
    }

    /** Entfernt ein zuvor selbst abgelegtes Bild. Fremde (http-)Quellen bleiben unberührt. */
    fun entfernenFallsLokal(bildUrl: String?) {
        if (bildUrl != null && bildUrl.startsWith("file://")) {
            runCatching { Uri.parse(bildUrl).path?.let { File(it).delete() } }
        }
    }

    private companion object {
        const val ORDNER = "artikelbilder"
    }
}
