using ArtikelFinder.Api.Data;
using ArtikelFinder.Api.Entities;
using ArtikelFinder.Import;
using ArtikelFinder.Shared.Enums;
using Microsoft.Data.Sqlite;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Logging.Abstractions;
using Xunit;

namespace ArtikelFinder.Api.Tests;

/// <summary>
/// Der Katalog entsteht in einem stundenlangen Importlauf. Diese Tests sichern, dass er
/// eine SQLite-Datei überlebt: exportieren, in eine frische Datenbank einlesen, identischer
/// Bestand.
/// </summary>
public sealed class KatalogdateiTests : IAsyncLifetime
{
    private readonly string _pfad = Path.Combine(
        Path.GetTempPath(), $"katalog-test-{Guid.NewGuid():N}.tsv.gz");

    private SqliteConnection _quelle = null!;
    private ArtikelFinderDbContext _quellDb = null!;

    public async Task InitializeAsync()
    {
        _quelle = new SqliteConnection("Filename=:memory:");
        await _quelle.OpenAsync();
        _quellDb = await DatenbankAsync(_quelle);
    }

    public async Task DisposeAsync()
    {
        await _quellDb.DisposeAsync();
        await _quelle.DisposeAsync();
        File.Delete(_pfad);
    }

    [Fact]
    public async Task Rundlauf_ErhaeltAlleFelderUndDieKategorie()
    {
        var milch = await _quellDb.Kategorien.FirstAsync(k => k.Name == "Milch");
        _quellDb.Artikel.Add(new Artikel
        {
            Id = Guid.NewGuid(),
            Name = "Haltbare Bio-Vollmilch 3,5 % 1l",
            Marke = "Bärenmarke",
            Ean = "4045317058067",
            BildUrl = "https://images.example/mi.jpg",
            KategorieId = milch.Id,
            ErstelltVon = Erstellerquelle.Import,
            ErstelltAm = DateTimeOffset.UtcNow,
        });
        await _quellDb.SaveChangesAsync();

        var geschrieben = await Datei(_quellDb).SchreibenAsync(_pfad, CancellationToken.None);
        Assert.Equal(1, geschrieben);

        await using var zielVerbindung = new SqliteConnection("Filename=:memory:");
        await zielVerbindung.OpenAsync();
        await using var zielDb = await DatenbankAsync(zielVerbindung);

        var statistik = await Datei(zielDb).LesenAsync(
            _pfad, Schreiber(zielDb), stapelgroesse: 500, CancellationToken.None);

        Assert.Equal(1, statistik.Neu);

        var wiedereingelesen = await zielDb.Artikel.Include(a => a.Kategorie).SingleAsync();
        Assert.Equal("Haltbare Bio-Vollmilch 3,5 % 1l", wiedereingelesen.Name);
        Assert.Equal("Bärenmarke", wiedereingelesen.Marke);
        Assert.Equal("4045317058067", wiedereingelesen.Ean);
        Assert.Equal("https://images.example/mi.jpg", wiedereingelesen.BildUrl);
        Assert.Equal("Milch", wiedereingelesen.Kategorie!.Name);

        // Der Suchindex wird beim Einlesen neu aufgebaut, nicht mitgeschleppt.
        Assert.Contains("baerenmarke", wiedereingelesen.SuchText);
        Assert.Contains("barenmarke", wiedereingelesen.SuchText);
    }

    [Fact]
    public async Task Rundlauf_UeberstehtTabulatorenUndUmbruecheImNamen()
    {
        // Produktnamen aus Open Food Facts sind Freitext von Freiwilligen; ein Tabulator
        // darin würde das TSV-Format sonst zerlegen und die Spalten verschieben.
        _quellDb.Artikel.Add(NeuerArtikel("Käse\tmit\tTabulator\nund Umbruch", "4045317058067"));
        await _quellDb.SaveChangesAsync();

        await Datei(_quellDb).SchreibenAsync(_pfad, CancellationToken.None);

        await using var zielVerbindung = new SqliteConnection("Filename=:memory:");
        await zielVerbindung.OpenAsync();
        await using var zielDb = await DatenbankAsync(zielVerbindung);
        await Datei(zielDb).LesenAsync(_pfad, Schreiber(zielDb), 500, CancellationToken.None);

        var eingelesen = await zielDb.Artikel.SingleAsync();
        Assert.Equal("Käse mit Tabulator und Umbruch", eingelesen.Name);
    }

    [Fact]
    public async Task Seed_UeberschreibtSelbstErfassteArtikelNicht()
    {
        _quellDb.Artikel.Add(NeuerArtikel("Katalogname", "4045317058067"));
        await _quellDb.SaveChangesAsync();
        await Datei(_quellDb).SchreibenAsync(_pfad, CancellationToken.None);

        await using var zielVerbindung = new SqliteConnection("Filename=:memory:");
        await zielVerbindung.OpenAsync();
        await using var zielDb = await DatenbankAsync(zielVerbindung);

        zielDb.Artikel.Add(new Artikel
        {
            Id = Guid.NewGuid(),
            Name = "Mein eigener Name",
            Ean = "4045317058067",
            ErstelltVon = Erstellerquelle.Nutzer,
            ErstelltAm = DateTimeOffset.UtcNow,
        });
        await zielDb.SaveChangesAsync();

        var statistik = await Datei(zielDb).LesenAsync(_pfad, Schreiber(zielDb), 500, CancellationToken.None);

        Assert.Equal(1, statistik.Geschuetzt);
        Assert.Equal("Mein eigener Name", (await zielDb.Artikel.SingleAsync()).Name);
    }

    [Fact]
    public async Task Seed_IstIdempotent()
    {
        _quellDb.Artikel.Add(NeuerArtikel("Vollmilch", "4045317058067"));
        await _quellDb.SaveChangesAsync();
        await Datei(_quellDb).SchreibenAsync(_pfad, CancellationToken.None);

        await using var zielVerbindung = new SqliteConnection("Filename=:memory:");
        await zielVerbindung.OpenAsync();
        await using var zielDb = await DatenbankAsync(zielVerbindung);

        await Datei(zielDb).LesenAsync(_pfad, Schreiber(zielDb), 500, CancellationToken.None);
        var zweiterLauf = await Datei(zielDb).LesenAsync(_pfad, Schreiber(zielDb), 500, CancellationToken.None);

        Assert.Equal(0, zweiterLauf.Neu);
        Assert.Equal(1, await zielDb.Artikel.CountAsync());
    }

    [Fact]
    public async Task Rundlauf_ErhaeltDenRichtpreis()
    {
        var artikel = NeuerArtikel("Vollmilch 1 l", "4045317058067");
        artikel.Referenzpreis = 1.19m;
        artikel.ReferenzpreisNiedrigster = 1.09m;
        artikel.ReferenzpreisHoechster = 1.29m;
        artikel.ReferenzpreisAnzahl = 3;
        artikel.ReferenzpreisStand = new DateOnly(2026, 6, 30);

        _quellDb.Artikel.Add(artikel);
        await _quellDb.SaveChangesAsync();
        await Datei(_quellDb).SchreibenAsync(_pfad, CancellationToken.None);

        await using var zielVerbindung = new SqliteConnection("Filename=:memory:");
        await zielVerbindung.OpenAsync();
        await using var zielDb = await DatenbankAsync(zielVerbindung);
        await Datei(zielDb).LesenAsync(_pfad, Schreiber(zielDb), 500, CancellationToken.None);

        var eingelesen = await zielDb.Artikel.SingleAsync();
        Assert.Equal(1.19m, eingelesen.Referenzpreis);
        Assert.Equal(1.09m, eingelesen.ReferenzpreisNiedrigster);
        Assert.Equal(1.29m, eingelesen.ReferenzpreisHoechster);
        Assert.Equal(3, eingelesen.ReferenzpreisAnzahl);
        Assert.Equal(new DateOnly(2026, 6, 30), eingelesen.ReferenzpreisStand);
    }

    [Fact]
    public async Task Lesen_KommtMitEinerKatalogdateiOhnePreisspaltenZurecht()
    {
        // Die Fassung vor dem Preisimport hatte fünf Spalten. Sie muss lesbar bleiben,
        // sonst ist jeder ältere Katalog im Umlauf wertlos.
        var pfad = _pfad.Replace(".gz", string.Empty);
        await File.WriteAllTextAsync(
            pfad,
            "ean\tname\tmarke\tkategorie\tbildUrl\n"
            + "4045317058067\tVollmilch 1 l\tK-Classic\tMilch\t\n");

        var statistik = await Datei(_quellDb).LesenAsync(
            pfad, Schreiber(_quellDb), 500, CancellationToken.None);

        Assert.Equal(1, statistik.Neu);

        var eingelesen = await _quellDb.Artikel.SingleAsync();
        Assert.Equal("Vollmilch 1 l", eingelesen.Name);
        Assert.Null(eingelesen.Referenzpreis);

        File.Delete(pfad);
    }

    [Fact]
    public async Task Lesen_UeberspringtEinenKaputtenRichtpreisStattDenArtikel()
    {
        var pfad = _pfad.Replace(".gz", string.Empty);
        await File.WriteAllTextAsync(
            pfad,
            "ean\tname\tmarke\tkategorie\tbildUrl\tpreis\tpreisMin\tpreisMax\tpreisAnzahl\tpreisStand\n"
            + "4045317058067\tVollmilch 1 l\tK-Classic\tMilch\t\tkaputt\t1.09\t1.29\t3\t2026-06-30\n");

        await Datei(_quellDb).LesenAsync(pfad, Schreiber(_quellDb), 500, CancellationToken.None);

        var eingelesen = await _quellDb.Artikel.SingleAsync();
        Assert.Equal("Vollmilch 1 l", eingelesen.Name);
        Assert.Null(eingelesen.Referenzpreis);

        File.Delete(pfad);
    }

    [Fact]
    public async Task Lesen_LehntFremdeDateienAb()
    {
        await File.WriteAllTextAsync(_pfad.Replace(".gz", string.Empty), "irgendwas\tanderes\n");

        await Assert.ThrowsAsync<InvalidDataException>(() =>
            Datei(_quellDb).LesenAsync(
                _pfad.Replace(".gz", string.Empty), Schreiber(_quellDb), 500, CancellationToken.None));

        File.Delete(_pfad.Replace(".gz", string.Empty));
    }

    private static Artikel NeuerArtikel(string name, string ean) => new()
    {
        Id = Guid.NewGuid(),
        Name = name,
        Ean = ean,
        ErstelltVon = Erstellerquelle.Import,
        ErstelltAm = DateTimeOffset.UtcNow,
    };

    private static async Task<ArtikelFinderDbContext> DatenbankAsync(SqliteConnection verbindung)
    {
        var db = new ArtikelFinderDbContext(new DbContextOptionsBuilder<ArtikelFinderDbContext>()
            .UseSqlite(verbindung).Options);

        await db.Database.EnsureCreatedAsync();
        await Startdaten.AnwendenAsync(db);
        return db;
    }

    private static Katalogdatei Datei(ArtikelFinderDbContext db) =>
        new(db, NullLogger<Katalogdatei>.Instance);

    private static Katalogschreiber Schreiber(ArtikelFinderDbContext db) =>
        new(db, NullLogger<Katalogschreiber>.Instance);
}
