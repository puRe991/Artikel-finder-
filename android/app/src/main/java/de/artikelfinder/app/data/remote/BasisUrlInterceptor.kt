package de.artikelfinder.app.data.remote

import de.artikelfinder.app.data.einstellungen.Servereinstellungen
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ersetzt Schema, Host und Port jeder Anfrage durch die eingestellte Serveradresse.
 *
 * Retrofit braucht beim Erzeugen eine feste Basis-URL, die Adresse steht aber erst zur
 * Laufzeit fest. Statt Retrofit bei jeder Änderung neu zu bauen, bleibt dort ein
 * Platzhalter stehen, den dieser Interceptor überschreibt.
 */
@Singleton
class BasisUrlInterceptor @Inject constructor(
    private val einstellungen: Servereinstellungen,
) : Interceptor {

    override fun intercept(kette: Interceptor.Chain): Response {
        // OkHttp-Interceptor sind blockierend; der Aufruf läuft ohnehin auf einem
        // Hintergrundthread, und DataStore liefert den zwischengespeicherten Wert sofort.
        val eingestellt = runBlocking { einstellungen.aktuelleBasisUrl() }
            ?: throw IOException("Es ist keine Serveradresse eingestellt.")

        val basis = eingestellt.toHttpUrlOrNull()
            ?: throw IOException("Die eingestellte Serveradresse ist ungültig: $eingestellt")

        val anfrage = kette.request()
        val ziel = anfrage.url.newBuilder()
            .scheme(basis.scheme)
            .host(basis.host)
            .port(basis.port)
            .build()

        return kette.proceed(anfrage.newBuilder().url(ziel).build())
    }
}
