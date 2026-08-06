# Artikel-Finder

Artikelsuche für den Kaufland Gießen: Name, EAN, Normalpreis, laufender Werbepreis und der
Gang, in dem der Artikel steht.

Der Katalog wird aus [Open Food Facts](https://world.openfoodfacts.org/data) vorbefüllt
(Name, Marke, EAN, Bild, Kategorie). Preise und Standorte gibt es dort nicht — die entstehen
beim Einkaufen: scannen, Preis und Gang eintippen, fertig.

## Projektstruktur

```
backend/                              .NET-8-Solution
  src/ArtikelFinder.Shared/           DTOs, EAN- und Suchtext-Normalisierung
  src/ArtikelFinder.Api/              Web-API, EF-Core-Modell, Migrationen
  src/ArtikelFinder.Import/           Konsolen-Tool für den Open-Food-Facts-Import
  daten/katalog-seed.tsv.gz           Vorbefüllter Artikelkatalog (siehe „Katalog sichern")
  tests/ArtikelFinder.Api.Tests/      55 Tests gegen echtes SQLite
android/                              Kotlin + Jetpack Compose
  app/src/main/java/de/artikelfinder/app/
    data/                             Retrofit-API, Room-Cache, Repository
    ui/suche | detail | bearbeiten | scan | gaenge | verlauf
  app/src/test/                       10 Tests (Repository gegen MockWebServer)
```

## Schnellstart

Vorausgesetzt: .NET 8 SDK, JDK 17+, Android SDK (für die App).

### 1. API starten

```bash
cd backend/src/ArtikelFinder.Api
dotnet run
```

Läuft auf `http://0.0.0.0:5080`, legt die SQLite-Datei an, wendet die Migrationen an und
seedet den Markt „Kaufland Gießen" samt Kategorieraster. Swagger-UI unter
`http://localhost:5080/swagger`.

### 2. Katalog befüllen

```bash
cd backend/src/ArtikelFinder.Import

# Über die Such-API, eine Warengruppe:
dotnet run -- api --kategorie en:milks --max 200

# Standardliste (27 Warengruppen), dauert wegen der Ratenbegrenzung eine Weile:
dotnet run -- api --max 500

# Alternativ der Bulk-Export (mehrere GB, wird streamend gelesen):
dotnet run -- csv --datei en.openfoodfacts.org.products.csv.gz --max 50000
```

Beide Befehle schreiben in dieselbe Datenbank wie die API
(`--datenbank "Data Source=..."`, Standard `artikelfinder.db` im Arbeitsverzeichnis).

**Vor dem ersten Lauf den User-Agent setzen.** Open Food Facts blockt anonyme Clients:

```bash
dotnet run -- api --user-agent "ArtikelFinder/0.1 (deine@mailadresse.de)"
```

Open Food Facts antwortet im Dauerbetrieb regelmäßig mit 503. Der Importer wiederholt jede
Seite mit wachsender Wartezeit; bleibt sie trotzdem aus, überspringt er sie und macht mit
der nächsten weiter. Wie viele Seiten dabei verloren gingen, steht am Ende in der
Zusammenfassung — dann den Lauf einfach wiederholen, er ist idempotent (Abgleich über EAN).

### Katalog sichern und wiederherstellen

Ein vollständiger Importlauf dauert rund 40 Minuten. Damit das Ergebnis nicht an einer
lokalen SQLite-Datei hängt, liegt es als gepacktes TSV im Repository:

```bash
# Frische Datenbank aus dem mitgelieferten Katalog aufbauen (Sekunden statt Minuten):
dotnet run -- seed

# Nach eigenen Ergänzungen den Katalog neu ablegen:
dotnet run -- export
```

Standardpfad ist `backend/daten/katalog-seed.tsv.gz`. Die Datei enthält Name, Marke, EAN,
Kategorie und Bild-URL — bewusst **keine** Preise und Standorte: die sind markt- und
personenbezogen und gehören nicht in eine allgemeine Katalogdatei. `seed` ist idempotent
und lässt selbst erfasste Artikel unangetastet.

### 3. App bauen

```bash
cd android
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew :app:assembleDebug
```

Die API-Adresse steht in `app/build.gradle.kts` als `API_BASIS_URL`. `10.0.2.2` ist der
Host aus dem Emulator; für ein echtes Gerät die LAN-Adresse des Rechners eintragen und
prüfen, dass sie zu `res/xml/network_security_config.xml` passt (Klartext-HTTP ist nur für
private Adressbereiche erlaubt).

## Endpunkte

| Methode | Pfad | Zweck |
| --- | --- | --- |
| GET | `/api/artikel?q=&kategorieId=&nurMitWerbepreis=&seite=` | Volltextsuche mit Filtern |
| GET | `/api/artikel/{id}` | Artikel mit Preis- und Standorthistorie |
| GET | `/api/artikel/ean/{ean}` | Barcode-Lookup (404 = unbekannt) |
| POST | `/api/artikel` | Anlegen, optional mit Erstpreis und Standort |
| PUT/DELETE | `/api/artikel/{id}` | Stammdaten ändern / löschen |
| POST | `/api/artikel/{id}/preise` | Preis erfassen |
| POST | `/api/artikel/{id}/standorte` | Standort erfassen |
| GET | `/api/artikel/{id}/verlauf` | Änderungsverlauf |
| GET | `/api/kategorien`, `/api/maerkte` | Stammdaten |
| GET | `/api/maerkte/{id}/gaenge` | Belegte Gänge mit Artikelzahl |
| GET | `/api/maerkte/{id}/gaenge/{gang}/artikel` | Artikel eines Gangs |

## Entwurfsentscheidungen

**Preise und Standorte hängen am Markt, nicht am Artikel allein.** `MarktId` steckt von
Anfang an in beiden Tabellen. Der Ausbau auf weitere Filialen (Phase 3) kostet damit keine
Datenmigration — nur zusätzliche Zeilen in `Maerkte`.

**Erfassungen werden angehängt, nie überschrieben.** Der jüngste Eintrag pro
(Artikel, Markt) ist der aktuelle. Die Preishistorie und der „steht jetzt in Gang 3
statt 7"-Fall fallen dadurch ohne Zusatztabelle ab; der Änderungsverlauf hält
zusätzlich fest, wer wann was geändert hat.

**Nutzerdaten gewinnen gegen den Import.** Ein Artikel, den du im Markt selbst angelegt
hast, wird von einem späteren Import-Lauf nicht angefasst. Bei importierten Artikeln füllt
der Import nur leere Felder auf. Ohne diese Regel räumt ein Nachtlauf die Arbeit eines
Einkaufs weg.

**Volltextsuche über eine normalisierte Spalte statt FTS.** `Artikel.SuchText` enthält
Name und Marke kleingeschrieben, ohne Satzzeichen und mit beiden Umlaut-Schreibweisen
(„Bärenmarke" → `baerenmarke barenmarke`). Damit findet sowohl `mueller` als auch `muller`
den Artikel, und die Suche verhält sich auf SQLite und PostgreSQL identisch — provider-
spezifische Volltextindizes hätten das nicht getan.

**DateTimeOffset wird auf SQLite als Ticks gespeichert.** SQLite lehnt `ORDER BY` auf
`DateTimeOffset` ab, und genau das braucht jede „jüngster Preis"-Abfrage. Ein Value
Converter (`SqliteZeitKonverter`) legt die Werte als UTC-Ticks ab; auf PostgreSQL bleibt
der native `timestamptz`.

**Offline: lesen ja, schreiben nein.** Die App hält die zuletzt gesehenen Artikel in Room
vor und zeigt sie ohne Netz mit deutlichem Hinweis an. Erfassungen brauchen dagegen eine
Verbindung — eine stille Warteschlange würde verschleiern, ob der Preis angekommen ist.
Eine echte Offline-Erfassung lohnt sich erst mit dem Mehrbenutzerbetrieb, wenn ohnehin
Konflikte aufgelöst werden müssen.

**Barcode-Treffer werden doppelt bestätigt.** Ein Code gilt erst nach zweimaliger Erkennung
hintereinander. Bei verknitterten Etiketten sind Einzelbild-Fehlerkennungen häufig, und ein
falscher Barcode führt direkt zum falschen Artikel.

## Tests

```bash
cd backend && dotnet test          # 55 Tests
cd android && ./gradlew test       # 10 Tests
```

Die Backend-Tests laufen gegen echtes SQLite (In-Memory), nicht gegen den
InMemory-Provider — die interessanten Fehler dieses Projekts (Sortierung von
`DateTimeOffset`, partielle Indizes, `LIKE`-Verhalten) treten nur beim echten Provider auf.

## Offen (nach Ausbaustufen)

**Phase 2**
- Interaktive Grundriss-Karte (SVG) mit anklickbaren Zonen. Die Koordinatenfelder
  (`KartenX`, `KartenY` als relative 0..1-Werte) und `Markt.GrundrissUrl` liegen im Modell
  bereit, die App nutzt bisher nur die Gang-Liste.
- Erinnerung „Angebot läuft bald ab" — die Gültigkeitszeiträume sind erfasst, es fehlt der
  Hintergrundjob plus Benachrichtigung.

**Phase 3**
- Nutzerkonten. `ErfasstVon`/`GeaendertVon` sind heute Freitext und werden zur Nutzer-Id.
- Authentifizierung an der API. Läuft im MVP bewusst offen im lokalen Netz.
- Umstellung auf PostgreSQL: in `Program.cs` `UseNpgsql` eintragen, Npgsql referenzieren
  und die Migrationen neu erzeugen. Der Value Converter für Zeitstempel entfällt dabei.
- Bilder in einen Objektspeicher; aktuell werden nur die Bild-URLs von Open Food Facts
  verlinkt.

## Rechtliches

Der Katalog (`backend/daten/katalog-seed.tsv.gz`) stammt aus
[Open Food Facts](https://world.openfoodfacts.org) und steht unter der
[Open Database License](https://opendatacommons.org/licenses/odbl/1-0/). Weil die Datei
hier mitgeliefert wird, ist das eine Weitergabe: Namensnennung ist Pflicht, und eine
veränderte Fassung der Datenbank muss unter derselben Lizenz stehen. Für den privaten
Gebrauch ist das folgenlos — vor einer Veröffentlichung (Phase 3) aber zu beachten.

Kaufland.de wird **nicht** gescrapt — das verstößt gegen deren AGB. Preise und Standorte
werden ausschließlich selbst im Markt erfasst.
