using ArtikelFinder.Api.Data;
using ArtikelFinder.Api.Entities;
using ArtikelFinder.Import;
using ArtikelFinder.Shared.Enums;
using Microsoft.Data.Sqlite;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Logging.Abstractions;
using Xunit;

namespace ArtikelFinder.Api.Tests;

public sealed class KatalogschreiberTests : IAsyncLifetime
{
    private SqliteConnection _verbindung = null!;
    private ArtikelFinderDbContext _db = null!;
    private Katalogschreiber _schreiber = null!;

    public async Task InitializeAsync()
    {
        _verbindung = new SqliteConnection("Filename=:memory:");
        await _verbindung.OpenAsync();

        _db = new ArtikelFinderDbContext(new DbContextOptionsBuilder<ArtikelFinderDbContext>()
            .UseSqlite(_verbindung).Options);

        await _db.Database.EnsureCreatedAsync();
        await Startdaten.AnwendenAsync(_db);

        _schreiber = new Katalogschreiber(_db, NullLogger<Katalogschreiber>.Instance);
    }

    public async Task DisposeAsync()
    {
        await _db.DisposeAsync();
        await _verbindung.DisposeAsync();
    }

    [Fact]
    public async Task Import_LegtNeueArtikelMitQuelleImportAn()
    {
        var statistik = await _schreiber.SchreibenAsync(
            [new Rohartikel("Vollmilch 1l", "Bärenmarke", "4045317058067", "https://bild", "Milch")],
            CancellationToken.None);

        Assert.Equal(1, statistik.Neu);

        var artikel = await _db.Artikel.SingleAsync();
        Assert.Equal(Erstellerquelle.Import, artikel.ErstelltVon);
        Assert.Equal("Milch", (await _db.Kategorien.FindAsync(artikel.KategorieId))!.Name);
    }

    [Fact]
    public async Task Import_UeberschreibtNutzerdatenNicht()
    {
        // Genau der Fall, der beim naechtlichen Nachimport wehtut: der Artikel wurde beim
        // Einkauf selbst angelegt und darf nicht von OFF-Daten ueberbuegelt werden.
        _db.Artikel.Add(new Artikel
        {
            Id = Guid.NewGuid(),
            Name = "Meine Vollmilch",
            Marke = "Selbst erfasst",
            Ean = "4045317058067",
            ErstelltVon = Erstellerquelle.Nutzer,
            ErstelltAm = DateTimeOffset.UtcNow,
        });
        await _db.SaveChangesAsync();

        var statistik = await _schreiber.SchreibenAsync(
            [new Rohartikel("OFF-Name", "OFF-Marke", "4045317058067", "https://bild", "Milch")],
            CancellationToken.None);

        Assert.Equal(1, statistik.Geschuetzt);

        var artikel = await _db.Artikel.SingleAsync();
        Assert.Equal("Meine Vollmilch", artikel.Name);
        Assert.Equal("Selbst erfasst", artikel.Marke);
    }

    [Fact]
    public async Task Import_FuelltNurLuecken()
    {
        await _schreiber.SchreibenAsync(
            [new Rohartikel("Vollmilch", null, "4045317058067", null, null)],
            CancellationToken.None);

        var statistik = await _schreiber.SchreibenAsync(
            [new Rohartikel("Anderer Name", "Bärenmarke", "4045317058067", "https://bild", "Milch")],
            CancellationToken.None);

        Assert.Equal(1, statistik.Ergaenzt);

        var artikel = await _db.Artikel.SingleAsync();
        Assert.Equal("Vollmilch", artikel.Name);      // vorhandener Name bleibt
        Assert.Equal("Bärenmarke", artikel.Marke);    // Luecke wurde gefuellt
        Assert.Equal("https://bild", artikel.BildUrl);
    }

    [Fact]
    public async Task Import_EntferntDublettenInnerhalbEinesStapels()
    {
        var statistik = await _schreiber.SchreibenAsync(
            [
                new Rohartikel("Vollmilch DE", null, "4045317058067", null, null),
                new Rohartikel("Vollmilch AT", null, "4045317058067", null, null),
            ],
            CancellationToken.None);

        Assert.Equal(1, statistik.Neu);
        Assert.Equal(1, statistik.Uebersprungen);
        Assert.Equal("Vollmilch DE", (await _db.Artikel.SingleAsync()).Name);
    }

    [Fact]
    public async Task Import_SetztDenSuchtextAutomatisch()
    {
        await _schreiber.SchreibenAsync(
            [new Rohartikel("Vollmilch 1l", "Bärenmarke", "4045317058067", null, null)],
            CancellationToken.None);

        var artikel = await _db.Artikel.SingleAsync();
        Assert.Contains("baerenmarke", artikel.SuchText);
        Assert.Contains("barenmarke", artikel.SuchText);
    }
}
