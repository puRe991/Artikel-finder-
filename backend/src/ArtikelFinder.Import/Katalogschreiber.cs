using ArtikelFinder.Api.Data;
using ArtikelFinder.Api.Entities;
using ArtikelFinder.Shared.Enums;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Logging;

namespace ArtikelFinder.Import;

/// <summary>Ein Artikel, so wie ihn eine Importquelle liefert.</summary>
public sealed record Rohartikel(string Name, string? Marke, string Ean, string? BildUrl, string? ZielKategorie);

/// <summary>
/// Schreibt importierte Artikel in den Katalog.
///
/// Kernregel: <b>Nutzerdaten gewinnen immer.</b> Ein Artikel, den du im Markt selbst
/// angelegt hast, wird von einem spaeteren Import nie ueberschrieben — sonst raeumt ein
/// Nachtlauf die Arbeit eines Einkaufs wieder weg. Bei importierten Artikeln werden nur
/// leere Felder aufgefuellt.
/// </summary>
public sealed class Katalogschreiber(ArtikelFinderDbContext db, ILogger<Katalogschreiber> log)
{
    private Dictionary<string, int>? _kategorieIds;

    public async Task<Importstatistik> SchreibenAsync(
        IReadOnlyList<Rohartikel> artikel,
        CancellationToken ct)
    {
        var statistik = new Importstatistik { Gelesen = artikel.Count };
        if (artikel.Count == 0)
        {
            return statistik;
        }

        var kategorien = await KategorieIdsAsync(ct);

        // Innerhalb eines Stapels kann dieselbe EAN mehrfach vorkommen (OFF hat Dubletten
        // ueber Laendervarianten). Der erste Treffer gewinnt.
        var eindeutig = artikel
            .GroupBy(a => a.Ean, StringComparer.Ordinal)
            .Select(g => g.First())
            .ToList();

        statistik.Uebersprungen += artikel.Count - eindeutig.Count;

        var eans = eindeutig.Select(a => a.Ean).ToList();
        var vorhandene = await db.Artikel
            .Where(a => a.Ean != null && eans.Contains(a.Ean))
            .ToDictionaryAsync(a => a.Ean!, ct);

        var jetzt = DateTimeOffset.UtcNow;

        foreach (var roh in eindeutig)
        {
            int? kategorieId = roh.ZielKategorie is not null
                && kategorien.TryGetValue(roh.ZielKategorie, out var id) ? id : null;

            if (!vorhandene.TryGetValue(roh.Ean, out var bestehend))
            {
                db.Artikel.Add(new Artikel
                {
                    Id = Guid.NewGuid(),
                    Name = Kuerzen(roh.Name, 300)!,
                    Marke = Kuerzen(roh.Marke, 120),
                    Ean = roh.Ean,
                    KategorieId = kategorieId,
                    BildUrl = Kuerzen(roh.BildUrl, 1000),
                    ErstelltVon = Erstellerquelle.Import,
                    ErstelltAm = jetzt,
                });

                statistik.Neu++;
                continue;
            }

            if (bestehend.ErstelltVon == Erstellerquelle.Nutzer)
            {
                statistik.Geschuetzt++;
                continue;
            }

            var geaendert = false;

            // Nur Luecken fuellen: ein bereits importierter Name kann von dir korrigiert
            // worden sein, auch wenn der Artikel selbst aus dem Import stammt.
            if (string.IsNullOrWhiteSpace(bestehend.Marke) && !string.IsNullOrWhiteSpace(roh.Marke))
            {
                bestehend.Marke = Kuerzen(roh.Marke, 120);
                geaendert = true;
            }

            if (string.IsNullOrWhiteSpace(bestehend.BildUrl) && !string.IsNullOrWhiteSpace(roh.BildUrl))
            {
                bestehend.BildUrl = Kuerzen(roh.BildUrl, 1000);
                geaendert = true;
            }

            if (bestehend.KategorieId is null && kategorieId is not null)
            {
                bestehend.KategorieId = kategorieId;
                geaendert = true;
            }

            if (geaendert)
            {
                bestehend.GeaendertAm = jetzt;
                statistik.Ergaenzt++;
            }
            else
            {
                statistik.Uebersprungen++;
            }
        }

        await db.SaveChangesAsync(ct);

        // Den Change-Tracker leeren, sonst waechst er ueber einen langen Lauf unbegrenzt.
        db.ChangeTracker.Clear();

        log.LogDebug("Stapel geschrieben: {Statistik}", statistik);
        return statistik;
    }

    private async Task<Dictionary<string, int>> KategorieIdsAsync(CancellationToken ct)
    {
        // Bewusst nach Name statt nach Pfad: die Zielkategorien im Importziel sind
        // eindeutige Blattnamen im Startraster.
        _kategorieIds ??= await db.Kategorien
            .AsNoTracking()
            .GroupBy(k => k.Name)
            .ToDictionaryAsync(g => g.Key, g => g.Min(k => k.Id), ct);

        return _kategorieIds;
    }

    private static string? Kuerzen(string? wert, int maximum)
    {
        if (string.IsNullOrWhiteSpace(wert))
        {
            return null;
        }

        var getrimmt = wert.Trim();
        return getrimmt.Length <= maximum ? getrimmt : getrimmt[..maximum];
    }
}
