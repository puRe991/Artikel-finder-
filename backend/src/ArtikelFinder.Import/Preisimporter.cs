using ArtikelFinder.Api.Data;
using ArtikelFinder.Import.OpenFoodFacts;
using ArtikelFinder.Import.OpenPrices;
using ArtikelFinder.Shared;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Logging;

namespace ArtikelFinder.Import;

public sealed record PreisImportEinstellungen
{
    /// <summary>Ländername, wie ihn OpenStreetMap führt. Leer = alle Länder.</summary>
    public string Land { get; init; } = "Deutschland";

    /// <summary>Nur Läden dieser Kette, z.B. "Kaufland". Leer = alle Ketten.</summary>
    public string? Kette { get; init; }

    /// <summary>
    /// Ältere Erfassungen fließen nicht ein. Zwei Jahre sind der Kompromiss zwischen
    /// "aussagekräftig" und "überhaupt vorhanden": ein Preis von 2023 sagt heute wenig,
    /// aber für viele Artikel gibt es nur eine Handvoll Erfassungen. 0 = keine Grenze.
    /// </summary>
    public int HoechstalterTage { get; init; } = 730;

    /// <summary>
    /// Artikel anlegen, zu denen es einen Preis gibt, die im Katalog aber fehlen. Das sind
    /// Produkte, die jemand in einem deutschen Laden im Regal hatte — genau das Sortiment,
    /// um das es geht.
    /// </summary>
    public bool NeueArtikel { get; init; } = true;

    public int Seitengroesse { get; init; } = 100;

    /// <summary>
    /// Pause zwischen zwei Anfragen. Open Prices deckelt lesende Zugriffe deutlich
    /// großzügiger als Open Food Facts, ein Lauf über tausend Standorte soll den Dienst
    /// trotzdem nicht belasten.
    /// </summary>
    public TimeSpan Pause { get; init; } = TimeSpan.FromMilliseconds(250);
}

public sealed class Preisstatistik
{
    public int Standorte { get; set; }
    public int StandorteMitPreisen { get; set; }
    public int PreiseGelesen { get; set; }
    public int PreiseVerwertbar { get; set; }
    public int ArtikelBepreist { get; set; }
    public int ArtikelNeu { get; set; }

    /// <summary>EANs mit Preis, zu denen es keinen Katalogartikel gibt und keiner angelegt
    /// wurde. Ohne diese Zahl sähe ein Lauf ohne <c>--neue-artikel</c> aus wie ein Lauf, bei
    /// dem es die Preise nicht gibt.</summary>
    public int OhneArtikel { get; set; }

    public int SeitenFehlgeschlagen { get; set; }

    public override string ToString()
    {
        var text = $"{StandorteMitPreisen} von {Standorte} Standorten, {PreiseGelesen} Preise gelesen, "
            + $"{PreiseVerwertbar} verwertbar, {ArtikelBepreist} Artikel mit Richtpreis "
            + $"(davon {ArtikelNeu} neu angelegt), {OhneArtikel} EANs ohne Artikel";

        return SeitenFehlgeschlagen == 0
            ? text
            : $"{text} — ACHTUNG: {SeitenFehlgeschlagen} Seite(n) nicht geladen, erneut ausführen";
    }
}

/// <summary>
/// Holt Preise aus Open Prices und schreibt je EAN einen Richtwert an den Artikel.
///
/// Open Prices ist die Preisdatenbank derselben Familie: Freiwillige fotografieren
/// Regaletikett oder Kassenbon, die Daten stehen unter derselben freien Lizenz wie der
/// Katalog. Damit ist es die einzige Quelle, aus der dieses Projekt Preise überhaupt
/// beziehen darf — Prospekte und Händlerseiten sind es ausdrücklich nicht.
///
/// Preise hängen dort am Standort, nicht am Land. Der Lauf holt deshalb erst die deutschen
/// Läden und fragt dann Laden für Laden dessen Preise ab.
/// </summary>
public sealed class Preisimporter(
    IOpenPricesClient client,
    ArtikelFinderDbContext db,
    Katalogschreiber schreiber,
    ILogger<Preisimporter> log)
{
    private const int Stapelgroesse = 500;

    public async Task<Preisstatistik> AusfuehrenAsync(PreisImportEinstellungen einstellungen, CancellationToken ct)
    {
        var statistik = new Preisstatistik();

        var fruehestens = einstellungen.HoechstalterTage > 0
            ? DateOnly.FromDateTime(DateTime.UtcNow.Date.AddDays(-einstellungen.HoechstalterTage))
            : DateOnly.MinValue;

        var standorte = await StandorteAsync(einstellungen, statistik, ct);

        // Alle Erfassungen erst sammeln, dann je EAN zusammenfassen: der Median lässt sich
        // nicht standortweise fortschreiben, und derselbe Artikel kommt in vielen Läden vor.
        var beobachtungen = new Dictionary<string, List<Preisbeobachtung>>(StringComparer.Ordinal);
        var produkte = new Dictionary<string, OffProdukt>(StringComparer.Ordinal);

        for (var i = 0; i < standorte.Count; i++)
        {
            ct.ThrowIfCancellationRequested();

            await PreiseSammelnAsync(standorte[i], einstellungen, fruehestens, beobachtungen, produkte, statistik, ct);

            if ((i + 1) % 100 == 0)
            {
                log.LogInformation(
                    "{Erledigt}/{Gesamt} Standorte, {Preise} Preise zu {Eans} EANs.",
                    i + 1, standorte.Count, statistik.PreiseVerwertbar, beobachtungen.Count);
            }
        }

        var richtpreise = beobachtungen
            .Select(e => (Ean: e.Key, Referenzpreis: Referenzpreis.Aus(e.Value)))
            .Where(e => e.Referenzpreis is not null)
            .ToDictionary(e => e.Ean, e => e.Referenzpreis!, StringComparer.Ordinal);

        log.LogInformation("{Anzahl} EANs mit Richtpreis ermittelt, schreibe in den Katalog.", richtpreise.Count);

        var fehlende = await BestehendeBepreisenAsync(richtpreise, statistik, ct);

        if (einstellungen.NeueArtikel)
        {
            await NeueArtikelAnlegenAsync(fehlende, richtpreise, produkte, statistik, ct);
        }

        statistik.OhneArtikel = fehlende.Count - statistik.ArtikelNeu;
        return statistik;
    }

    /// <summary>Die Läden des Landes, die überhaupt Preise haben.</summary>
    private async Task<List<OpStandort>> StandorteAsync(
        PreisImportEinstellungen einstellungen,
        Preisstatistik statistik,
        CancellationToken ct)
    {
        var gefunden = new List<OpStandort>();
        var seite = 1;

        while (true)
        {
            ct.ThrowIfCancellationRequested();

            var antwort = await client.StandorteAsync(
                einstellungen.Land, seite, einstellungen.Seitengroesse, ct);

            if (antwort is null)
            {
                statistik.SeitenFehlgeschlagen++;
                break;
            }

            gefunden.AddRange(antwort.Eintraege);
            statistik.Standorte += antwort.Eintraege.Count;

            if (seite >= antwort.Seiten || antwort.Eintraege.Count == 0)
            {
                break;
            }

            seite++;
            await PausierenAsync(einstellungen.Pause, ct);
        }

        var abzufragen = gefunden
            .Where(s => s.Preisanzahl > 0)
            .Where(s => string.IsNullOrWhiteSpace(einstellungen.Kette)
                || (s.Kette?.Contains(einstellungen.Kette, StringComparison.OrdinalIgnoreCase) ?? false))
            .OrderByDescending(s => s.Preisanzahl)
            .ToList();

        statistik.StandorteMitPreisen = abzufragen.Count;

        log.LogInformation(
            "{Mit} von {Gesamt} Standorten in {Land} haben Preise ({Summe} insgesamt).",
            abzufragen.Count, gefunden.Count, einstellungen.Land, abzufragen.Sum(s => s.Preisanzahl));

        return abzufragen;
    }

    private async Task PreiseSammelnAsync(
        OpStandort standort,
        PreisImportEinstellungen einstellungen,
        DateOnly fruehestens,
        Dictionary<string, List<Preisbeobachtung>> beobachtungen,
        Dictionary<string, OffProdukt> produkte,
        Preisstatistik statistik,
        CancellationToken ct)
    {
        var seite = 1;

        while (true)
        {
            ct.ThrowIfCancellationRequested();

            var antwort = await client.PreiseAsync(standort.Id, seite, einstellungen.Seitengroesse, ct);

            if (antwort is null)
            {
                // Ein ausgefallener Laden kostet ein paar Preise, nicht den Lauf.
                statistik.SeitenFehlgeschlagen++;
                return;
            }

            statistik.PreiseGelesen += antwort.Eintraege.Count;

            foreach (var preis in antwort.Eintraege)
            {
                var ean = Ean.Normalisieren(preis.ProduktCode);
                if (ean is null || Preisbeobachtung.Aus(preis, fruehestens) is not { } beobachtung)
                {
                    continue;
                }

                if (!beobachtungen.TryGetValue(ean, out var liste))
                {
                    liste = [];
                    beobachtungen[ean] = liste;
                }

                liste.Add(beobachtung);
                statistik.PreiseVerwertbar++;

                // Die Produktdaten hängen an jedem Preis; der erste brauchbare Satz genügt.
                if (preis.Produkt is { } produkt && !produkte.ContainsKey(ean))
                {
                    produkte[ean] = produkt.AlsOffProdukt();
                }
            }

            if (seite >= antwort.Seiten || antwort.Eintraege.Count == 0)
            {
                return;
            }

            seite++;
            await PausierenAsync(einstellungen.Pause, ct);
        }
    }

    /// <summary>
    /// Schreibt die Richtpreise an die Artikel, die es schon gibt, und gibt die EANs zurück,
    /// zu denen kein Artikel gefunden wurde.
    ///
    /// Anders als Name, Marke und Bild wird der Richtpreis dabei <b>überschrieben</b>: er ist
    /// keine Eingabe, die jemand gemacht haben könnte, sondern der Stand der Quelle. Ein
    /// zweiter Lauf soll den älteren Wert ablösen. Selbst erfasste Preise stehen in der
    /// Preistabelle und werden hier nicht angefasst.
    /// </summary>
    private async Task<List<string>> BestehendeBepreisenAsync(
        IReadOnlyDictionary<string, Referenzpreis> richtpreise,
        Preisstatistik statistik,
        CancellationToken ct)
    {
        var fehlende = new List<string>();

        foreach (var stapel in richtpreise.Keys.Chunk(Stapelgroesse))
        {
            ct.ThrowIfCancellationRequested();

            var eans = stapel.ToList();
            var vorhandene = await db.Artikel
                .Where(a => a.Ean != null && eans.Contains(a.Ean))
                .ToListAsync(ct);

            foreach (var artikel in vorhandene)
            {
                richtpreise[artikel.Ean!].AnwendenAuf(artikel);
                statistik.ArtikelBepreist++;
            }

            await db.SaveChangesAsync(ct);
            db.ChangeTracker.Clear();

            var getroffen = vorhandene
                .Select(a => a.Ean!)
                .ToHashSet(StringComparer.Ordinal);

            fehlende.AddRange(eans.Where(e => !getroffen.Contains(e)));
        }

        return fehlende;
    }

    /// <summary>
    /// Legt Artikel an, zu denen es einen Preis gibt, die aber im Katalog fehlen.
    ///
    /// Es gelten dieselben Mindestanforderungen wie beim Import aus Open Food Facts: ein
    /// Name muss da sein und die Prüfziffer stimmen. Was die Quelle nur als nackten Barcode
    /// kennt, hilft im Regal nicht weiter.
    /// </summary>
    private async Task NeueArtikelAnlegenAsync(
        IReadOnlyList<string> fehlende,
        IReadOnlyDictionary<string, Referenzpreis> richtpreise,
        IReadOnlyDictionary<string, OffProdukt> produkte,
        Preisstatistik statistik,
        CancellationToken ct)
    {
        var anzulegen = new List<Rohartikel>();

        foreach (var ean in fehlende)
        {
            if (!produkte.TryGetValue(ean, out var produkt)
                || produkt.BesterName() is not { } name
                || !Ean.PruefzifferKorrekt(ean))
            {
                continue;
            }

            anzulegen.Add(new Rohartikel(
                name,
                produkt.ErsteMarke(),
                ean,
                produkt.BildUrl,
                Kategoriezuordnung.Fuer(produkt, null),
                richtpreise[ean]));
        }

        foreach (var stapel in anzulegen.Chunk(Stapelgroesse))
        {
            ct.ThrowIfCancellationRequested();

            var ergebnis = await schreiber.SchreibenAsync(stapel, ct);
            statistik.ArtikelNeu += ergebnis.Neu;
            statistik.ArtikelBepreist += ergebnis.Neu;
        }
    }

    private static Task PausierenAsync(TimeSpan pause, CancellationToken ct) =>
        pause > TimeSpan.Zero ? Task.Delay(pause, ct) : Task.CompletedTask;
}
