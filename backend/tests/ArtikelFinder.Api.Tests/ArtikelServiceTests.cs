using ArtikelFinder.Api.Data;
using ArtikelFinder.Api.Entities;
using ArtikelFinder.Api.Infrastructure;
using ArtikelFinder.Api.Services;
using ArtikelFinder.Shared.Dtos;
using ArtikelFinder.Shared.Enums;
using Microsoft.Data.Sqlite;
using Microsoft.EntityFrameworkCore;
using Xunit;

namespace ArtikelFinder.Api.Tests;

/// <summary>
/// Laeuft gegen echtes SQLite (In-Memory), nicht gegen den InMemory-Provider: die
/// interessanten Fehler dieses Projekts — Sortierung von DateTimeOffset, partielle
/// Indizes, LIKE-Verhalten — treten nur beim echten Provider auf.
/// </summary>
public sealed class ArtikelServiceTests : IAsyncLifetime
{
    private const int MarktId = 1;

    private SqliteConnection _verbindung = null!;
    private ArtikelFinderDbContext _db = null!;
    private ArtikelService _service = null!;
    private TestZeitgeber _zeit = null!;

    public async Task InitializeAsync()
    {
        _verbindung = new SqliteConnection("Filename=:memory:");
        await _verbindung.OpenAsync();

        var optionen = new DbContextOptionsBuilder<ArtikelFinderDbContext>()
            .UseSqlite(_verbindung)
            .Options;

        _db = new ArtikelFinderDbContext(optionen);
        await _db.Database.EnsureCreatedAsync();
        await Startdaten.AnwendenAsync(_db);

        _zeit = new TestZeitgeber(new DateTimeOffset(2026, 8, 6, 10, 0, 0, TimeSpan.Zero));
        _service = new ArtikelService(_db, new KategorieService(_db), _zeit);
    }

    public async Task DisposeAsync()
    {
        await _db.DisposeAsync();
        await _verbindung.DisposeAsync();
    }

    [Fact]
    public async Task AnlegenMitPreisUndStandort_LegtAllesInEinemAufrufAn()
    {
        var ergebnis = await Anlegen("Bio Vollmilch", ean: "4008400202990", preis: 1.49m, gang: "7");

        Assert.True(ergebnis.IstErfolg);
        var artikel = ergebnis.Wert!;
        Assert.Single(artikel.Preise);
        Assert.Single(artikel.Standorte);
        Assert.Equal(Erstellerquelle.Nutzer, artikel.ErstelltVon);
        Assert.Equal("7", artikel.Standorte[0].Gang);
    }

    [Fact]
    public async Task Anlegen_MitBereitsVergebenerEan_MeldetKonflikt()
    {
        await Anlegen("Bio Vollmilch", ean: "4008400202990");

        var zweiter = await Anlegen("Andere Milch", ean: "4008400202990");

        Assert.Equal(Fehlerart.Konflikt, zweiter.Fehlerart);
    }

    [Fact]
    public async Task Anlegen_OhneEan_IstMehrfachErlaubt()
    {
        // Der Unique-Index auf EAN ist gefiltert — lose Ware hat keinen Barcode.
        Assert.True((await Anlegen("Brötchen")).IstErfolg);
        Assert.True((await Anlegen("Brezel")).IstErfolg);
    }

    [Fact]
    public async Task Suche_FindetUeberMarkeUndIgnoriertUmlautSchreibweise()
    {
        await Anlegen("Vollmilch 1l", marke: "Bärenmarke");

        foreach (var begriff in new[] { "bärenmarke", "baerenmarke", "barenmarke", "BÄRENMARKE" })
        {
            var treffer = await _service.SuchenAsync(new ArtikelSuchfilter { Suchbegriff = begriff, MarktId = MarktId });
            Assert.True(treffer.GesamtAnzahl == 1, $"'{begriff}' fand {treffer.GesamtAnzahl} Artikel.");
        }
    }

    [Fact]
    public async Task Suche_VerknuepftMehrereBegriffeMitUnd()
    {
        await Anlegen("Bio Vollmilch");
        await Anlegen("Bio Joghurt");

        var treffer = await _service.SuchenAsync(
            new ArtikelSuchfilter { Suchbegriff = "bio vollmilch", MarktId = MarktId });

        Assert.Equal(1, treffer.GesamtAnzahl);
        Assert.Equal("Bio Vollmilch", treffer.Eintraege[0].Name);
    }

    [Fact]
    public async Task Suche_LiefertJuengstenPreisJeArtikel()
    {
        var artikel = (await Anlegen("Vollmilch", preis: 1.49m)).Wert!;

        _zeit.Vorspulen(TimeSpan.FromDays(7));
        await _service.PreisErfassenAsync(artikel.Id, new PreisErfassenDto { Preis = 1.59m }, MarktId);

        var treffer = await _service.SuchenAsync(new ArtikelSuchfilter { MarktId = MarktId });

        Assert.Equal(1.59m, treffer.Eintraege[0].AktuellerPreis!.Preis);
    }

    [Fact]
    public async Task Werbepreis_GiltNurImGueltigkeitszeitraum()
    {
        var artikel = (await Anlegen("Vollmilch")).Wert!;

        await _service.PreisErfassenAsync(artikel.Id, new PreisErfassenDto
        {
            Preis = 1.49m,
            Werbepreis = 1.29m,
            WerbepreisGueltigVon = _zeit.Jetzt,
            WerbepreisGueltigBis = _zeit.Jetzt.AddDays(3),
        }, MarktId);

        var waehrendAktion = await _service.HolenAsync(artikel.Id);
        Assert.True(waehrendAktion!.Preise[0].WerbepreisAktiv);
        Assert.Equal(1.29m, waehrendAktion.Preise[0].GueltigerPreis);

        _zeit.Vorspulen(TimeSpan.FromDays(5));

        var nachAktion = await _service.HolenAsync(artikel.Id);
        Assert.False(nachAktion!.Preise[0].WerbepreisAktiv);
        Assert.Equal(1.49m, nachAktion.Preise[0].GueltigerPreis);
    }

    [Fact]
    public async Task NurMitWerbepreis_FiltertAbgelaufeneAktionenAus()
    {
        var artikel = (await Anlegen("Vollmilch")).Wert!;
        await _service.PreisErfassenAsync(artikel.Id, new PreisErfassenDto
        {
            Preis = 1.49m,
            Werbepreis = 1.29m,
            WerbepreisGueltigBis = _zeit.Jetzt.AddDays(2),
        }, MarktId);

        var filter = new ArtikelSuchfilter { MarktId = MarktId, NurMitWerbepreis = true };
        Assert.Equal(1, (await _service.SuchenAsync(filter)).GesamtAnzahl);

        _zeit.Vorspulen(TimeSpan.FromDays(4));
        Assert.Equal(0, (await _service.SuchenAsync(filter)).GesamtAnzahl);
    }

    [Fact]
    public async Task Umraeumen_EntferntArtikelAusDemAltenGang()
    {
        var artikel = (await Anlegen("Vollmilch", gang: "7")).Wert!;

        _zeit.Vorspulen(TimeSpan.FromDays(1));
        await _service.StandortErfassenAsync(artikel.Id, new StandortErfassenDto { Gang = "3" }, MarktId);

        Assert.Empty(await _service.NachGangAsync(MarktId, "7"));
        Assert.Single(await _service.NachGangAsync(MarktId, "3"));

        var gaenge = await _service.GaengeAsync(MarktId);
        Assert.Equal(["3"], gaenge.Select(g => g.Gang));
    }

    [Fact]
    public async Task Gaenge_SortiertNumerischNichtAlphabetisch()
    {
        await Anlegen("A", gang: "2");
        await Anlegen("B", gang: "10");
        await Anlegen("C", gang: "1");

        var gaenge = await _service.GaengeAsync(MarktId);

        Assert.Equal(["1", "2", "10"], gaenge.Select(g => g.Gang));
    }

    [Fact]
    public async Task Verlauf_ProtokolliertWerWasWannGeaendertHat()
    {
        var artikel = (await Anlegen("Vollmilch", preis: 1.49m, erfasstVon: "tobias")).Wert!;

        _zeit.Vorspulen(TimeSpan.FromDays(1));
        await _service.PreisErfassenAsync(
            artikel.Id, new PreisErfassenDto { Preis = 1.59m, ErfasstVon = "tobias" }, MarktId);

        var verlauf = (await _service.VerlaufAsync(artikel.Id)).Wert!;

        Assert.Equal(3, verlauf.Count); // Artikel + Erstpreis + neuer Preis
        Assert.Equal("Preis 1,49 -> 1,59 EUR", verlauf[0].Beschreibung);
        Assert.All(verlauf, e => Assert.Equal("tobias", e.GeaendertVon));
    }

    [Fact]
    public async Task Kategoriefilter_SchliesstUnterkategorienEin()
    {
        var molkerei = await _db.Kategorien.FirstAsync(k => k.Name == "Molkereiprodukte");
        var milch = await _db.Kategorien.FirstAsync(k => k.Name == "Milch");

        await Anlegen("Vollmilch", kategorieId: milch.Id);

        var treffer = await _service.SuchenAsync(
            new ArtikelSuchfilter { KategorieId = molkerei.Id, MarktId = MarktId });

        Assert.Equal(1, treffer.GesamtAnzahl);
    }

    [Fact]
    public async Task PerEan_FindetArtikelUnabhaengigVonSchreibweise()
    {
        await Anlegen("Vollmilch", ean: "4008400202990");

        Assert.NotNull(await _service.PerEanHolenAsync("4008-400-202990"));
        Assert.Null(await _service.PerEanHolenAsync("4000000000000"));
    }

    [Fact]
    public async Task PreisErfassen_FuerUnbekanntenArtikel_MeldetNichtGefunden()
    {
        var ergebnis = await _service.PreisErfassenAsync(
            Guid.NewGuid(), new PreisErfassenDto { Preis = 1m }, MarktId);

        Assert.Equal(Fehlerart.NichtGefunden, ergebnis.Fehlerart);
    }

    [Fact]
    public async Task PreisErfassen_FuerUnbekanntenMarkt_MeldetUngueltig()
    {
        var artikel = (await Anlegen("Vollmilch")).Wert!;

        var ergebnis = await _service.PreisErfassenAsync(
            artikel.Id, new PreisErfassenDto { Preis = 1m, MarktId = 999 }, MarktId);

        Assert.Equal(Fehlerart.Ungueltig, ergebnis.Fehlerart);
    }

    [Fact]
    public async Task Loeschen_RaeumtPreiseStandorteUndVerlaufMitAb()
    {
        var artikel = (await Anlegen("Vollmilch", preis: 1.49m, gang: "7")).Wert!;

        Assert.True((await _service.LoeschenAsync(artikel.Id)).IstErfolg);

        Assert.Empty(await _db.Preise.Where(p => p.ArtikelId == artikel.Id).ToListAsync());
        Assert.Empty(await _db.Standorte.Where(s => s.ArtikelId == artikel.Id).ToListAsync());
        Assert.Empty(await _db.Verlauf.Where(v => v.ArtikelId == artikel.Id).ToListAsync());
    }

    [Fact]
    public async Task Suche_BlaettertStabil()
    {
        for (var i = 0; i < 12; i++)
        {
            await Anlegen($"Artikel {i:00}");
        }

        var seite1 = await _service.SuchenAsync(
            new ArtikelSuchfilter { MarktId = MarktId, Seite = 1, Seitengroesse = 5 });
        var seite3 = await _service.SuchenAsync(
            new ArtikelSuchfilter { MarktId = MarktId, Seite = 3, Seitengroesse = 5 });

        Assert.Equal(12, seite1.GesamtAnzahl);
        Assert.Equal(3, seite1.Seitenanzahl);
        Assert.True(seite1.HatWeitere);
        Assert.Equal(2, seite3.Eintraege.Count);
        Assert.False(seite3.HatWeitere);
        Assert.Empty(seite1.Eintraege.Select(e => e.Id).Intersect(seite3.Eintraege.Select(e => e.Id)));
    }

    private Task<Ergebnis<ArtikelDetailDto>> Anlegen(
        string name,
        string? marke = null,
        string? ean = null,
        int? kategorieId = null,
        decimal? preis = null,
        string? gang = null,
        string? erfasstVon = null) =>
        _service.AnlegenAsync(
            new ArtikelAnlegenDto
            {
                Name = name,
                Marke = marke,
                Ean = ean,
                KategorieId = kategorieId,
                Preis = preis is null ? null : new PreisErfassenDto { Preis = preis.Value, ErfasstVon = erfasstVon },
                Standort = gang is null ? null : new StandortErfassenDto { Gang = gang, ErfasstVon = erfasstVon },
            },
            MarktId);

    private sealed class TestZeitgeber(DateTimeOffset start) : IZeitgeber
    {
        public DateTimeOffset Jetzt { get; private set; } = start;

        public void Vorspulen(TimeSpan spanne) => Jetzt += spanne;
    }
}
