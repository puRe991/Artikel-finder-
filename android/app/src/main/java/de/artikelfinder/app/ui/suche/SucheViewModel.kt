package de.artikelfinder.app.ui.suche

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.artikelfinder.app.data.Abruf
import de.artikelfinder.app.data.Artikel
import de.artikelfinder.app.data.ArtikelRepository
import de.artikelfinder.app.data.Kategorie
import de.artikelfinder.app.data.Tagesaufgabe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SucheZustand(
    val suchbegriff: String = "",
    val treffer: List<Artikel> = emptyList(),
    val zuletztBearbeitet: List<Artikel> = emptyList(),
    val kategorien: List<Kategorie> = emptyList(),
    val gewaehlteKategorieId: Int? = null,
    val nurMitWerbepreis: Boolean = false,
    /** Der Artikel, den die App heute zum Nachprüfen vorschlägt. */
    val tagesaufgabe: Tagesaufgabe? = null,
    val laedt: Boolean = false,
    val fehler: String? = null,
) {
    /** Ohne Filter und ohne Suchbegriff zeigt der Bildschirm die zuletzt gesehenen Artikel. */
    val zeigtVerlauf: Boolean
        get() = suchbegriff.isBlank() && gewaehlteKategorieId == null && !nurMitWerbepreis
}

/** Alles außer dem Suchtext — das wird angetippt, nicht getippt, und wirkt deshalb sofort. */
private data class Suchfilter(
    val kategorieId: Int? = null,
    val nurMitWerbepreis: Boolean = false,
    /** Hochzählen erzwingt eine erneute Abfrage bei unveränderter Eingabe. */
    val versuch: Int = 0,
)

/**
 * Auftrag an die Suche. Bewusst getrennt vom Anzeigezustand: dieser ändert sich mit jedem
 * Treffer, der Auftrag nur, wenn der Nutzer etwas eingibt — und nur dann ist neu zu suchen.
 */
private data class Suchauftrag(
    val suchbegriff: String,
    val filter: Suchfilter,
) {
    val zeigtVerlauf: Boolean
        get() = suchbegriff.isBlank() && filter.kategorieId == null && !filter.nurMitWerbepreis
}

/** Eine Zwischenmeldung der Trefferabfrage. `treffer == null` heißt "Liste unverändert". */
private data class Trefferstand(
    val treffer: List<Artikel>?,
    val laedt: Boolean = false,
    val fehler: String? = null,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SucheViewModel @Inject constructor(
    private val repository: ArtikelRepository,
) : ViewModel() {

    private val _zustand = MutableStateFlow(SucheZustand())
    val zustand: StateFlow<SucheZustand> = _zustand.asStateFlow()

    private val eingabe = MutableStateFlow("")
    private val filter = MutableStateFlow(Suchfilter())

    init {
        combine(
            // Erst tippen lassen, dann suchen — sonst löst jeder Buchstabe eine Abfrage aus.
            eingabe.debounce(300),
            filter,
        ) { begriff, gewaehlt -> Suchauftrag(begriff, gewaehlt) }
            .distinctUntilChanged()
            .flatMapLatest { trefferstrom(it) }
            .onEach { stand ->
                _zustand.value = _zustand.value.copy(
                    // Die alte Liste stehen lassen, solange die neue lädt — sonst blitzt
                    // zwischendurch „Keine Treffer“ auf.
                    treffer = stand.treffer ?: _zustand.value.treffer,
                    laedt = stand.laedt,
                    fehler = stand.fehler,
                )
            }
            .launchIn(viewModelScope)

        repository.zuletztBearbeitet()
            .onEach { liste -> _zustand.value = _zustand.value.copy(zuletztBearbeitet = liste) }
            .launchIn(viewModelScope)

        repository.tagesaufgabe()
            .onEach { aufgabe -> _zustand.value = _zustand.value.copy(tagesaufgabe = aufgabe) }
            // Ohne Tagesaufgabe bleibt die Karte einfach weg — die Suche funktioniert weiter.
            .catch { _zustand.value = _zustand.value.copy(tagesaufgabe = null) }
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
        filter.value = filter.value.copy(kategorieId = kategorieId)
    }

    fun werbepreisFilterUmschalten() {
        val nurAngebote = !_zustand.value.nurMitWerbepreis
        _zustand.value = _zustand.value.copy(nurMitWerbepreis = nurAngebote)
        filter.value = filter.value.copy(nurMitWerbepreis = nurAngebote)
    }

    fun aktualisieren() {
        filter.value = filter.value.copy(versuch = filter.value.versuch + 1)
    }

    fun tagesaufgabeErledigt() {
        viewModelScope.launch { repository.tagesaufgabeErledigen() }
    }

    private fun trefferstrom(auftrag: Suchauftrag): Flow<Trefferstand> {
        // Ohne Suchbegriff und Filter zeigt der Bildschirm die zuletzt bearbeiteten Artikel;
        // die kommen aus einem eigenen Flow und brauchen keine Trefferliste.
        if (auftrag.zeigtVerlauf) return flowOf(Trefferstand(treffer = emptyList()))

        return repository
            .suchenLive(
                suchbegriff = auftrag.suchbegriff,
                kategorieId = auftrag.filter.kategorieId,
                nurMitWerbepreis = auftrag.filter.nurMitWerbepreis,
            )
            .map { Trefferstand(treffer = it) }
            .onStart { emit(Trefferstand(treffer = null, laedt = true)) }
            .catch { fehler ->
                emit(
                    Trefferstand(
                        treffer = emptyList(),
                        fehler = fehler.message ?: "Die Suche ist fehlgeschlagen.",
                    )
                )
            }
    }
}
