package de.artikelfinder.app.data.einstellungen

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.datenspeicher: DataStore<Preferences> by preferencesDataStore("einstellungen")

/**
 * Adresse der API, vom Nutzer eingestellt.
 *
 * Die Adresse gehört nicht in den Build: dieselbe APK läuft bei dir im WLAN gegen
 * 192.168.x.y, im Emulator gegen 10.0.2.2 und später gegen einen echten Server. Wäre sie
 * einkompiliert, bräuchte jeder Netzwechsel einen neuen Build.
 */
@Singleton
class Servereinstellungen @Inject constructor(@ApplicationContext private val context: Context) {

    private val schluessel = stringPreferencesKey("api_basis_url")

    val basisUrl: Flow<String?> = context.datenspeicher.data.map { it[schluessel] }

    suspend fun aktuelleBasisUrl(): String? = basisUrl.first()

    suspend fun setzen(url: String) {
        context.datenspeicher.edit { it[schluessel] = Adresse.normalisieren(url) }
    }
}

object Adresse {
    /**
     * Macht aus einer Eingabe wie "192.168.178.20", "192.168.178.20:5080" oder
     * "http://192.168.178.20:5080" eine vollständige Basis-URL mit Schrägstrich am Ende.
     * Retrofit verlangt den Schrägstrich, sonst wirft es beim Start eine Ausnahme.
     */
    fun normalisieren(eingabe: String): String {
        var wert = eingabe.trim()
        if (wert.isEmpty()) return wert

        if (!wert.startsWith("http://") && !wert.startsWith("https://")) {
            wert = "http://$wert"
        }

        // Ohne Portangabe der Standardport der Entwicklungs-API.
        val ohneSchema = wert.substringAfter("://")
        val hatPort = ohneSchema.substringBefore('/').contains(':')
        if (!hatPort) {
            val (host, rest) = ohneSchema.substringBefore('/') to ohneSchema.substringAfter('/', "")
            wert = wert.substringBefore("://") + "://" + host + ":5080" + if (rest.isEmpty()) "" else "/$rest"
        }

        return if (wert.endsWith('/')) wert else "$wert/"
    }

    /** Grobe Plausibilitätsprüfung für die Eingabemaske. */
    fun siehtGueltigAus(eingabe: String): Boolean = runCatching {
        val url = java.net.URI(normalisieren(eingabe))
        !url.host.isNullOrBlank() && url.port > 0
    }.getOrDefault(false)
}
