using ArtikelFinder.Api.Data;
using ArtikelFinder.Api.Entities;
using ArtikelFinder.Api.Infrastructure;
using ArtikelFinder.Shared;
using ArtikelFinder.Shared.Dtos;
using ArtikelFinder.Shared.Enums;
using Microsoft.EntityFrameworkCore;

namespace ArtikelFinder.Api.Services;

public sealed class ArtikelService(
    ArtikelFinderDbContext db,
    KategorieService kategorien,
    IZeitgeber zeitgeber)
{
    public async Task<SeitenErgebnis<ArtikelListeDto>> SuchenAsync(
        ArtikelSuchfilter filter,
        CancellationToken ct = default)
    {
        var jetzt = zeitgeber.Jetzt;
        var abfrage = db.Artikel.AsNoTracking();

        var ean = Ean.Normalisieren(filter.Ean);
        if (ean is not null)
        {
            // EAN-Lookup ist eine Punktabfrage — alle anderen Filter waeren nur Rauschen.
            abfrage = abfrage.Where(a => a.Ean == ean);
        }
        else
        {
            if (filter.KategorieId is { } kategorieId)
            {
                var zweig = await kategorien.ZweigIdsAsync(kategorieId, ct);
                abfrage = abfrage.Where(a => a.KategorieId != null && zweig.Contains(a.KategorieId.Value));
            }

            // Jedes Token muss treffen (UND-Verknuepfung): "milch bio" findet "Bio Vollmilch".
            // Die Normalisierung entfernt alle Sonderzeichen, deshalb koennen die Tokens
            // keine LIKE-Wildcards mehr enthalten.
            foreach (var token in Tokenisieren(filter.Suchbegriff))
            {
                var muster = $"%{token}%";
                abfrage = abfrage.Where(a => EF.Functions.Like(a.SuchText, muster));
            }
        }

        if (filter.NurMitStandort)
        {
            abfrage = abfrage.Where(a => a.Standorte.Any(s => s.MarktId == filter.MarktId));
        }

        if (filter.NurMitWerbepreis)
        {
            abfrage = abfrage.Where(a => a.Preise
                .Where(p => p.MarktId == filter.MarktId)
                .OrderByDescending(p => p.ErfasstAm)
                .Take(1)
                .Any(p => p.Werbepreis != null
                    && (p.WerbepreisGueltigVon == null || p.WerbepreisGueltigVon <= jetzt)
                    && (p.WerbepreisGueltigBis == null || p.WerbepreisGueltigBis >= jetzt)));
        }

        var gesamt = await abfrage.CountAsync(ct);
        if (gesamt == 0)
        {
            return SeitenErgebnis<ArtikelListeDto>.Leer(filter.Seite, filter.Seitengroesse);
        }

        var rohdaten = await abfrage
            .OrderBy(a => a.Name).ThenBy(a => a.Id)
            .Skip((filter.Seite - 1) * filter.Seitengroesse)
            .Take(filter.Seitengroesse)
            .Select(a => new
            {
                a.Id,
                a.Name,
                a.Marke,
                a.Ean,
                a.Artikelnummer,
                a.KategorieId,
                KategorieName = a.Kategorie != null ? a.Kategorie.Name : null,
                a.BildUrl,
                LetzterPreis = a.Preise
                    .Where(p => p.MarktId == filter.MarktId)
                    .OrderByDescending(p => p.ErfasstAm)
                    .FirstOrDefault(),
                LetzterStandort = a.Standorte
                    .Where(s => s.MarktId == filter.MarktId)
                    .OrderByDescending(s => s.ErfasstAm)
                    .FirstOrDefault(),
            })
            .ToListAsync(ct);

        return new SeitenErgebnis<ArtikelListeDto>
        {
            Eintraege = [.. rohdaten.Select(r => new ArtikelListeDto
            {
                Id = r.Id,
                Name = r.Name,
                Marke = r.Marke,
                Ean = r.Ean,
                Artikelnummer = r.Artikelnummer,
                KategorieId = r.KategorieId,
                KategorieName = r.KategorieName,
                BildUrl = r.BildUrl,
                AktuellerPreis = r.LetzterPreis?.ZuDto(jetzt),
                Standort = r.LetzterStandort?.ZuDto(),
            })],
            Seite = filter.Seite,
            Seitengroesse = filter.Seitengroesse,
            GesamtAnzahl = gesamt,
        };
    }

    public async Task<ArtikelDetailDto?> HolenAsync(Guid id, CancellationToken ct = default)
    {
        var artikel = await LadenMitDetailsAsync(a => a.Id == id, verfolgen: false, ct);
        return artikel?.ZuDetailDto(zeitgeber.Jetzt);
    }

    /// <summary>Barcode-Lookup. <c>null</c> bedeutet "unbekannter Artikel" — die App bietet
    /// dann das Anlegen an.</summary>
    public async Task<ArtikelDetailDto?> PerEanHolenAsync(string ean, CancellationToken ct = default)
    {
        var normalisiert = Ean.Normalisieren(ean);
        if (normalisiert is null)
        {
            return null;
        }

        var artikel = await LadenMitDetailsAsync(a => a.Ean == normalisiert, verfolgen: false, ct);
        return artikel?.ZuDetailDto(zeitgeber.Jetzt);
    }

    public async Task<Ergebnis<ArtikelDetailDto>> AnlegenAsync(
        ArtikelAnlegenDto eingabe,
        int standardMarktId,
        CancellationToken ct = default)
    {
        var ean = Ean.Normalisieren(eingabe.Ean);
        if (ean is not null && await db.Artikel.AnyAsync(a => a.Ean == ean, ct))
        {
            return Ergebnis<ArtikelDetailDto>.Konflikt(
                $"Zur EAN {ean} existiert bereits ein Artikel. Ergaenze ihn, statt einen zweiten anzulegen.");
        }

        if (eingabe.KategorieId is { } kategorieId && !await kategorien.ExistiertAsync(kategorieId, ct))
        {
            return Ergebnis<ArtikelDetailDto>.Ungueltig($"Kategorie {kategorieId} existiert nicht.");
        }

        var jetzt = zeitgeber.Jetzt;
        var artikel = new Artikel
        {
            Id = Guid.NewGuid(),
            Name = eingabe.Name.Trim(),
            Marke = Leerzustand(eingabe.Marke),
            Ean = ean,
            Artikelnummer = Leerzustand(eingabe.Artikelnummer),
            KategorieId = eingabe.KategorieId,
            BildUrl = Leerzustand(eingabe.BildUrl),
            ErstelltVon = Erstellerquelle.Nutzer,
            ErstelltAm = jetzt,
        };

        db.Artikel.Add(artikel);
        Protokollieren(artikel.Id, "Artikel", Aenderungsart.Angelegt, $"Artikel \"{artikel.Name}\" angelegt.",
            eingabe.Preis?.ErfasstVon ?? eingabe.Standort?.ErfasstVon, jetzt);

        // Scannen, Preis und Regal in einem Rutsch — das ist der Ablauf im Markt.
        if (eingabe.Preis is { } preisEingabe)
        {
            var marktPruefung = await MarktIdAufloesenAsync(preisEingabe.MarktId, standardMarktId, ct);
            if (!marktPruefung.IstErfolg)
            {
                return Ergebnis<ArtikelDetailDto>.Ungueltig(marktPruefung.Meldung!);
            }

            db.Preise.Add(PreisErzeugen(artikel.Id, marktPruefung.Wert, preisEingabe, jetzt));
            Protokollieren(artikel.Id, "Preis", Aenderungsart.Angelegt,
                PreisBeschreibung(null, preisEingabe.Preis, preisEingabe.Werbepreis), preisEingabe.ErfasstVon, jetzt);
        }

        if (eingabe.Standort is { } standortEingabe)
        {
            var marktPruefung = await MarktIdAufloesenAsync(standortEingabe.MarktId, standardMarktId, ct);
            if (!marktPruefung.IstErfolg)
            {
                return Ergebnis<ArtikelDetailDto>.Ungueltig(marktPruefung.Meldung!);
            }

            db.Standorte.Add(StandortErzeugen(artikel.Id, marktPruefung.Wert, standortEingabe, jetzt));
            Protokollieren(artikel.Id, "Standort", Aenderungsart.Angelegt,
                StandortBeschreibung(standortEingabe), standortEingabe.ErfasstVon, jetzt);
        }

        await db.SaveChangesAsync(ct);

        var gespeichert = await LadenMitDetailsAsync(a => a.Id == artikel.Id, verfolgen: false, ct);
        return Ergebnis<ArtikelDetailDto>.Erfolg(gespeichert!.ZuDetailDto(jetzt));
    }

    public async Task<Ergebnis<ArtikelDetailDto>> AendernAsync(
        Guid id,
        ArtikelAendernDto eingabe,
        string? geaendertVon,
        CancellationToken ct = default)
    {
        var artikel = await db.Artikel.FirstOrDefaultAsync(a => a.Id == id, ct);
        if (artikel is null)
        {
            return Ergebnis<ArtikelDetailDto>.NichtGefunden($"Artikel {id} existiert nicht.");
        }

        var ean = Ean.Normalisieren(eingabe.Ean);
        if (ean is not null && await db.Artikel.AnyAsync(a => a.Ean == ean && a.Id != id, ct))
        {
            return Ergebnis<ArtikelDetailDto>.Konflikt($"Die EAN {ean} gehoert bereits zu einem anderen Artikel.");
        }

        if (eingabe.KategorieId is { } kategorieId && !await kategorien.ExistiertAsync(kategorieId, ct))
        {
            return Ergebnis<ArtikelDetailDto>.Ungueltig($"Kategorie {kategorieId} existiert nicht.");
        }

        var aenderungen = new List<string>();
        Vergleichen(aenderungen, "Name", artikel.Name, eingabe.Name.Trim());
        Vergleichen(aenderungen, "Marke", artikel.Marke, Leerzustand(eingabe.Marke));
        Vergleichen(aenderungen, "EAN", artikel.Ean, ean);
        Vergleichen(aenderungen, "Artikelnummer", artikel.Artikelnummer, Leerzustand(eingabe.Artikelnummer));
        Vergleichen(aenderungen, "Kategorie", artikel.KategorieId?.ToString(), eingabe.KategorieId?.ToString());
        Vergleichen(aenderungen, "Bild", artikel.BildUrl, Leerzustand(eingabe.BildUrl));

        if (aenderungen.Count == 0)
        {
            var unveraendert = await LadenMitDetailsAsync(a => a.Id == id, verfolgen: false, ct);
            return Ergebnis<ArtikelDetailDto>.Erfolg(unveraendert!.ZuDetailDto(zeitgeber.Jetzt));
        }

        var jetzt = zeitgeber.Jetzt;
        artikel.Name = eingabe.Name.Trim();
        artikel.Marke = Leerzustand(eingabe.Marke);
        artikel.Ean = ean;
        artikel.Artikelnummer = Leerzustand(eingabe.Artikelnummer);
        artikel.KategorieId = eingabe.KategorieId;
        artikel.BildUrl = Leerzustand(eingabe.BildUrl);
        artikel.GeaendertAm = jetzt;

        Protokollieren(artikel.Id, "Artikel", Aenderungsart.Geaendert,
            string.Join("; ", aenderungen), geaendertVon, jetzt);

        await db.SaveChangesAsync(ct);

        var aktualisiert = await LadenMitDetailsAsync(a => a.Id == id, verfolgen: false, ct);
        return Ergebnis<ArtikelDetailDto>.Erfolg(aktualisiert!.ZuDetailDto(jetzt));
    }

    public async Task<Ergebnis<bool>> LoeschenAsync(Guid id, CancellationToken ct = default)
    {
        var artikel = await db.Artikel.FirstOrDefaultAsync(a => a.Id == id, ct);
        if (artikel is null)
        {
            return Ergebnis<bool>.NichtGefunden($"Artikel {id} existiert nicht.");
        }

        // Preise, Standorte und Verlauf haengen per Cascade dran.
        db.Artikel.Remove(artikel);
        await db.SaveChangesAsync(ct);
        return Ergebnis<bool>.Erfolg(true);
    }

    public async Task<Ergebnis<PreisDto>> PreisErfassenAsync(
        Guid artikelId,
        PreisErfassenDto eingabe,
        int standardMarktId,
        CancellationToken ct = default)
    {
        if (!await db.Artikel.AnyAsync(a => a.Id == artikelId, ct))
        {
            return Ergebnis<PreisDto>.NichtGefunden($"Artikel {artikelId} existiert nicht.");
        }

        var marktPruefung = await MarktIdAufloesenAsync(eingabe.MarktId, standardMarktId, ct);
        if (!marktPruefung.IstErfolg)
        {
            return Ergebnis<PreisDto>.Ungueltig(marktPruefung.Meldung!);
        }

        var marktId = marktPruefung.Wert;
        var jetzt = zeitgeber.Jetzt;

        var vorheriger = await db.Preise
            .AsNoTracking()
            .Where(p => p.ArtikelId == artikelId && p.MarktId == marktId)
            .OrderByDescending(p => p.ErfasstAm)
            .FirstOrDefaultAsync(ct);

        var preis = PreisErzeugen(artikelId, marktId, eingabe, jetzt);
        db.Preise.Add(preis);

        Protokollieren(artikelId, "Preis", Aenderungsart.Angelegt,
            PreisBeschreibung(vorheriger?.Wert, eingabe.Preis, eingabe.Werbepreis), eingabe.ErfasstVon, jetzt);

        await db.SaveChangesAsync(ct);
        return Ergebnis<PreisDto>.Erfolg(preis.ZuDto(jetzt));
    }

    public async Task<Ergebnis<StandortDto>> StandortErfassenAsync(
        Guid artikelId,
        StandortErfassenDto eingabe,
        int standardMarktId,
        CancellationToken ct = default)
    {
        if (!await db.Artikel.AnyAsync(a => a.Id == artikelId, ct))
        {
            return Ergebnis<StandortDto>.NichtGefunden($"Artikel {artikelId} existiert nicht.");
        }

        var marktPruefung = await MarktIdAufloesenAsync(eingabe.MarktId, standardMarktId, ct);
        if (!marktPruefung.IstErfolg)
        {
            return Ergebnis<StandortDto>.Ungueltig(marktPruefung.Meldung!);
        }

        var jetzt = zeitgeber.Jetzt;
        var standort = StandortErzeugen(artikelId, marktPruefung.Wert, eingabe, jetzt);
        db.Standorte.Add(standort);

        Protokollieren(artikelId, "Standort", Aenderungsart.Angelegt,
            StandortBeschreibung(eingabe), eingabe.ErfasstVon, jetzt);

        await db.SaveChangesAsync(ct);
        return Ergebnis<StandortDto>.Erfolg(standort.ZuDto());
    }

    public async Task<Ergebnis<IReadOnlyList<VerlaufEintragDto>>> VerlaufAsync(
        Guid artikelId,
        CancellationToken ct = default)
    {
        if (!await db.Artikel.AnyAsync(a => a.Id == artikelId, ct))
        {
            return Ergebnis<IReadOnlyList<VerlaufEintragDto>>.NichtGefunden($"Artikel {artikelId} existiert nicht.");
        }

        var eintraege = await db.Verlauf
            .AsNoTracking()
            .Where(v => v.ArtikelId == artikelId)
            .OrderByDescending(v => v.GeaendertAm)
            .ThenByDescending(v => v.Id)
            .ToListAsync(ct);

        return Ergebnis<IReadOnlyList<VerlaufEintragDto>>.Erfolg([.. eintraege.Select(e => e.ZuDto())]);
    }

    /// <summary>Alle Artikel eines Gangs — die Listenansicht aus Kernfunktion 5.</summary>
    public async Task<IReadOnlyList<ArtikelListeDto>> NachGangAsync(
        int marktId,
        string gang,
        CancellationToken ct = default)
    {
        var jetzt = zeitgeber.Jetzt;

        var treffer = await AktuelleStandorte(marktId)
            .Where(s => s.Gang == gang)
            .Select(s => new
            {
                Standort = s,
                s.Artikel.Name,
                s.Artikel.Marke,
                s.Artikel.Ean,
                s.Artikel.Artikelnummer,
                s.Artikel.KategorieId,
                KategorieName = s.Artikel.Kategorie != null ? s.Artikel.Kategorie.Name : null,
                s.Artikel.BildUrl,
                LetzterPreis = s.Artikel.Preise
                    .Where(p => p.MarktId == marktId)
                    .OrderByDescending(p => p.ErfasstAm)
                    .FirstOrDefault(),
            })
            .OrderBy(x => x.Name)
            .ToListAsync(ct);

        return
        [
            .. treffer.Select(x => new ArtikelListeDto
            {
                Id = x.Standort.ArtikelId,
                Name = x.Name,
                Marke = x.Marke,
                Ean = x.Ean,
                Artikelnummer = x.Artikelnummer,
                KategorieId = x.KategorieId,
                KategorieName = x.KategorieName,
                BildUrl = x.BildUrl,
                AktuellerPreis = x.LetzterPreis?.ZuDto(jetzt),
                Standort = x.Standort.ZuDto(),
            }),
        ];
    }

    /// <summary>Belegte Gaenge eines Marktes mit Artikelzahl.</summary>
    public async Task<IReadOnlyList<GangDto>> GaengeAsync(int marktId, CancellationToken ct = default)
    {
        var gaenge = await AktuelleStandorte(marktId)
            .GroupBy(s => s.Gang)
            .Select(g => new GangDto { Gang = g.Key, AnzahlArtikel = g.Count() })
            .ToListAsync(ct);

        // "2" vor "10": numerische Gaenge sollen nicht alphabetisch sortiert werden.
        return
        [
            .. gaenge
                .OrderBy(g => int.TryParse(g.Gang, out var n) ? n : int.MaxValue)
                .ThenBy(g => g.Gang, StringComparer.OrdinalIgnoreCase),
        ];
    }

    /// <summary>
    /// Je Artikel nur die juengste Standorterfassung des Marktes. Umgeraeumte Artikel
    /// tauchen damit nicht mehr in ihrem alten Gang auf.
    /// </summary>
    private IQueryable<Standort> AktuelleStandorte(int marktId)
    {
        var imMarkt = db.Standorte.AsNoTracking().Where(s => s.MarktId == marktId);

        var juengste = imMarkt
            .GroupBy(s => s.ArtikelId)
            .Select(g => new { ArtikelId = g.Key, ErfasstAm = g.Max(s => s.ErfasstAm) });

        return from standort in imMarkt
               join neueste in juengste
                   on new { standort.ArtikelId, standort.ErfasstAm }
                   equals new { neueste.ArtikelId, neueste.ErfasstAm }
               select standort;
    }

    private async Task<Artikel?> LadenMitDetailsAsync(
        System.Linq.Expressions.Expression<Func<Artikel, bool>> bedingung,
        bool verfolgen,
        CancellationToken ct)
    {
        var abfrage = db.Artikel
            .Include(a => a.Kategorie)
            .Include(a => a.Preise)
            .Include(a => a.Standorte)
            .Where(bedingung);

        if (!verfolgen)
        {
            abfrage = abfrage.AsNoTracking();
        }

        return await abfrage.FirstOrDefaultAsync(ct);
    }

    private async Task<Ergebnis<int>> MarktIdAufloesenAsync(int? angefragt, int standard, CancellationToken ct)
    {
        var marktId = angefragt ?? standard;
        return await db.Maerkte.AnyAsync(m => m.Id == marktId, ct)
            ? Ergebnis<int>.Erfolg(marktId)
            : Ergebnis<int>.Ungueltig($"Markt {marktId} existiert nicht.");
    }

    private void Protokollieren(
        Guid artikelId,
        string entitaet,
        Aenderungsart art,
        string beschreibung,
        string? von,
        DateTimeOffset jetzt)
    {
        db.Verlauf.Add(new Verlaufseintrag
        {
            ArtikelId = artikelId,
            Entitaet = entitaet,
            Aenderungsart = art,
            Beschreibung = Kuerzen(beschreibung, 500),
            GeaendertVon = Leerzustand(von),
            GeaendertAm = jetzt,
        });
    }

    private static Preis PreisErzeugen(Guid artikelId, int marktId, PreisErfassenDto eingabe, DateTimeOffset jetzt) => new()
    {
        Id = Guid.NewGuid(),
        ArtikelId = artikelId,
        MarktId = marktId,
        Wert = eingabe.Preis,
        Werbepreis = eingabe.Werbepreis,
        WerbepreisGueltigVon = eingabe.WerbepreisGueltigVon?.ToUniversalTime(),
        WerbepreisGueltigBis = eingabe.WerbepreisGueltigBis?.ToUniversalTime(),
        ErfasstAm = jetzt,
        ErfasstVon = Leerzustand(eingabe.ErfasstVon),
    };

    private static Standort StandortErzeugen(Guid artikelId, int marktId, StandortErfassenDto eingabe, DateTimeOffset jetzt) => new()
    {
        Id = Guid.NewGuid(),
        ArtikelId = artikelId,
        MarktId = marktId,
        Gang = eingabe.Gang.Trim(),
        RegalBeschreibung = Leerzustand(eingabe.RegalBeschreibung),
        KartenX = eingabe.KartenX,
        KartenY = eingabe.KartenY,
        ErfasstAm = jetzt,
        ErfasstVon = Leerzustand(eingabe.ErfasstVon),
    };

    private static IEnumerable<string> Tokenisieren(string? suchbegriff) =>
        Suchtext.Normalisieren(suchbegriff)
            .Split(' ', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries)
            .Take(6); // Mehr Tokens bringen keine besseren Treffer, kosten aber je einen JOIN-freien LIKE.

    private static void Vergleichen(List<string> ziel, string feld, string? alt, string? neu)
    {
        if (!string.Equals(alt, neu, StringComparison.Ordinal))
        {
            ziel.Add($"{feld}: {alt ?? "—"} -> {neu ?? "—"}");
        }
    }

    /// <summary>Deutsche Schreibweise, weil der Verlauf unveraendert in der App angezeigt wird.</summary>
    private static readonly System.Globalization.CultureInfo Deutsch = new("de-DE");

    private static string PreisBeschreibung(decimal? alterPreis, decimal neuerPreis, decimal? werbepreis)
    {
        var text = alterPreis.HasValue
            ? string.Format(Deutsch, "Preis {0:0.00} -> {1:0.00} EUR", alterPreis.Value, neuerPreis)
            : string.Format(Deutsch, "Preis {0:0.00} EUR erfasst", neuerPreis);

        return werbepreis.HasValue
            ? string.Format(Deutsch, "{0}, Werbepreis {1:0.00} EUR", text, werbepreis.Value)
            : text;
    }

    private static string StandortBeschreibung(StandortErfassenDto eingabe) =>
        string.IsNullOrWhiteSpace(eingabe.RegalBeschreibung)
            ? $"Standort Gang {eingabe.Gang.Trim()}"
            : $"Standort Gang {eingabe.Gang.Trim()} ({eingabe.RegalBeschreibung.Trim()})";

    private static string? Leerzustand(string? wert) =>
        string.IsNullOrWhiteSpace(wert) ? null : wert.Trim();

    private static string Kuerzen(string wert, int maximum) =>
        wert.Length <= maximum ? wert : wert[..(maximum - 1)] + "…";
}
