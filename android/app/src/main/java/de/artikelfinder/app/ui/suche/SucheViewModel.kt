package de.artikelfinder.app.ui.suche

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.artikelfinder.app.data.Abruf
import de.artikelfinder.app.data.Artikel
import de.artikelfinder.app.data.ArtikelRepository
import de.artikelfinder.app.data.Kategorie
import de.artikelfinder.app.data.local.MarktEintrag
import de.artikelfinder.app.data.markt.Ketten
import de.artikelfinder.app.data.markt.Marktverwaltung
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SucheZustand(
    val suchbegriff: String = "",
    val treffer: List<Artikel> = emptyList(),
    val zuletztBearbeitet: List<Artikel> = emptyList(),
    val kategorien: List<Kategorie> = emptyList(),
    val gewaehlteKategorieId: Int? = null,
    val nurMitWerbepreis: Boolean = false,
    val fremdeEigenmarken: Boolean = false,
    val markt: MarktEintrag? = null,
    val laedt: Boolean = false,
    val fehler: String? = null,
) {
    /**
     * Ohne bekannte Kette gibt es nichts auszublenden — dann waere der Schalter nur ein
     * Knopf ohne Wirkung.
     */
    val zeigtEigenmarkenfilter: Boolean
        get() = markt?.kette?.let { it != Ketten.SONSTIGE } == true

    val marktname: String
        get() = markt?.name ?: "Artikel-Finder"
    /** Ohne Filter und ohne Suchbegriff zeigt der Bildschirm die zuletzt gesehenen Artikel. */
    val zeigtVerlauf: Boolean
        get() = suchbegriff.isBlank() && gewaehlteKategorieId == null && !nurMitWerbepreis
}

@OptIn(FlowPreview::class)
@HiltViewModel
class SucheViewModel @Inject constructor(
    private val repository: ArtikelRepository,
    private val maerkte: Marktverwaltung,
) : ViewModel() {

    private val _zustand = MutableStateFlow(SucheZustand())
    val zustand: StateFlow<SucheZustand> = _zustand.asStateFlow()

    private val eingabe = MutableStateFlow("")
    private var suchauftrag: Job? = null

    init {
        // Erst tippen lassen, dann suchen — sonst löst jeder Buchstabe einen Request aus.
        eingabe
            .debounce(300)
            .distinctUntilChanged()
            .onEach { suchen() }
            .launchIn(viewModelScope)

        repository.zuletztBearbeitet()
            .onEach { liste -> _zustand.value = _zustand.value.copy(zuletztBearbeitet = liste) }
            .launchIn(viewModelScope)

        // Ein Marktwechsel aendert Preise, Gaenge und das Sortiment — die Trefferliste
        // muss danach neu gezogen werden.
        maerkte.aktuell
            .onEach { markt ->
                _zustand.value = _zustand.value.copy(markt = markt)
                suchen()
            }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            when (val ergebnis = repository.kategorien()) {
                is Abruf.Erfolg -> _zustand.value = _zustand.value.copy(kategorien = ergebnis.wert)
                // Ohne Kategorien ist die App nutzbar, nur der Filter fehlt — kein Fehler nötig.
                is Abruf.Fehler -> Unit
            }
        }
    }

    fun suchbegriffGeaendert(wert: String) {
        _zustand.value = _zustand.value.copy(suchbegriff = wert)
        eingabe.value = wert
    }

    fun kategorieGewaehlt(kategorieId: Int?) {
        _zustand.value = _zustand.value.copy(gewaehlteKategorieId = kategorieId)
        suchen()
    }

    fun werbepreisFilterUmschalten() {
        _zustand.value = _zustand.value.copy(nurMitWerbepreis = !_zustand.value.nurMitWerbepreis)
        suchen()
    }

    fun fremdeEigenmarkenUmschalten() {
        _zustand.value = _zustand.value.copy(fremdeEigenmarken = !_zustand.value.fremdeEigenmarken)
        suchen()
    }

    fun aktualisieren() = suchen()

    private fun suchen() {
        val aktuell = _zustand.value

        if (aktuell.zeigtVerlauf) {
            suchauftrag?.cancel()
            _zustand.value = aktuell.copy(treffer = emptyList(), laedt = false, fehler = null)
            return
        }

        // Ein laufender Request zu einem älteren Suchbegriff darf das Ergebnis nicht mehr
        // überschreiben.
        suchauftrag?.cancel()
        suchauftrag = viewModelScope.launch {
            _zustand.value = _zustand.value.copy(laedt = true, fehler = null)

            val ergebnis = repository.suchen(
                suchbegriff = aktuell.suchbegriff,
                kategorieId = aktuell.gewaehlteKategorieId,
                nurMitWerbepreis = aktuell.nurMitWerbepreis,
                fremdeEigenmarken = aktuell.fremdeEigenmarken,
            )

            _zustand.value = when (ergebnis) {
                is Abruf.Erfolg -> _zustand.value.copy(
                    treffer = ergebnis.wert,
                    laedt = false,
                    fehler = null,
                )
                is Abruf.Fehler -> _zustand.value.copy(
                    treffer = emptyList(),
                    laedt = false,
                    fehler = ergebnis.meldung,
                )
            }
        }
    }
}
