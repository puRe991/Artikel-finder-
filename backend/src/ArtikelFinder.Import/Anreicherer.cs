using ArtikelFinder.Api.Data;
using ArtikelFinder.Shared;
using ArtikelFinder.Import.OpenFoodFacts;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Logging;

namespace ArtikelFinder.Import;

public sealed class AnreicherungsEinstellungen
{
    /// <summary>Barcodes je Anfrage. Open Food Facts nimmt bis zu hundert entgegen.</summary>
    public int Buendelgroesse { get; init; } = 100;

    /// <summary>Pause zwischen den Buendeln — das Projekt laeuft auf Spenden.</summary>
    public TimeSpan Pause { get; init; } = TimeSpan.FromMilliseconds(1200);

    /// <summary>0 = alle. Sonst nur so viele Artikel, etwa fuer einen Probelauf.</summary>
    public int Hoechstzahl { get; init; }

    /// <summary>
    /// Standardmaessig werden nur Artikel gefragt, zu denen noch nichts vorliegt. Damit ist
    /// ein abgebrochener Lauf einfach fortsetzbar, statt von vorn zu beginnen.
    /// </summary>
    public bool AlleErneut { get; init; }
}

public sealed class Anreicherungsstatistik
{
    public int Gefragt { get; set; }
    public int Beantwortet { get; set; }
    public int Ergaenzt { get; set; }
    public int OhneAngaben { get; set; }
    public int FehlgeschlageneBuendel { get; set; }

    public int MitAllergenen { get; set; }
    public int MitZutaten { get; set; }
    public int MitAuszeichnungen { get; set; }
    public int MitNaehrwerten { get; set; }
    public int MitMenge { get; set; }

    public override string ToString() =>
        $"{Ergaenzt} von {Gefragt} Artikeln ergänzt "
        + $"(Allergene {MitAllergenen}, Zutaten {MitZutaten}, Auszeichnungen {MitAuszeichnungen}, "
        + $"Nährwerte {MitNaehrwerten}, Menge {MitMenge}); "
        + $"{OhneAngaben} ohne Angaben, {FehlgeschlageneBuendel} Bündel fehlgeschlagen";
}

/// <summary>
/// Traegt zu bereits bekannten Artikeln nach, was im Laden als Auskunft gebraucht wird:
/// Allergene, Zutaten, Auszeichnungen, Naehrwerte, Menge.
///
/// <para>Der Katalog steht schon — gefragt wird deshalb nicht nach Warengruppen oder Marken,
/// sondern gezielt nach den vorhandenen Barcodes. Das ist genauer (es trifft genau die
/// Artikel, die die App kennt) und sparsamer: statt 19.000 Einzelabfragen sind es
/// zweihundert Buendel zu hundert.</para>
///
/// <para>Der Lauf ist fortsetzbar und wiederholbar. Ohne <c>--alle</c> werden nur Artikel
/// gefragt, zu denen noch nichts vorliegt; bricht der Lauf ab, macht der naechste dort
/// weiter.</para>
/// </summary>
public sealed class Anreicherer(
    ArtikelFinderDbContext db,
    IOffClient client,
    ILogger<Anreicherer> log)
{
    public async Task<Anreicherungsstatistik> AusfuehrenAsync(
        AnreicherungsEinstellungen einstellungen,
        CancellationToken ct)
    {
        var statistik = new Anreicherungsstatistik();

        var offen = db.Artikel.Where(a => a.Ean != null);

        if (!einstellungen.AlleErneut)
        {
            offen = offen.Where(a =>
                a.Allergene == null && a.Zutaten == null && a.Auszeichnungen == null
                && a.Naehrwerte == null && a.Nutriscore == null && a.Menge == null);
        }

        var eans = await offen
            .OrderBy(a => a.Ean)
            .Select(a => a.Ean!)
            .ToListAsync(ct);

        if (einstellungen.Hoechstzahl > 0 && eans.Count > einstellungen.Hoechstzahl)
        {
            eans = eans.Take(einstellungen.Hoechstzahl).ToList();
        }

        if (eans.Count == 0)
        {
            log.LogInformation("Nichts zu tun — alle Artikel haben bereits Angaben.");
            return statistik;
        }

        var buendel = (int)Math.Ceiling(eans.Count / (double)einstellungen.Buendelgroesse);
        log.LogInformation(
            "Frage {Anzahl} Artikel in {Buendel} Bündeln ab.", eans.Count, buendel);

        var nummer = 0;

        foreach (var teil in eans.Chunk(einstellungen.Buendelgroesse))
        {
            ct.ThrowIfCancellationRequested();
            nummer++;
            statistik.Gefragt += teil.Length;

            var ergebnis = await client.AngabenAsync(teil, ct);

            if (ergebnis.Antwort is null)
            {
                statistik.FehlgeschlageneBuendel++;
                log.LogWarning("Bündel {Nummer} von {Gesamt} übersprungen.", nummer, buendel);
                continue;
            }

            await UebernehmenAsync(ergebnis.Antwort.Produkte, statistik, ct);

            if (nummer % 10 == 0 || nummer == buendel)
            {
                log.LogInformation(
                    "Bündel {Nummer}/{Gesamt} — {Ergaenzt} Artikel ergänzt.",
                    nummer, buendel, statistik.Ergaenzt);
            }

            if (einstellungen.Pause > TimeSpan.Zero && nummer < buendel)
            {
                await Task.Delay(einstellungen.Pause, ct);
            }
        }

        log.LogInformation("Fertig. {Statistik}", statistik);
        return statistik;
    }

    private async Task UebernehmenAsync(
        List<OffProdukt> produkte,
        Anreicherungsstatistik statistik,
        CancellationToken ct)
    {
        var nachEan = new Dictionary<string, Produktangaben>();

        foreach (var produkt in produkte)
        {
            var ean = Ean.Normalisieren(produkt.Code);
            if (ean is null)
            {
                continue;
            }

            statistik.Beantwortet++;
            var angaben = produkt.Angaben();

            if (angaben.IstLeer)
            {
                statistik.OhneAngaben++;
                continue;
            }

            nachEan[ean] = angaben;
        }

        if (nachEan.Count == 0)
        {
            return;
        }

        var codes = nachEan.Keys.ToList();
        var artikel = await db.Artikel
            .Where(a => a.Ean != null && codes.Contains(a.Ean))
            .ToListAsync(ct);

        foreach (var eintrag in artikel)
        {
            if (!nachEan.TryGetValue(eintrag.Ean!, out var angaben))
            {
                continue;
            }

            // Nur setzen, was auch da ist: ein zweiter Lauf soll bereits Vorhandenes nicht
            // durch eine zwischenzeitlich luecken­hafte Antwort ueberschreiben.
            eintrag.Menge = angaben.Menge ?? eintrag.Menge;
            eintrag.Allergene = angaben.Allergene ?? eintrag.Allergene;
            eintrag.Spuren = angaben.Spuren ?? eintrag.Spuren;
            eintrag.Auszeichnungen = angaben.Auszeichnungen ?? eintrag.Auszeichnungen;
            eintrag.Naehrwerte = angaben.Naehrwerte ?? eintrag.Naehrwerte;
            eintrag.Nutriscore = angaben.Nutriscore ?? eintrag.Nutriscore;
            eintrag.Zutaten = angaben.Zutaten ?? eintrag.Zutaten;

            statistik.Ergaenzt++;
            if (angaben.Allergene is not null) statistik.MitAllergenen++;
            if (angaben.Zutaten is not null) statistik.MitZutaten++;
            if (angaben.Auszeichnungen is not null) statistik.MitAuszeichnungen++;
            if (angaben.Naehrwerte is not null) statistik.MitNaehrwerten++;
            if (angaben.Menge is not null) statistik.MitMenge++;
        }

        await db.SaveChangesAsync(ct);
        db.ChangeTracker.Clear();
    }
}
