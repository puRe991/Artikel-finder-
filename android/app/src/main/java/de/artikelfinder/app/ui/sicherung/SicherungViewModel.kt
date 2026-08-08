package de.artikelfinder.app.ui.sicherung

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import de.artikelfinder.app.data.Abruf
import de.artikelfinder.app.data.sicherung.Sicherungsbericht
import de.artikelfinder.app.data.sicherung.Sicherungsdienst
import de.artikelfinder.app.data.sicherung.Sicherungsformat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject

data class SicherungZustand(
    val laeuft: Boolean = false,
    val erfolg: String? = null,
    val fehler: String? = null,
)

@HiltViewModel
class SicherungViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dienst: Sicherungsdienst,
) : ViewModel() {

    private val _zustand = MutableStateFlow(SicherungZustand())
    val zustand: StateFlow<SicherungZustand> = _zustand.asStateFlow()

    /** Vorschlag für den Dateinamen — das Datum macht mehrere Sicherungen unterscheidbar. */
    fun dateiname(): String = "artikel-finder-sicherung-${LocalDate.now()}.tsv"

    fun exportieren(ziel: Uri) = arbeiten {
        val sicherung = dienst.erstellen()

        context.contentResolver.openOutputStream(ziel, "wt")?.use { strom ->
            strom.writer(Charsets.UTF_8).use { it.write(Sicherungsformat.schreiben(sicherung)) }
        } ?: return@arbeiten Abruf.Fehler("Die Datei ließ sich nicht zum Schreiben öffnen.")

        val anzahl = sicherung.preise.size + sicherung.standorte.size + sicherung.artikel.size

        Abruf.Erfolg(
            if (sicherung.istLeer) {
                "Die Sicherung ist geschrieben — sie ist noch leer, weil bisher nichts " +
                    "erfasst wurde."
            } else {
                "$anzahl Einträge gesichert: ${sicherung.preise.size} Preise, " +
                    "${sicherung.standorte.size} Standorte, " +
                    "${sicherung.artikel.size} selbst angelegte Artikel."
            }
        )
    }

    fun importieren(quelle: Uri) = arbeiten {
        val inhalt = context.contentResolver.openInputStream(quelle)?.use {
            it.reader(Charsets.UTF_8).readText()
        } ?: return@arbeiten Abruf.Fehler("Die Datei ließ sich nicht öffnen.")

        when (val gelesen = Sicherungsformat.lesen(inhalt)) {
            is Abruf.Fehler -> Abruf.Fehler(gelesen.meldung)
            is Abruf.Erfolg -> Abruf.Erfolg(dienst.einspielen(gelesen.wert).alsMeldung())
        }
    }

    fun meldungGelesen() {
        _zustand.value = SicherungZustand()
    }

    private fun arbeiten(auftrag: suspend () -> Abruf<String>) {
        if (_zustand.value.laeuft) return
        _zustand.value = SicherungZustand(laeuft = true)

        viewModelScope.launch {
            val ergebnis = try {
                withContext(Dispatchers.IO) { auftrag() }
            } catch (fehler: Exception) {
                // Ein abgezogener USB-Speicher oder ein zurückgezogenes Recht auf die Datei
                // darf die App nicht mitreißen.
                Abruf.Fehler(fehler.message ?: "Die Datei konnte nicht verarbeitet werden.")
            }

            _zustand.value = when (ergebnis) {
                is Abruf.Erfolg -> SicherungZustand(erfolg = ergebnis.wert)
                is Abruf.Fehler -> SicherungZustand(fehler = ergebnis.meldung)
            }
        }
    }
}

private fun Sicherungsbericht.alsMeldung(): String {
    if (nichtsGeaendert && ohneArtikel == 0) {
        return "Alles aus dieser Datei war schon vorhanden — es hat sich nichts geändert."
    }

    val teile = buildList {
        if (neuePreise > 0) add("$neuePreise Preise")
        if (neueStandorte > 0) add("$neueStandorte Standorte")
        if (neueArtikel > 0) add("$neueArtikel Artikel")
    }

    return buildString {
        if (teile.isEmpty()) {
            append("Es wurde nichts übernommen.")
        } else {
            append("Übernommen: ${teile.joinToString(", ")}.")
        }
        if (bereitsVorhanden > 0) append(" $bereitsVorhanden Einträge waren schon da.")
        if (ohneArtikel > 0) {
            append(
                " $ohneArtikel Erfassungen ließen sich keinem Artikel dieses Katalogs " +
                    "zuordnen und wurden übersprungen."
            )
        }
    }
}
