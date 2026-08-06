using System.IO.Compression;
using ArtikelFinder.Shared;
using Microsoft.Extensions.Logging;

namespace ArtikelFinder.Import;

public sealed record CsvImportEinstellungen
{
    public required string Datei { get; init; }

    /// <summary>Nur Produkte, deren Laenderliste diesen Begriff enthaelt. Leer = alle.</summary>
    public string? Land { get; init; } = "germany";

    /// <summary>0 = kein Limit.</summary>
    public int MaxArtikel { get; init; }

    public int Stapelgroesse { get; init; } = 2000;
}

/// <summary>
/// Liest den Bulk-Export von Open Food Facts (TSV, optional gzip-gepackt).
///
/// Der Export ist mehrere Gigabyte gross und wird deshalb streamend verarbeitet — die
/// Datei landet nie komplett im Speicher. Fuer den Erstbefuellungs-Lauf ist das der
/// schnellere Weg als zehntausende API-Requests.
/// </summary>
public sealed class CsvImporter(Katalogschreiber schreiber, ILogger<CsvImporter> log)
{
    public async Task<Importstatistik> AusfuehrenAsync(CsvImportEinstellungen einstellungen, CancellationToken ct)
    {
        if (!File.Exists(einstellungen.Datei))
        {
            throw new FileNotFoundException($"Die Exportdatei wurde nicht gefunden: {einstellungen.Datei}");
        }

        var gesamt = new Importstatistik();

        using var leser = LeserOeffnen(einstellungen.Datei);

        var kopfzeile = await leser.ReadLineAsync(ct);
        if (kopfzeile is null)
        {
            throw new InvalidDataException("Die Exportdatei ist leer.");
        }

        var spalten = SpaltenIndizes(kopfzeile);
        var stapel = new List<Rohartikel>(einstellungen.Stapelgroesse);
        var uebernommen = 0;
        var zeilennummer = 1L;

        while (await leser.ReadLineAsync(ct) is { } zeile)
        {
            ct.ThrowIfCancellationRequested();
            zeilennummer++;

            var felder = zeile.Split('\t');
            if (felder.Length <= spalten.Maximum)
            {
                gesamt.Uebersprungen++;
                continue;
            }

            if (!PasstZumLand(felder, spalten, einstellungen.Land))
            {
                gesamt.Uebersprungen++;
                continue;
            }

            var name = Feld(felder, spalten.NameDe) ?? Feld(felder, spalten.Name);
            if (name is null)
            {
                gesamt.OhneName++;
                continue;
            }

            var ean = Ean.Normalisieren(Feld(felder, spalten.Code));
            if (ean is null || !Ean.PruefzifferKorrekt(ean))
            {
                gesamt.OhneGueltigeEan++;
                continue;
            }

            var menge = Feld(felder, spalten.Menge);
            stapel.Add(new Rohartikel(
                Name: menge is null ? name : $"{name} {menge}",
                Marke: Feld(felder, spalten.Marken)?.Split(',')[0].Trim(),
                Ean: ean,
                BildUrl: Feld(felder, spalten.BildUrl),
                // Die Kategorie kommt beim Bulk-Import nicht mit: die OFF-Tags sind zu
                // uneinheitlich, um sie ohne Zuordnungstabelle sinnvoll zu mappen.
                // Kategorien setzt du in der App oder ueber einen gezielten API-Lauf.
                ZielKategorie: null));

            if (stapel.Count >= einstellungen.Stapelgroesse)
            {
                gesamt.Dazu(await schreiber.SchreibenAsync(stapel, ct));
                uebernommen += stapel.Count;
                stapel.Clear();

                log.LogInformation("Zeile {Zeile}: {Statistik}", zeilennummer, gesamt);

                if (einstellungen.MaxArtikel > 0 && uebernommen >= einstellungen.MaxArtikel)
                {
                    return gesamt;
                }
            }
        }

        if (stapel.Count > 0)
        {
            gesamt.Dazu(await schreiber.SchreibenAsync(stapel, ct));
        }

        return gesamt;
    }

    private static StreamReader LeserOeffnen(string pfad)
    {
        var datei = File.OpenRead(pfad);

        return pfad.EndsWith(".gz", StringComparison.OrdinalIgnoreCase)
            ? new StreamReader(new GZipStream(datei, CompressionMode.Decompress))
            : new StreamReader(datei);
    }

    private static bool PasstZumLand(string[] felder, Spalten spalten, string? land)
    {
        if (string.IsNullOrWhiteSpace(land) || spalten.Laender < 0)
        {
            return true;
        }

        var wert = Feld(felder, spalten.Laender);
        return wert is not null && wert.Contains(land, StringComparison.OrdinalIgnoreCase);
    }

    private static string? Feld(string[] felder, int index)
    {
        if (index < 0 || index >= felder.Length)
        {
            return null;
        }

        var wert = felder[index].Trim();
        return wert.Length == 0 ? null : wert;
    }

    private static Spalten SpaltenIndizes(string kopfzeile)
    {
        var namen = kopfzeile.Split('\t');
        int Suchen(string spalte) => Array.FindIndex(namen, n =>
            n.Trim().Equals(spalte, StringComparison.OrdinalIgnoreCase));

        var spalten = new Spalten
        {
            Code = Suchen("code"),
            Name = Suchen("product_name"),
            NameDe = Suchen("product_name_de"),
            Marken = Suchen("brands"),
            Menge = Suchen("quantity"),
            Laender = Suchen("countries_tags"),
            BildUrl = Suchen("image_small_url"),
        };

        if (spalten.Code < 0 || (spalten.Name < 0 && spalten.NameDe < 0))
        {
            throw new InvalidDataException(
                "Die Exportdatei enthält keine Spalten 'code' und 'product_name'. "
                + "Erwartet wird der TSV-Export von Open Food Facts.");
        }

        return spalten;
    }

    private sealed record Spalten
    {
        public required int Code { get; init; }
        public required int Name { get; init; }
        public required int NameDe { get; init; }
        public required int Marken { get; init; }
        public required int Menge { get; init; }
        public required int Laender { get; init; }
        public required int BildUrl { get; init; }

        /// <summary>Groesster benoetigter Index — kuerzere Zeilen sind defekt.</summary>
        public int Maximum => Math.Max(Code, Math.Max(Name, NameDe));
    }
}
