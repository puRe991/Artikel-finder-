package de.artikelfinder.app.data.markt

import de.artikelfinder.app.data.local.ArtikelDatenbank
import de.artikelfinder.app.data.local.MarktEintrag
import de.artikelfinder.app.data.local.MerkpostenEintrag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Welcher Markt gerade gilt.
 *
 * Preise und Gänge hängen seit dem ersten Entwurf am Markt, nicht am Artikel allein —
 * dieselbe Butter steht im Kaufland in Gang 3 und im Rewe in Gang 7, zu verschiedenen
 * Preisen. Die Wahl des Markts entscheidet deshalb, welche Erfassungen die App zeigt, und
 * sie steckt als Merkposten in der Datenbank statt in den Einstellungen: sie gehört zu den
 * Daten, nicht zur Oberfläche, und wandert damit auch in die Sicherung.
 */
@Singleton
class Marktverwaltung @Inject constructor(private val datenbank: ArtikelDatenbank) {

    private val _aktuell = MutableStateFlow<MarktEintrag?>(null)

    /** `null`, solange kein Markt gewählt ist — dann fragt die App beim Start danach. */
    val aktuell: StateFlow<MarktEintrag?> = _aktuell.asStateFlow()

    fun alle(): Flow<List<MarktEintrag>> = datenbank.stammdatenDao().maerkteStrom()

    /**
     * Stellt den zuletzt gewählten Markt wieder her. Ist er inzwischen gelöscht oder war
     * noch keiner gewählt, rückt der erste vorhandene nach — wer nur einen Markt hat, soll
     * nicht bei jedem Start gefragt werden.
     */
    suspend fun laden() {
        val stammdaten = datenbank.stammdatenDao()
        val gemerkt = datenbank.merkpostenDao().lesen(MERKPOSTEN)?.toIntOrNull()

        _aktuell.value = gemerkt?.let { stammdaten.markt(it) }
            ?: stammdaten.maerkte().firstOrNull()?.also { merken(it.id) }
    }

    suspend fun waehlen(marktId: Int) {
        val markt = datenbank.stammdatenDao().markt(marktId) ?: return
        merken(marktId)
        _aktuell.value = markt
    }

    /**
     * Legt einen Markt an und wählt ihn gleich aus. Ohne Ortsangabe heißt er wie die Kette;
     * mit Ort wird daraus „Kaufland Gießen", damit sich zwei Filialen unterscheiden lassen.
     */
    suspend fun anlegen(ketteSchluessel: String, ort: String? = null): MarktEintrag {
        val kette = Ketten.kette(ketteSchluessel)
        val anzeigename = kette?.name ?: ketteSchluessel
        val bereinigterOrt = ort?.trim()?.takeIf { it.isNotEmpty() }

        val id = datenbank.stammdatenDao().marktEinfuegen(
            MarktEintrag(
                name = listOfNotNull(anzeigename, bereinigterOrt).joinToString(" "),
                kette = ketteSchluessel,
                ort = bereinigterOrt,
            )
        ).toInt()

        waehlen(id)
        return checkNotNull(_aktuell.value)
    }

    suspend fun umbenennen(marktId: Int, name: String, ort: String?) {
        val markt = datenbank.stammdatenDao().markt(marktId) ?: return
        val geaendert = markt.copy(
            name = name.trim().ifEmpty { markt.name },
            ort = ort?.trim()?.takeIf { it.isNotEmpty() },
        )

        datenbank.stammdatenDao().marktAktualisieren(geaendert)
        if (_aktuell.value?.id == marktId) _aktuell.value = geaendert
    }

    /**
     * Entfernt einen Markt. Die dort erfassten Preise und Gänge bleiben liegen — sie hängen
     * an der Markt-Id und wären bei einem Versehen sonst unwiederbringlich weg. Wer den
     * Markt neu anlegt, bekommt eine neue Id und sieht sie nicht wieder; die Sicherung
     * enthält sie aber weiterhin.
     */
    suspend fun entfernen(marktId: Int) {
        datenbank.stammdatenDao().marktLoeschen(marktId)
        if (_aktuell.value?.id == marktId) {
            _aktuell.value = null
            laden()
        }
    }

    /** Die Id für Abfragen. `0` trifft keinen Markt und liefert damit leere Ergebnisse. */
    fun aktuelleId(): Int = _aktuell.value?.id ?: 0

    /** Die Kette des aktuellen Markts — Grundlage für den Eigenmarkenfilter. */
    fun aktuelleKette(): String? = _aktuell.value?.kette

    private suspend fun merken(marktId: Int) =
        datenbank.merkpostenDao().schreiben(MerkpostenEintrag(MERKPOSTEN, marktId.toString()))

    private companion object {
        const val MERKPOSTEN = "markt"
    }
}
