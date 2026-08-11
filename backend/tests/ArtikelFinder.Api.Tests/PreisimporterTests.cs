using ArtikelFinder.Api.Data;
using ArtikelFinder.Api.Entities;
using ArtikelFinder.Import;
using ArtikelFinder.Import.OpenPrices;
using ArtikelFinder.Shared.Enums;
using Microsoft.Data.Sqlite;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Logging.Abstractions;
using Xunit;

namespace ArtikelFinder.Api.Tests;

/// <summary>
/// Der Preisimport ist der einzige Lauf, der etwas an bestehenden Artikeln ändert. Diese
/// Tests halten fest, was er anfassen darf und was nicht: der Richtpreis wird gesetzt und
/// bei einem zweiten Lauf abgelöst, selbst erfasste Preise bleiben unberührt.
/// </summary>
public sealed class PreisimporterTests : IAsyncLifetime
{
    private static readonly DateOnly Heute = DateOnly.FromDateTime(DateTime.UtcNow.Date);

    private SqliteConnection _verbindung = null!;
    private ArtikelFinderDbContext _db = null!;

    public async Task InitializeAsync()
    {
        _verbindung = new SqliteConnection("Filename=:memory:");
        await _verbindung.OpenAsync();

        _db = new ArtikelFinderDbContext(new DbContextOptionsBuilder<ArtikelFinderDbContext>()
            .UseSqlite(_verbindung).Options);

        await _db.Database.EnsureCreatedAsync();
        await Startdaten.AnwendenAsync(_db);
    }

    public async Task DisposeAsync()
    {
        await _db.DisposeAsync();
        await _verbindung.DisposeAsync();
    }

    [Fact]
    public async Task VorhandenerArtikel_BekommtDenMedianAllerLaeden()
    {
        await ArtikelAnlegenAsync("4045317058067", "Vollmilch 1 l");

        var client = new OpenPricesAttrappe();
        client.Standort(1, "Kaufland", Preis("4045317058067", 1.09m), Preis("4045317058067", 1.29m));
        client.Standort(2, "Lidl", Preis("4045317058067", 1.19m));

        var statistik = await ImportierenAsync(client);

        var artikel = await _db.Artikel.SingleAsync(a => a.Ean == "4045317058067");
        Assert.Equal(1.19m, artikel.Referenzpreis);
        Assert.Equal(1.09m, artikel.ReferenzpreisNiedrigster);
        Assert.Equal(1.29m, artikel.ReferenzpreisHoechster);
        Assert.Equal(3, artikel.ReferenzpreisAnzahl);
        Assert.Equal(Heute, artikel.ReferenzpreisStand);

        Assert.Equal(1, statistik.ArtikelBepreist);
        Assert.Equal(0, statistik.ArtikelNeu);
    }

    [Fact]
    public async Task UnbekannteEan_WirdMitDenProduktdatenDesPreisesAngelegt()
    {
        var client = new OpenPricesAttrappe();
        client.Standort(1, "REWE", new OpPreis
        {
            Art = "PRODUCT",
            Waehrung = "EUR",
            Preis = 2.49m,
            Datum = Heute,
            ProduktCode = "4045317058067",
            Produkt = new OpProdukt
            {
                Code = "4045317058067",
                Name = "Bergbauern Vollmilch",
                Marken = "K-Classic, Kaufland",
                Menge = "1 l",
                KategorieTags = ["en:dairies", "en:uht-milks"],
                BildUrl = "https://example.org/milch.jpg",
            },
        });

        var statistik = await ImportierenAsync(client);

        var artikel = await _db.Artikel
            .Include(a => a.Kategorie)
            .SingleAsync(a => a.Ean == "4045317058067");

        Assert.Equal("Bergbauern Vollmilch 1 l", artikel.Name);
        Assert.Equal("K-Classic", artikel.Marke);
        Assert.Equal("Milch", artikel.Kategorie?.Name);
        Assert.Equal(2.49m, artikel.Referenzpreis);
        Assert.Equal(Erstellerquelle.Import, artikel.ErstelltVon);

        Assert.Equal(1, statistik.ArtikelNeu);
        Assert.Equal(0, statistik.OhneArtikel);
    }

    [Fact]
    public async Task OhneNeueArtikel_WirdNurGezaehltStattAngelegt()
    {
        var client = new OpenPricesAttrappe();
        client.Standort(1, "REWE", new OpPreis
        {
            Art = "PRODUCT",
            Waehrung = "EUR",
            Preis = 2.49m,
            Datum = Heute,
            ProduktCode = "4045317058067",
            Produkt = new OpProdukt { Code = "4045317058067", Name = "Bergbauern Vollmilch" },
        });

        var statistik = await ImportierenAsync(client, neueArtikel: false);

        Assert.Empty(await _db.Artikel.ToListAsync());
        Assert.Equal(1, statistik.OhneArtikel);
    }

    [Fact]
    public async Task PreisOhneProduktdaten_LegtKeinenArtikelAn()
    {
        // Ein nackter Barcode ohne Namen hilft im Regal nicht weiter.
        var client = new OpenPricesAttrappe();
        client.Standort(1, "Netto", Preis("4045317058067", 1.19m));

        var statistik = await ImportierenAsync(client);

        Assert.Empty(await _db.Artikel.ToListAsync());
        Assert.Equal(0, statistik.ArtikelNeu);
        Assert.Equal(1, statistik.OhneArtikel);
    }

    [Fact]
    public async Task EigenErfasstePreiseUndDerArtikelname_BleibenUnberuehrt()
    {
        var artikelId = await ArtikelAnlegenAsync(
            "4045317058067", "Meine eigene Schreibweise", Erstellerquelle.Nutzer);

        _db.Preise.Add(new Preis
        {
            Id = Guid.NewGuid(),
            ArtikelId = artikelId,
            MarktId = 1,
            Wert = 0.89m,
            ErfasstAm = DateTimeOffset.UtcNow,
            ErfasstVon = "ich",
        });

        await _db.SaveChangesAsync();
        _db.ChangeTracker.Clear();

        var client = new OpenPricesAttrappe();
        client.Standort(1, "Kaufland", Preis("4045317058067", 1.49m));

        await ImportierenAsync(client);

        var artikel = await _db.Artikel.SingleAsync();
        Assert.Equal("Meine eigene Schreibweise", artikel.Name);
        Assert.Equal(1.49m, artikel.Referenzpreis);

        var eigener = await _db.Preise.SingleAsync();
        Assert.Equal(0.89m, eigener.Wert);
    }

    [Fact]
    public async Task ZweiterLauf_LoestDenAelterenRichtpreisAb()
    {
        // Der Richtpreis ist keine Eingabe, sondern der Stand der Quelle — ein neuer Lauf
        // soll den alten Wert ersetzen und nicht danebenstehen lassen.
        await ArtikelAnlegenAsync("4045317058067", "Vollmilch 1 l");

        var erster = new OpenPricesAttrappe();
        erster.Standort(1, "Kaufland", Preis("4045317058067", 1.09m));
        await ImportierenAsync(erster);

        var zweiter = new OpenPricesAttrappe();
        zweiter.Standort(1, "Kaufland", Preis("4045317058067", 1.39m));
        await ImportierenAsync(zweiter);

        var artikel = await _db.Artikel.SingleAsync();
        Assert.Equal(1.39m, artikel.Referenzpreis);
        Assert.Equal(1, artikel.ReferenzpreisAnzahl);
    }

    [Fact]
    public async Task StandortOhnePreise_WirdGarNichtErstAbgefragt()
    {
        var client = new OpenPricesAttrappe();
        client.Standort(1, "Kaufland"); // keine Preise

        var statistik = await ImportierenAsync(client);

        Assert.Equal(0, client.PreisAbfragen);
        Assert.Equal(1, statistik.Standorte);
        Assert.Equal(0, statistik.StandorteMitPreisen);
    }

    [Fact]
    public async Task KettenFilter_LaesstNurDieEigeneKetteDurch()
    {
        await ArtikelAnlegenAsync("4045317058067", "Vollmilch 1 l");

        var client = new OpenPricesAttrappe();
        client.Standort(1, "Kaufland", Preis("4045317058067", 1.09m));
        client.Standort(2, "Lidl", Preis("4045317058067", 0.89m));

        await ImportierenAsync(client, kette: "Kaufland");

        var artikel = await _db.Artikel.SingleAsync();
        Assert.Equal(1.09m, artikel.Referenzpreis);
        Assert.Equal(1, client.PreisAbfragen);
    }

    private async Task<Guid> ArtikelAnlegenAsync(
        string ean,
        string name,
        Erstellerquelle quelle = Erstellerquelle.Import)
    {
        var artikel = new Artikel
        {
            Id = Guid.NewGuid(),
            Name = name,
            Ean = ean,
            ErstelltVon = quelle,
            ErstelltAm = DateTimeOffset.UtcNow,
        };

        _db.Artikel.Add(artikel);
        await _db.SaveChangesAsync();
        _db.ChangeTracker.Clear();

        return artikel.Id;
    }

    private Task<Preisstatistik> ImportierenAsync(
        IOpenPricesClient client,
        bool neueArtikel = true,
        string? kette = null) =>
        new Preisimporter(
                client,
                _db,
                new Katalogschreiber(_db, NullLogger<Katalogschreiber>.Instance),
                NullLogger<Preisimporter>.Instance)
            .AusfuehrenAsync(
                new PreisImportEinstellungen
                {
                    NeueArtikel = neueArtikel,
                    Kette = kette,
                    Pause = TimeSpan.Zero,
                },
                CancellationToken.None);

    private static OpPreis Preis(string ean, decimal wert) => new()
    {
        Art = "PRODUCT",
        Waehrung = "EUR",
        Preis = wert,
        Datum = Heute,
        ProduktCode = ean,
    };

    /// <summary>Liefert je Standort eine einzige Seite — Blättern hat der echte Client schon.</summary>
    private sealed class OpenPricesAttrappe : IOpenPricesClient
    {
        private readonly List<OpStandort> _standorte = [];
        private readonly Dictionary<int, List<OpPreis>> _preise = [];

        public int PreisAbfragen { get; private set; }

        public void Standort(int id, string kette, params OpPreis[] preise)
        {
            _standorte.Add(new OpStandort
            {
                Id = id,
                Kette = kette,
                Name = kette,
                Land = "Deutschland",
                Preisanzahl = preise.Length,
            });

            _preise[id] = [.. preise];
        }

        public Task<OpSeite<OpStandort>?> StandorteAsync(
            string land, int seite, int seitengroesse, CancellationToken ct) =>
            Task.FromResult<OpSeite<OpStandort>?>(new OpSeite<OpStandort>
            {
                Eintraege = _standorte,
                Seite = 1,
                Seiten = 1,
                Gesamt = _standorte.Count,
            });

        public Task<OpSeite<OpPreis>?> PreiseAsync(
            int standortId, int seite, int seitengroesse, CancellationToken ct)
        {
            PreisAbfragen++;

            var eintraege = _preise.TryGetValue(standortId, out var gefunden) ? gefunden : [];

            return Task.FromResult<OpSeite<OpPreis>?>(new OpSeite<OpPreis>
            {
                Eintraege = eintraege,
                Seite = 1,
                Seiten = 1,
                Gesamt = eintraege.Count,
            });
        }
    }
}
