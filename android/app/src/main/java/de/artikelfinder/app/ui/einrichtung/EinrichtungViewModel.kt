package de.artikelfinder.app.ui.einrichtung

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.artikelfinder.app.data.einstellungen.Adresse
import de.artikelfinder.app.data.einstellungen.Servereinstellungen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject

data class EinrichtungZustand(
    val eingabe: String = "",
    val prueft: Boolean = false,
    val meldung: String? = null,
    val erfolgreich: Boolean = false,
    val gespeichert: Boolean = false,
) {
    val kannPruefen: Boolean get() = !prueft && Adresse.siehtGueltigAus(eingabe)
}

@HiltViewModel
class EinrichtungViewModel @Inject constructor(
    private val einstellungen: Servereinstellungen,
    private val http: OkHttpClient,
) : ViewModel() {

    private val _zustand = MutableStateFlow(EinrichtungZustand())
    val zustand: StateFlow<EinrichtungZustand> = _zustand.asStateFlow()

    init {
        viewModelScope.launch {
            einstellungen.aktuelleBasisUrl()?.let {
                _zustand.value = _zustand.value.copy(eingabe = it)
            }
        }
    }

    fun eingabeGeaendert(wert: String) {
        _zustand.value = _zustand.value.copy(eingabe = wert, meldung = null, erfolgreich = false)
    }

    /**
     * Prüft die Adresse gegen /health, bevor sie gespeichert wird. Ohne diesen Schritt
     * merkt der Nutzer den Tippfehler erst später als „keine Verbindung" auf jedem
     * einzelnen Bildschirm.
     */
    fun pruefenUndSpeichern() {
        val aktuell = _zustand.value
        if (!aktuell.kannPruefen) return

        val url = Adresse.normalisieren(aktuell.eingabe)

        viewModelScope.launch {
            _zustand.value = aktuell.copy(prueft = true, meldung = null)

            val ergebnis = pruefen(url)

            _zustand.value = if (ergebnis == null) {
                einstellungen.setzen(url)
                _zustand.value.copy(
                    prueft = false,
                    erfolgreich = true,
                    gespeichert = true,
                    meldung = "Verbunden mit $url",
                )
            } else {
                _zustand.value.copy(prueft = false, erfolgreich = false, meldung = ergebnis)
            }
        }
    }

    /** Gibt `null` bei Erfolg zurück, sonst die Fehlermeldung für den Nutzer. */
    private suspend fun pruefen(basisUrl: String): String? = withContext(Dispatchers.IO) {
        // Bewusst ein eigener Aufruf statt über Retrofit: geprüft wird die eingetippte
        // Adresse, nicht die gespeicherte.
        val anfrage = Request.Builder().url("${basisUrl}health").get().build()

        try {
            http.newCall(anfrage).execute().use { antwort ->
                when {
                    antwort.isSuccessful -> null
                    else -> "Der Server antwortet, aber mit HTTP ${antwort.code}. " +
                        "Läuft dort wirklich die Artikel-Finder-API?"
                }
            }
        } catch (fehler: IOException) {
            "Keine Verbindung: ${fehler.message ?: "Zeitüberschreitung"}. " +
                "Prüfe, ob Handy und Rechner im selben WLAN sind und die API läuft."
        } catch (fehler: IllegalArgumentException) {
            "Die Adresse ist ungültig: ${fehler.message}"
        }
    }
}
