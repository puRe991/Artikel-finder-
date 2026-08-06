using ArtikelFinder.Import.OpenFoodFacts;
using ArtikelFinder.Shared;
using Microsoft.Extensions.Logging;

namespace ArtikelFinder.Import;

public sealed record ApiImportEinstellungen
{
    public IReadOnlyList<Importziel> Ziele { get; init; } = Importziel.Standard;
    public string Land { get; init; } = "germany";

    /// <summary>Obergrenze je Warengruppe. Verhindert, dass ein Lauf tagelang laeuft.</summary>
    public int MaxProZiel { get; init; } = 500;

    public int Seitengroesse { get; init; } = 100;

    /// <summary>Pause zwischen zwei Requests. Die Such-API erlaubt 10 Anfragen pro Minute.</summary>
    public TimeSpan Pause { get; init; } = TimeSpan.FromSeconds(6);
}

public sealed record MarkenImportEinstellungen
{
    public IReadOnlyList<Eigenmarke> Marken { get; init; } = Eigenmarke.Standard;

    /// <summary>
    /// Welche Datenbanken der Familie abgefragt werden. Alle vier sind die Voreinstellung:
    /// Eigenmarken hoeren nicht bei Lebensmitteln auf.
    /// </summary>
    public IReadOnlyList<OffDatenbank> Datenbanken { get; init; } = OffDatenbank.Alle;

    public string Land { get; init; } = "germany";

    /// <summary>Obergrenze je Marke und Datenbank. 0 = keine Grenze.</summary>
    public int MaxProMarke { get; init; }

    public int Seitengroesse { get; init; } = 100;

    public TimeSpan Pause { get; init; } = TimeSpan.FromSeconds(6);
}

/// <summary>Holt Artikel ueber die Open-Food-Facts-Suche und schreibt sie in den Katalog.</summary>
public sealed class ApiImporter(IOffClient client, Katalogschreiber schreiber, ILogger<ApiImporter> log)
{
    /// <summary>Ab so vielen Ausfaellen hintereinander ist der Dienst als Ganzes weg.</summary>
    private const int MaxAusfaelleInFolge = 3;

    /// <summary>Importiert nach Warengruppe: die Zielkategorie steht in der Abfrage.</summary>
    public async Task<Importstatistik> AusfuehrenAsync(ApiImportEinstellungen einstellungen, CancellationToken ct)
    {
        var gesamt = new Importstatistik();

        foreach (var ziel in einstellungen.Ziele)
        {
            ct.ThrowIfCancellationRequested();

            var statistik = await AbfrageImportierenAsync(
                OffAbfrage.NachKategorie(ziel.OffTag, einstellungen.Land),
                einstellungen.MaxProZiel,
                einstellungen.Seitengroesse,
                einstellungen.Pause,
                _ => ziel.ZielKategorie,
                ct);

            gesamt.Dazu(statistik);

            log.LogInformation("{Tag} -> {Kategorie}: {Statistik}", ziel.OffTag, ziel.ZielKategorie, statistik);
        }

        return gesamt;
    }

    /// <summary>
    /// Importiert nach Marke, quer durch alle Datenbanken der Familie.
    ///
    /// Anders als bei der Warengruppe gibt die Abfrage hier keine Kategorie her — eine Marke
    /// zieht sich durchs ganze Sortiment. Die Kategorie kommt deshalb je Artikel aus seinen
    /// eigenen Tags.
    /// </summary>
    public async Task<Importstatistik> MarkenImportierenAsync(
        MarkenImportEinstellungen einstellungen,
        CancellationToken ct)
    {
        var gesamt = new Importstatistik();
        var erste = true;

        foreach (var marke in einstellungen.Marken)
        {
            var jeMarke = new Importstatistik();

            foreach (var datenbank in einstellungen.Datenbanken)
            {
                ct.ThrowIfCancellationRequested();

                // Zwischen zwei Abfragen genauso pausieren wie zwischen zwei Seiten — sonst
                // laufen bei zwoelf Marken mal vier Datenbanken die ersten Anfragen ohne
                // Abstand ins Limit.
                if (!erste)
                {
                    await PausierenAsync(einstellungen.Pause, ct);
                }

                erste = false;

                var statistik = await AbfrageImportierenAsync(
                    OffAbfrage.NachMarke(datenbank, marke.MarkenTag, einstellungen.Land),
                    einstellungen.MaxProMarke,
                    einstellungen.Seitengroesse,
                    einstellungen.Pause,
                    produkt => Kategoriezuordnung.Fuer(produkt, datenbank.StandardKategorie),
                    ct);

                jeMarke.Dazu(statistik);
            }

            gesamt.Dazu(jeMarke);
            log.LogInformation("{Marke}: {Statistik}", marke.Anzeigename, jeMarke);
        }

        return gesamt;
    }

    private async Task<Importstatistik> AbfrageImportierenAsync(
        OffAbfrage abfrage,
        int maxArtikel,
        int seitengroesse,
        TimeSpan pause,
        Func<OffProdukt, string?> kategorie,
        CancellationToken ct)
    {
        var statistik = new Importstatistik();
        var grenze = maxArtikel > 0 ? maxArtikel : int.MaxValue;
        var seite = 1;
        var uebernommen = 0;
        var ausfaelleInFolge = 0;

        while (uebernommen < grenze)
        {
            ct.ThrowIfCancellationRequested();

            var ergebnis = await client.SuchenAsync(abfrage, seite, seitengroesse, ct);

            if (ergebnis.IstFehlgeschlagen)
            {
                // Eine vorübergehend nicht erreichbare Seite beendet die Abfrage nicht —
                // sonst kostet ein einzelner 503 auf Seite 1 den kompletten Rest. Erst wenn
                // mehrere Seiten hintereinander ausfallen, ist Open Food Facts offenbar
                // insgesamt nicht erreichbar und Weitermachen sinnlos.
                statistik.SeitenFehlgeschlagen++;
                ausfaelleInFolge++;

                if (ausfaelleInFolge >= MaxAusfaelleInFolge)
                {
                    log.LogWarning(
                        "{Abfrage}: {Anzahl} Seiten in Folge ausgefallen — abgebrochen.",
                        abfrage, ausfaelleInFolge);
                    break;
                }

                seite++;
                await PausierenAsync(pause, ct);
                continue;
            }

            ausfaelleInFolge = 0;
            var antwort = ergebnis.Antwort;

            if (antwort is null || antwort.Produkte.Count == 0)
            {
                break;
            }

            var (roh, verworfen) = Umwandeln(antwort.Produkte, kategorie);
            statistik.Dazu(verworfen);

            var geschrieben = await schreiber.SchreibenAsync(roh, ct);
            statistik.Dazu(geschrieben);

            uebernommen += roh.Count;
            seite++;

            // Letzte Seite erreicht?
            if (antwort.Produkte.Count < seitengroesse)
            {
                break;
            }

            await PausierenAsync(pause, ct);
        }

        return statistik;
    }

    private static Task PausierenAsync(TimeSpan pause, CancellationToken ct) =>
        pause > TimeSpan.Zero ? Task.Delay(pause, ct) : Task.CompletedTask;

    /// <summary>
    /// Filtert die fuer den Katalog unbrauchbaren Datensaetze heraus. Open Food Facts
    /// enthaelt viele halbfertige Eintraege — ohne Name oder mit kaputtem Barcode.
    /// </summary>
    private static (List<Rohartikel> Brauchbar, Importstatistik Verworfen) Umwandeln(
        IEnumerable<OffProdukt> produkte,
        Func<OffProdukt, string?> kategorie)
    {
        var brauchbar = new List<Rohartikel>();
        var verworfen = new Importstatistik();

        foreach (var produkt in produkte)
        {
            var name = produkt.BesterName();
            if (name is null)
            {
                verworfen.OhneName++;
                continue;
            }

            var ean = Ean.Normalisieren(produkt.Code);
            if (ean is null || !Ean.PruefzifferKorrekt(ean))
            {
                verworfen.OhneGueltigeEan++;
                continue;
            }

            brauchbar.Add(new Rohartikel(name, produkt.ErsteMarke(), ean, produkt.BildUrl, kategorie(produkt)));
        }

        return (brauchbar, verworfen);
    }
}
