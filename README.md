# Artikel-Finder

Artikelsuche für den Kaufland Gießen: Name, EAN, Normalpreis, laufender Werbepreis und der
Gang, in dem der Artikel steht.

**Die App läuft eigenständig auf dem Handy.** Kein Server, kein Rechner, kein WLAN nötig.
Der Artikelkatalog — gut 19.000 reale Produkte aus
[Open Food Facts](https://world.openfoodfacts.org/data) und seinen Schwesterdatenbanken,
darunter 4.261 Kaufland-Eigenmarkenartikel (K-Classic, K-Bio, Purland, Bevola …) — liegt in
der App und wird beim ersten Start in die geräteeigene Datenbank geschrieben. Preise und
Standorte trägst du beim Einkaufen selbst ein; sie bleiben auf dem Gerät.

Eine Internetverbindung wird nur für die Produktbilder verwendet. Suche, Barcode-Scan,
Preis- und Standorterfassung funktionieren vollständig offline.

## Projektstruktur

```
android/                              Die App — Kotlin, Jetpack Compose, Room
  app/schemas/                        Die exportierten Datenbankschemata, je Version eins
  app/src/main/assets/                Der ausgelieferte Artikelkatalog
  app/src/main/java/de/artikelfinder/app/
    data/                             Room-Datenbank, Repository, Katalogaufbau
    data/sicherung/                   Sicherungsdatei der eigenen Erfassungen
    ui/suche | detail | bearbeiten | scan | gaenge | verlauf | angebote | sicherung
  app/src/test/                       49 Tests gegen echtes SQLite (Robolectric)

backend/                              Werkzeug, nicht zur Laufzeit nötig
  src/ArtikelFinder.Import/           Erzeugt den Katalog aus Open Food Facts
  src/ArtikelFinder.Api/              Datenmodell und Web-API für Phase 3 (Mehrbenutzer)
  daten/katalog-seed.tsv.gz           Quelle des Katalogs in der App
  tests/                              79 Tests

.github/workflows/ci.yml              Baut die App und fährt beide Testsätze bei jedem Push
```

Das Backend wird für den Betrieb der App **nicht** gebraucht. Es bleibt im Projekt, weil es
den Katalog erzeugt und den Weg zum Mehrbenutzerbetrieb (Phase 3) offenhält.

## App bauen

```bash
cd android
echo "sdk.dir=$ANDROID_HOME" > local.properties

./gradlew :app:assembleDebug   # zum Entwickeln (~26 MB)
./gradlew :app:assembleDist    # zum Weitergeben, verkleinert (~9 MB)
./gradlew :app:testDebugUnitTest   # 49 Tests
```

Beide Varianten erzeugen je ein APK pro Prozessorarchitektur unter
`app/build/outputs/apk/`. `arm64-v8a` passt auf praktisch jedes Handy der letzten Jahre.

Beim ersten Start baut die App den Katalog auf — das dauert wenige Sekunden und zeigt einen
Fortschrittsbalken. Danach startet sie sofort.

## Katalog erneuern

Nur nötig, wenn du neue Artikel aus Open Food Facts holen willst. Dafür wird das
.NET-Werkzeug gebraucht:

```bash
cd backend/src/ArtikelFinder.Import

# Artikel holen (dauert je nach Umfang bis zu einer Stunde):
dotnet run -- api --user-agent "ArtikelFinder/0.1 (deine@mailadresse.de)"

# Kaufland-Eigenmarken holen (K-Classic, K-Bio, Purland, Bevola …):
dotnet run -- marken --user-agent "ArtikelFinder/0.1 (deine@mailadresse.de)"

# Katalogdatei neu schreiben:
dotnet run -- export
```

`marken` fragt jede Marke in allen fünf Quellen ab (Open Food Facts über Produkt-API und
Suchdienst, dazu Open Beauty Facts, Open Products Facts, Open Pet Food Facts). Einzelne
Marken gehen mit `--marke k-classic --marke k-bio`, ein anderes Land mit `--land ""` (alle).
Beide Importwege schreiben in denselben Katalog und gleichen über die EAN ab — die
Reihenfolge ist egal, doppelte Läufe schaden nicht.

Anschließend `backend/daten/katalog-seed.tsv.gz` entpackt nach
`android/app/src/main/assets/katalog-seed.tsv` kopieren und die App neu bauen.

Open Food Facts antwortet im Dauerbetrieb regelmäßig mit 503. Der Importer wiederholt jede
Seite mit wachsender Wartezeit, überspringt sie sonst und macht weiter. Wie viele Seiten
dabei verloren gingen, steht am Ende in der Zusammenfassung — dann den Lauf wiederholen, er
ist idempotent (Abgleich über EAN).

## Entwurfsentscheidungen

**Der Katalog liegt als Textdatei bei, nicht als fertige Datenbank.** 2,8 MB TSV statt
mehrerer Megabyte SQLite, und beim Einlesen wird der Suchindex passend zur eingebauten
Normalisierung neu aufgebaut. Der Aufbau kostet einmalig wenige Sekunden.

**Der Build-Prozess entpackt `.gz`-Assets selbsttätig und schneidet die Endung ab.** Die
Datei heißt in der App deshalb `katalog-seed.tsv`. Der Katalogaufbau erkennt am Dateikopf,
ob gepackte oder ungepackte Daten vorliegen, statt sich auf eine Variante zu verlassen.

**Die Eigenmarken kommen über die Marke herein, nicht über die Warengruppe.** Der
Warengruppen-Import holt „Milch aus Deutschland" und trifft K-Classic nur zufällig mit. Für
ein Sortiment, das zu großen Teilen aus Eigenmarken besteht, ist das die falsche Achse:
gefragt wird deshalb nach `brands_tags` — K-Classic, K-Bio, K-take it veggie, K-Free,
K-Favourites, K-to go, K-Purland, Purland, Bevola, exquisit — und zwar in allen Quellen der
Open-Food-Facts-Familie. Toilettenpapier, Duschgel und Katzenfutter stehen
nicht in Open Food Facts, sondern in Open Beauty Facts, Open Products Facts und Open Pet
Food Facts; sie sprechen dieselbe API unter anderer Adresse. Ohne sie fehlte der halbe
Drogerie- und Tierbedarfsteil der Marke.

**Open Food Facts wird über zwei Endpunkte gefragt, nicht über einen mit Ausweichadresse.**
`api/v2/search` ist regelmäßig tagelang am Stück auf 503, während der eigenständige
Suchdienst `search.openfoodfacts.org` antwortet — und die beiden Indizes decken sich nicht
vollständig. Deshalb ist der Suchdienst eine gleichwertige fünfte Quelle mit eigenem
Abfragedialekt (`brands_tags:"k-classic" AND countries_tags:"en:germany"` statt
Feldparametern), keine Notfalladresse. Doppelte Treffer kostet der Abgleich über die EAN
nichts, und fällt eine Quelle aus, liefert die andere trotzdem.

**Bei Markenabfragen kommt die Kategorie aus dem einzelnen Artikel.** Eine Warengruppe
liefert ihre Zielkategorie mit, eine Marke nicht — unter K-Classic stehen Milch,
Toilettenpapier und Katzenfutter nebeneinander. `Kategoriezuordnung` liest deshalb die
Open-Food-Facts-Tags des Artikels und löst vom speziellsten zum allgemeinsten auf
(`en:uht-milks` → Milch, sonst `en:dairies` → Molkereiprodukte, sonst die Standardkategorie
der Datenbank). Was sich nicht eindeutig zuordnen lässt, bleibt ohne Kategorie: über die
Suche ist der Artikel weiter zu finden, ein falsch einsortierter schickt dich in den
falschen Gang.

**Preise und Standorte hängen am Markt, nicht am Artikel allein.** `marktId` steckt von
Anfang an in beiden Tabellen. Der Ausbau auf weitere Filialen kostet damit keine
Datenmigration.

**Die Sicherung bezieht sich auf die EAN, nicht auf die Artikel-Id.** Der Katalogaufbau
vergibt bei jeder Installation neue Ids — eine Sicherung, die daran hinge, wäre auf einem
zweiten Gerät wertlos, und genau dafür macht man eine. Selbst angelegte Artikel haben oft
keine EAN; sie behalten deshalb ihre Id und werden mit ihr wieder angelegt. Das macht das
Einlesen nebenbei wiederholbar: jeder Datensatz bringt seinen Schlüssel mit, ein zweiter
Lauf derselben Datei ändert nichts. Der Katalog selbst steht nicht in der Datei — er liegt
in der App.

**Erfassungen werden angehängt, nie überschrieben.** Der jüngste Eintrag pro
(Artikel, Markt) ist der aktuelle. Die Preishistorie und der „steht jetzt in Gang 3
statt 7"-Fall fallen dadurch ohne Zusatztabelle ab; der Änderungsverlauf hält zusätzlich
fest, wer wann was geändert hat.

**Volltextsuche über eine normalisierte Spalte.** `suchtext` enthält Name und Marke
kleingeschrieben, ohne Satzzeichen und mit beiden Umlaut-Schreibweisen („Bärenmarke" →
`baerenmarke barenmarke`). Damit findet sowohl `mueller` als auch `muller` den Artikel.
Die Normalisierung liegt in App und Backend doppelt vor und wird beidseitig gegen
dieselben Beispiele getestet — weicht eine Seite ab, findet die App Katalogartikel nicht
mehr.

**Wochenangebote tippt man einmal, nicht vierzigmal.** Ein Prospekt gilt für alle Angebote
im selben Zeitraum. Die App merkt sich deshalb das zuletzt eingegebene Aktionsende und
belegt es beim nächsten Preis vor — beim Abtippen eines Prospekts ist das der Unterschied
zwischen einer und vierzig Datumseingaben. Ein Preis ohne Werbepreis überschreibt den
Merkposten nicht.

**Barcode-Treffer werden doppelt bestätigt.** Ein Code gilt erst nach zweimaliger Erkennung
hintereinander. Bei verknitterten Etiketten sind Einzelbild-Fehlerkennungen häufig, und ein
falscher Barcode führt direkt zum falschen Artikel. Die Auswertung läuft über ML Kit
vollständig auf dem Gerät.

## Sicherung

Die eigenen Erfassungen lassen sich über *Weitere Aktionen → Sicherung* in eine Textdatei
schreiben und wieder einlesen. Die Datei landet über die Dateiauswahl des Systems dort, wo
du sie wiederfindest — die App braucht dafür kein Speicherrecht.

Einlesen **ergänzt** und löscht nichts. Dieselbe Datei zweimal einzulesen ändert nichts,
weil jeder Datensatz seine Id mitbringt.

## Datenbank ändern

Die Datenbank enthält selbst erfasste Daten, also gibt es keinen Neuaufbau bei
Schemawechseln. Beim Ändern einer Entität:

1. Entität ändern und `ArtikelDatenbank.VERSION` erhöhen.
2. Einmal bauen — Room legt `android/app/schemas/…/<Version>.json` an. Die Datei gehört
   mit ins Repository.
3. In `Migrationen.kt` den Schritt von der Vorgängerversion ergänzen.

`MigrationTest` baut die älteste Datenbank aus der eingecheckten Schemabeschreibung nach,
legt erfasste Preise und Standorte hinein und lässt Room die Migrationen darüber laufen.
Fehlt ein Schritt, schlägt der Test fehl — statt der App beim nächsten Update auf dem Handy.

## Tests

```bash
cd android && ./gradlew :app:testDebugUnitTest   # 49 Tests
cd backend && dotnet test                        # 79 Tests
```

`./gradlew test` fährt dieselben App-Tests zusätzlich gegen `release` und `dist` und
verdreifacht die Laufzeit ohne neue Erkenntnis; die CI nimmt deshalb nur `debug`.

Die App-Tests laufen unter Robolectric gegen echtes SQLite und lesen die tatsächlich
ausgelieferte Katalogdatei über den Asset-Manager ein — nicht über den Quellbaum. Genau
dieser Unterschied hat die App schon einmal beim ersten Start scheitern lassen.

## Offen

**Phase 2**
- Interaktive Grundriss-Karte (SVG) mit anklickbaren Zonen. Die Koordinatenfelder
  (`karten_x`, `karten_y` als relative 0..1-Werte) liegen im Modell bereit, die App nutzt
  bisher nur die Gang-Liste.
- Benachrichtigung, wenn ein Angebot ausläuft. Die Angebotsübersicht zeigt die Restlaufzeit
  bereits an und hebt „läuft heute/morgen ab" hervor; es fehlt der Hintergrundjob, der von
  sich aus meldet.
- Prospektdaten als Datei einlesen, statt jedes Angebot einzeln zu erfassen.
- Einkaufsliste, nach Gang sortiert — die Gangdaten liegen bereits vor.
- Volltextsuche (FTS) mit Rangfolge. Die Suche läuft heute über `LIKE '%…%'`, das führende
  Platzhalterzeichen macht den Index auf `suchtext` wirkungslos.

**Phase 3**
- Mehrbenutzerbetrieb über das Backend. `erfasst_von` ist heute Freitext und würde zur
  Nutzer-Id.

## Rechtliches

Der Katalog stammt aus [Open Food Facts](https://world.openfoodfacts.org) und seinen
Schwesterdatenbanken [Open Beauty Facts](https://world.openbeautyfacts.org),
[Open Products Facts](https://world.openproductsfacts.org) und
[Open Pet Food Facts](https://world.openpetfoodfacts.org). Alle vier stehen unter
der [Open Database License](https://opendatacommons.org/licenses/odbl/1-0/). Weil die Daten
mit der App ausgeliefert werden, ist das eine Weitergabe: Namensnennung ist Pflicht, eine
veränderte Fassung der Datenbank muss unter derselben Lizenz stehen. Für den privaten
Gebrauch folgenlos, vor einer Veröffentlichung aber zu beachten.

Kaufland.de wird **nicht** gescrapt — das verstößt gegen deren AGB. Auch die Eigenmarken
kommen deshalb nicht von dort, sondern aus den offenen Datenbanken: dort sind es
Community-Daten unter freier Lizenz, keine fremden Sortimentsdaten. Preise und Standorte
werden ausschließlich selbst im Markt erfasst.
