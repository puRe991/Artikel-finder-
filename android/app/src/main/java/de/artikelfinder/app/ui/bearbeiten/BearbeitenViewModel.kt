package de.artikelfinder.app.ui.bearbeiten

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.artikelfinder.app.data.Abruf
import de.artikelfinder.app.data.ArtikelRepository
import de.artikelfinder.app.data.Kategorie
import de.artikelfinder.app.data.remote.ArtikelAendernDto
import de.artikelfinder.app.data.remote.ArtikelAnlegenDto
import de.artikelfinder.app.data.remote.PreisErfassenDto
import de.artikelfinder.app.data.remote.StandortErfassenDto
import de.artikelfinder.app.ui.navigation.Ziele
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BearbeitenZustand(
    val name: String = "",
    val marke: String = "",
    val ean: String = "",
    val artikelnummer: String = "",
    val kategorieId: Int? = null,
    val preis: String = "",
    val werbepreis: String = "",
    val gang: String = "",
    val regalBeschreibung: String = "",
    val erfasstVon: String = "",
    val kategorien: List<Kategorie> = emptyList(),
    val istNeuanlage: Boolean = true,
    val laedt: Boolean = false,
    val speichert: Boolean = false,
    val fehler: String? = null,
    /** Gesetzt, sobald gespeichert wurde — der Bildschirm navigiert dann weiter. */
    val gespeicherteArtikelId: String? = null,
) {
    val kannSpeichern: Boolean get() = name.isNotBlank() && !speichert
}

@HiltViewModel
class BearbeitenViewModel @Inject constructor(
    private val repository: ArtikelRepository,
    zustandHalter: SavedStateHandle,
) : ViewModel() {

    private val artikelId: String? = zustandHalter.get<String>(Ziele.ARG_ARTIKEL_ID)?.takeIf { it.isNotBlank() }
    private val vorbelegteEan: String? = zustandHalter.get<String>(Ziele.ARG_EAN)?.takeIf { it.isNotBlank() }

    private val _zustand = MutableStateFlow(
        BearbeitenZustand(ean = vorbelegteEan.orEmpty(), istNeuanlage = artikelId == null)
    )
    val zustand: StateFlow<BearbeitenZustand> = _zustand.asStateFlow()

    init {
        viewModelScope.launch {
            (repository.kategorien() as? Abruf.Erfolg)?.let {
                aendern { copy(kategorien = it.wert) }
            }
        }

        artikelId?.let { laden(it) }
    }

    private fun laden(id: String) {
        viewModelScope.launch {
            aendern { copy(laedt = true) }

            when (val ergebnis = repository.holen(id)) {
                is Abruf.Erfolg -> {
                    val artikel = ergebnis.wert.artikel
                    aendern {
                        copy(
                            name = artikel.name,
                            marke = artikel.marke.orEmpty(),
                            ean = artikel.ean.orEmpty(),
                            artikelnummer = artikel.artikelnummer.orEmpty(),
                            kategorieId = artikel.kategorieId,
                            laedt = false,
                        )
                    }
                }
                is Abruf.Fehler -> aendern { copy(laedt = false, fehler = ergebnis.meldung) }
            }
        }
    }

    fun nameGeaendert(wert: String) = aendern { copy(name = wert, fehler = null) }
    fun markeGeaendert(wert: String) = aendern { copy(marke = wert) }
    fun eanGeaendert(wert: String) = aendern { copy(ean = wert.filter(Char::isDigit), fehler = null) }
    fun artikelnummerGeaendert(wert: String) = aendern { copy(artikelnummer = wert) }
    fun kategorieGewaehlt(id: Int?) = aendern { copy(kategorieId = id) }
    fun preisGeaendert(wert: String) = aendern { copy(preis = wert) }
    fun werbepreisGeaendert(wert: String) = aendern { copy(werbepreis = wert) }
    fun gangGeaendert(wert: String) = aendern { copy(gang = wert) }
    fun regalGeaendert(wert: String) = aendern { copy(regalBeschreibung = wert) }
    fun erfasstVonGeaendert(wert: String) = aendern { copy(erfasstVon = wert) }

    fun speichern() {
        val aktuell = _zustand.value
        if (!aktuell.kannSpeichern) return

        viewModelScope.launch {
            aendern { copy(speichert = true, fehler = null) }

            val ergebnis = if (artikelId == null) {
                repository.anlegen(
                    ArtikelAnlegenDto(
                        name = aktuell.name.trim(),
                        marke = aktuell.marke.leerAlsNull(),
                        ean = aktuell.ean.leerAlsNull(),
                        artikelnummer = aktuell.artikelnummer.leerAlsNull(),
                        kategorieId = aktuell.kategorieId,
                        // Preis und Standort gleich mitschicken: beim Anlegen im Markt
                        // stehen beide Angaben ohnehin gerade vor einem.
                        preis = aktuell.preis.alsBetrag()?.let {
                            PreisErfassenDto(
                                preis = it,
                                werbepreis = aktuell.werbepreis.alsBetrag(),
                                erfasstVon = aktuell.erfasstVon.leerAlsNull(),
                            )
                        },
                        standort = aktuell.gang.leerAlsNull()?.let {
                            StandortErfassenDto(
                                gang = it,
                                regalBeschreibung = aktuell.regalBeschreibung.leerAlsNull(),
                                erfasstVon = aktuell.erfasstVon.leerAlsNull(),
                            )
                        },
                    )
                )
            } else {
                repository.aendern(
                    artikelId,
                    ArtikelAendernDto(
                        name = aktuell.name.trim(),
                        marke = aktuell.marke.leerAlsNull(),
                        ean = aktuell.ean.leerAlsNull(),
                        artikelnummer = aktuell.artikelnummer.leerAlsNull(),
                        kategorieId = aktuell.kategorieId,
                    ),
                    geaendertVon = aktuell.erfasstVon.leerAlsNull(),
                )
            }

            when (ergebnis) {
                is Abruf.Erfolg -> aendern {
                    copy(speichert = false, gespeicherteArtikelId = ergebnis.wert.artikel.id)
                }
                is Abruf.Fehler -> aendern { copy(speichert = false, fehler = ergebnis.meldung) }
            }
        }
    }

    private fun aendern(block: BearbeitenZustand.() -> BearbeitenZustand) {
        _zustand.value = _zustand.value.block()
    }
}

private fun String.leerAlsNull(): String? = trim().takeIf { it.isNotEmpty() }

private fun String.alsBetrag(): Double? =
    trim().replace(',', '.').takeIf { it.isNotBlank() }?.toDoubleOrNull()?.takeIf { it > 0 }
