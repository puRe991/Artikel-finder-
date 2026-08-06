using ArtikelFinder.Api.Data;
using ArtikelFinder.Api.Entities;
using ArtikelFinder.Shared.Dtos;
using Microsoft.EntityFrameworkCore;

namespace ArtikelFinder.Api.Services;

/// <summary>
/// Der Kategorienbaum ist klein (einige hundert Knoten) und aendert sich selten, deshalb
/// wird er komplett geladen statt rekursiv abgefragt. Das haelt die Logik auf SQLite und
/// PostgreSQL identisch — rekursive CTEs schreiben sich sonst pro Provider anders.
/// </summary>
public sealed class KategorieService(ArtikelFinderDbContext db)
{
    public async Task<IReadOnlyList<KategorieDto>> AlleAsync(CancellationToken ct = default)
    {
        var alle = await LadenAsync(ct);
        return [.. alle.Values
            .Select(k => ZuDto(k, alle))
            .OrderBy(k => k.Pfad, StringComparer.OrdinalIgnoreCase)];
    }

    public async Task<bool> ExistiertAsync(int id, CancellationToken ct = default) =>
        await db.Kategorien.AnyAsync(k => k.Id == id, ct);

    /// <summary>Die Kategorie selbst plus alle Nachfahren. Damit findet ein Filter auf
    /// "Molkereiprodukte" auch die Artikel unter "Joghurt".</summary>
    public async Task<IReadOnlyList<int>> ZweigIdsAsync(int wurzelId, CancellationToken ct = default)
    {
        var alle = await LadenAsync(ct);
        if (!alle.ContainsKey(wurzelId))
        {
            return [];
        }

        var kinder = alle.Values
            .Where(k => k.ParentKategorieId.HasValue)
            .GroupBy(k => k.ParentKategorieId!.Value)
            .ToDictionary(g => g.Key, g => g.Select(k => k.Id).ToList());

        var ergebnis = new List<int>();
        var offen = new Queue<int>([wurzelId]);
        var gesehen = new HashSet<int>();

        while (offen.Count > 0)
        {
            var aktuell = offen.Dequeue();
            if (!gesehen.Add(aktuell))
            {
                continue; // Schuetzt gegen einen versehentlich eingebauten Zyklus.
            }

            ergebnis.Add(aktuell);

            if (kinder.TryGetValue(aktuell, out var naechste))
            {
                foreach (var kind in naechste)
                {
                    offen.Enqueue(kind);
                }
            }
        }

        return ergebnis;
    }

    public async Task<Ergebnis<KategorieDto>> AnlegenAsync(KategorieAnlegenDto eingabe, CancellationToken ct = default)
    {
        var name = eingabe.Name.Trim();

        if (eingabe.ParentKategorieId is { } parentId && !await ExistiertAsync(parentId, ct))
        {
            return Ergebnis<KategorieDto>.Ungueltig($"Elternkategorie {parentId} existiert nicht.");
        }

        var vorhanden = await db.Kategorien
            .FirstOrDefaultAsync(k => k.ParentKategorieId == eingabe.ParentKategorieId && k.Name == name, ct);

        if (vorhanden is not null)
        {
            return Ergebnis<KategorieDto>.Konflikt($"Es gibt unterhalb dieses Knotens bereits eine Kategorie \"{name}\".");
        }

        var kategorie = new Kategorie { Name = name, ParentKategorieId = eingabe.ParentKategorieId };
        db.Kategorien.Add(kategorie);
        await db.SaveChangesAsync(ct);

        var alle = await LadenAsync(ct, neuLaden: true);
        return Ergebnis<KategorieDto>.Erfolg(ZuDto(kategorie, alle));
    }

    private Dictionary<int, Kategorie>? _zwischenspeicher;

    private async Task<Dictionary<int, Kategorie>> LadenAsync(CancellationToken ct, bool neuLaden = false)
    {
        // Nur fuer die Dauer eines Requests gecacht (Scoped-Service) — kein Invalidierungsproblem.
        if (_zwischenspeicher is null || neuLaden)
        {
            _zwischenspeicher = await db.Kategorien.AsNoTracking().ToDictionaryAsync(k => k.Id, ct);
        }

        return _zwischenspeicher;
    }

    private static KategorieDto ZuDto(Kategorie kategorie, IReadOnlyDictionary<int, Kategorie> alle) => new()
    {
        Id = kategorie.Id,
        Name = kategorie.Name,
        ParentKategorieId = kategorie.ParentKategorieId,
        Pfad = PfadBauen(kategorie, alle),
    };

    private static string PfadBauen(Kategorie kategorie, IReadOnlyDictionary<int, Kategorie> alle)
    {
        var teile = new List<string>();
        var aktuell = kategorie;
        var tiefe = 0;

        while (aktuell is not null && tiefe++ < 20)
        {
            teile.Add(aktuell.Name);
            aktuell = aktuell.ParentKategorieId is { } parentId && alle.TryGetValue(parentId, out var parent)
                ? parent
                : null;
        }

        teile.Reverse();
        return string.Join(" > ", teile);
    }
}
