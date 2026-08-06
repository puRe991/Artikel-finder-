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

/// <summary>Holt Artikel ueber die Open-Food-Facts-Suche und schreibt sie in den Katalog.</summary>
public sealed class ApiImporter(IOffClient client, Katalogschreiber schreiber, ILogger<ApiImporter> log)
{
    /// <summary>Ab so vielen Ausfaellen hintereinander ist der Dienst als Ganzes weg.</summary>
    private const int MaxAusfaelleInFolge = 3;

    public async Task<Importstatistik> AusfuehrenAsync(ApiImportEinstellungen einstellungen, CancellationToken ct)
    {
        var gesamt = new Importstatistik();

        foreach (var ziel in einstellungen.Ziele)
        {
            ct.ThrowIfCancellationRequested();

            var statistik = await ZielImportierenAsync(ziel, einstellungen, ct);
            gesamt.Dazu(statistik);

            log.LogInformation("{Tag} -> {Kategorie}: {Statistik}", ziel.OffTag, ziel.ZielKategorie, statistik);
        }

        return gesamt;
    }

    private async Task<Importstatistik> ZielImportierenAsync(
        Importziel ziel,
        ApiImportEinstellungen einstellungen,
        CancellationToken ct)
    {
        var statistik = new Importstatistik();
        var seite = 1;
        var uebernommen = 0;
        var ausfaelleInFolge = 0;

        while (uebernommen < einstellungen.MaxProZiel)
        {
            ct.ThrowIfCancellationRequested();

            var ergebnis = await client.SuchenAsync(
                ziel.OffTag, einstellungen.Land, seite, einstellungen.Seitengroesse, ct);

            if (ergebnis.IstFehlgeschlagen)
            {
                // Eine vorübergehend nicht erreichbare Seite beendet die Warengruppe nicht —
                // sonst kostet ein einzelner 503 auf Seite 1 den kompletten Rest. Erst wenn
                // mehrere Seiten hintereinander ausfallen, ist Open Food Facts offenbar
                // insgesamt nicht erreichbar und Weitermachen sinnlos.
                statistik.SeitenFehlgeschlagen++;
                ausfaelleInFolge++;

                if (ausfaelleInFolge >= MaxAusfaelleInFolge)
                {
                    log.LogWarning(
                        "{Tag}: {Anzahl} Seiten in Folge ausgefallen — Warengruppe abgebrochen.",
                        ziel.OffTag, ausfaelleInFolge);
                    break;
                }

                seite++;
                await PausierenAsync(einstellungen, ct);
                continue;
            }

            ausfaelleInFolge = 0;
            var antwort = ergebnis.Antwort;

            if (antwort is null || antwort.Produkte.Count == 0)
            {
                break;
            }

            var (roh, verworfen) = Umwandeln(antwort.Produkte, ziel.ZielKategorie);
            statistik.Dazu(verworfen);

            var geschrieben = await schreiber.SchreibenAsync(roh, ct);
            statistik.Dazu(geschrieben);

            uebernommen += roh.Count;
            seite++;

            // Letzte Seite erreicht?
            if (antwort.Produkte.Count < einstellungen.Seitengroesse)
            {
                break;
            }

            await PausierenAsync(einstellungen, ct);
        }

        return statistik;
    }

    private static Task PausierenAsync(ApiImportEinstellungen einstellungen, CancellationToken ct) =>
        einstellungen.Pause > TimeSpan.Zero ? Task.Delay(einstellungen.Pause, ct) : Task.CompletedTask;

    /// <summary>
    /// Filtert die fuer den Katalog unbrauchbaren Datensaetze heraus. Open Food Facts
    /// enthaelt viele halbfertige Eintraege — ohne Name oder mit kaputtem Barcode.
    /// </summary>
    private static (List<Rohartikel> Brauchbar, Importstatistik Verworfen) Umwandeln(
        IEnumerable<OffProdukt> produkte,
        string zielKategorie)
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

            brauchbar.Add(new Rohartikel(name, produkt.ErsteMarke(), ean, produkt.BildUrl, zielKategorie));
        }

        return (brauchbar, verworfen);
    }
}
