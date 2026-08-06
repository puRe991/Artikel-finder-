using System.IO.Compression;
using System.Text;
using ArtikelFinder.Api.Data;
using ArtikelFinder.Shared;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Logging;

namespace ArtikelFinder.Import;

/// <summary>
/// Liest und schreibt den Artikelkatalog als portable Datei.
///
/// Zweck: ein einmal aufgebauter Katalog soll nicht an einer SQLite-Datei hängen. Ein
/// gepacktes TSV ist klein genug fürs Repository, überlebt Schemaänderungen (es enthält
/// nur fachliche Felder, keine Ids) und lässt sich ohne Werkzeug ansehen.
///
/// Bewusst nicht enthalten: Preise und Standorte. Die sind markt- und personenbezogen und
/// gehören nicht in eine allgemeine Katalogdatei.
/// </summary>
public sealed class Katalogdatei(ArtikelFinderDbContext db, ILogger<Katalogdatei> log)
{
    private const string Kopfzeile = "ean\tname\tmarke\tkategorie\tbildUrl";

    public async Task<int> SchreibenAsync(string pfad, CancellationToken ct)
    {
        var verzeichnis = Path.GetDirectoryName(Path.GetFullPath(pfad));
        if (!string.IsNullOrEmpty(verzeichnis))
        {
            Directory.CreateDirectory(verzeichnis);
        }

        await using var datei = File.Create(pfad);
        await using var strom = Packen(pfad, datei);
        await using var schreiber = new StreamWriter(strom, new UTF8Encoding(false));

        await schreiber.WriteLineAsync(Kopfzeile);

        var geschrieben = 0;
        var ohneEan = 0;

        // Ohne EAN ist ein Artikel beim Wiedereinlesen nicht zuzuordnen — der Abgleich
        // läuft über den Barcode.
        var artikel = db.Artikel
            .AsNoTracking()
            .Include(a => a.Kategorie)
            .OrderBy(a => a.Ean)
            .AsAsyncEnumerable();

        await foreach (var eintrag in artikel.WithCancellation(ct))
        {
            if (string.IsNullOrWhiteSpace(eintrag.Ean))
            {
                ohneEan++;
                continue;
            }

            await schreiber.WriteLineAsync(string.Join('\t',
                eintrag.Ean,
                Saeubern(eintrag.Name),
                Saeubern(eintrag.Marke),
                Saeubern(eintrag.Kategorie?.Name),
                Saeubern(eintrag.BildUrl)));

            geschrieben++;
        }

        if (ohneEan > 0)
        {
            log.LogWarning("{Anzahl} Artikel ohne EAN wurden nicht exportiert.", ohneEan);
        }

        return geschrieben;
    }

    public async Task<Importstatistik> LesenAsync(
        string pfad,
        Katalogschreiber schreiber,
        int stapelgroesse,
        CancellationToken ct)
    {
        if (!File.Exists(pfad))
        {
            throw new FileNotFoundException($"Katalogdatei nicht gefunden: {pfad}");
        }

        await using var datei = File.OpenRead(pfad);
        await using var strom = Entpacken(pfad, datei);
        using var leser = new StreamReader(strom);

        var kopfzeile = await leser.ReadLineAsync(ct);
        if (kopfzeile?.StartsWith("ean\t", StringComparison.OrdinalIgnoreCase) != true)
        {
            throw new InvalidDataException(
                "Unerwartetes Format. Erwartet wird eine mit 'export' erzeugte Katalogdatei.");
        }

        var gesamt = new Importstatistik();
        var stapel = new List<Rohartikel>(stapelgroesse);

        while (await leser.ReadLineAsync(ct) is { } zeile)
        {
            ct.ThrowIfCancellationRequested();

            if (string.IsNullOrWhiteSpace(zeile))
            {
                continue;
            }

            var felder = zeile.Split('\t');
            if (felder.Length < 5)
            {
                gesamt.Uebersprungen++;
                continue;
            }

            var ean = Ean.Normalisieren(felder[0]);
            var name = Wert(felder[1]);

            if (ean is null)
            {
                gesamt.OhneGueltigeEan++;
                continue;
            }

            if (name is null)
            {
                gesamt.OhneName++;
                continue;
            }

            stapel.Add(new Rohartikel(name, Wert(felder[2]), ean, Wert(felder[4]), Wert(felder[3])));

            if (stapel.Count >= stapelgroesse)
            {
                gesamt.Dazu(await schreiber.SchreibenAsync(stapel, ct));
                stapel.Clear();
            }
        }

        if (stapel.Count > 0)
        {
            gesamt.Dazu(await schreiber.SchreibenAsync(stapel, ct));
        }

        return gesamt;
    }

    private static Stream Packen(string pfad, Stream datei) =>
        pfad.EndsWith(".gz", StringComparison.OrdinalIgnoreCase)
            ? new GZipStream(datei, CompressionLevel.SmallestSize)
            : datei;

    private static Stream Entpacken(string pfad, Stream datei) =>
        pfad.EndsWith(".gz", StringComparison.OrdinalIgnoreCase)
            ? new GZipStream(datei, CompressionMode.Decompress)
            : datei;

    /// <summary>Tabulatoren und Zeilenumbrüche in Produktnamen würden das Format zerlegen.</summary>
    private static string Saeubern(string? wert) =>
        string.IsNullOrWhiteSpace(wert)
            ? string.Empty
            : wert.Replace('\t', ' ').Replace('\r', ' ').Replace('\n', ' ').Trim();

    private static string? Wert(string feld) => feld.Trim() is { Length: > 0 } w ? w : null;
}
