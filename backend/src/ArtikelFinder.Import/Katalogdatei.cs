using System.Globalization;
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
/// Enthalten ist der Richtpreis aus Open Prices: er gehört zum Artikel, nicht zu einem
/// Markt, und ist für jeden Nutzer derselbe. Selbst erfasste Preise und Standorte sind
/// dagegen markt- und personenbezogen und stehen bewusst nicht in der Katalogdatei.
///
/// Die Preisspalten sind angehängt statt eingeschoben: eine ältere Katalogdatei ohne sie
/// lässt sich weiterhin einlesen.
/// </summary>
public sealed class Katalogdatei(ArtikelFinderDbContext db, ILogger<Katalogdatei> log)
{
    private const string Kopfzeile =
        "ean\tname\tmarke\tkategorie\tbildUrl\tpreis\tpreisMin\tpreisMax\tpreisAnzahl\tpreisStand";

    /// <summary>Spaltenzahl der Fassung ohne Preise.</summary>
    private const int SpaltenOhnePreis = 5;

    private const int SpaltenMitPreis = 10;

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
                Saeubern(eintrag.BildUrl),
                AlsText(eintrag.Referenzpreis),
                AlsText(eintrag.ReferenzpreisNiedrigster),
                AlsText(eintrag.ReferenzpreisHoechster),
                eintrag.ReferenzpreisAnzahl?.ToString(CultureInfo.InvariantCulture) ?? string.Empty,
                eintrag.ReferenzpreisStand?.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture) ?? string.Empty));

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
            if (felder.Length < SpaltenOhnePreis)
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

            stapel.Add(new Rohartikel(
                name, Wert(felder[2]), ean, Wert(felder[4]), Wert(felder[3]), ReferenzpreisLesen(felder)));

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

    /// <summary>
    /// Liest die Preisspalten, falls die Datei sie hat. Eine unvollständige oder kaputte
    /// Angabe kostet nur den Richtpreis, nicht den Artikel — der Katalog ist auch ohne ihn
    /// brauchbar.
    /// </summary>
    private static Referenzpreis? ReferenzpreisLesen(string[] felder)
    {
        if (felder.Length < SpaltenMitPreis)
        {
            return null;
        }

        if (AlsBetrag(felder[5]) is not { } wert
            || AlsBetrag(felder[6]) is not { } niedrigster
            || AlsBetrag(felder[7]) is not { } hoechster
            || !int.TryParse(felder[8], CultureInfo.InvariantCulture, out var anzahl)
            || !DateOnly.TryParseExact(felder[9].Trim(), "yyyy-MM-dd", out var stand))
        {
            return null;
        }

        return new Referenzpreis(wert, niedrigster, hoechster, anzahl, stand);
    }

    private static string AlsText(decimal? wert) =>
        wert?.ToString("0.00", CultureInfo.InvariantCulture) ?? string.Empty;

    private static decimal? AlsBetrag(string feld) =>
        decimal.TryParse(feld.Trim(), NumberStyles.Number, CultureInfo.InvariantCulture, out var wert)
            ? wert
            : null;

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
