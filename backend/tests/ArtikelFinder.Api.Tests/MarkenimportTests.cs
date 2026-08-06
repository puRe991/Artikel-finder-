using ArtikelFinder.Api.Data;
using ArtikelFinder.Import;
using ArtikelFinder.Import.OpenFoodFacts;
using Microsoft.Data.Sqlite;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Logging.Abstractions;
using Xunit;

namespace ArtikelFinder.Api.Tests;

/// <summary>
/// Tests fuer den Eigenmarken-Import. Der Unterschied zum Warengruppen-Import: die Abfrage
/// gibt keine Kategorie her, die muss aus den Tags des einzelnen Produkts kommen — und
/// gefragt wird nicht nur Open Food Facts, sondern auch die Schwesterdatenbanken.
/// </summary>
public sealed class MarkenimportTests : IAsyncLifetime
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
    public async Task JedeMarke_WirdInAllenDatenbankenGesucht()
    {
        // Toilettenpapier, Duschgel und Katzenfutter stehen nicht in Open Food Facts. Wird
        // nur dort gesucht, fehlt der komplette Drogerie- und Tierbedarfsteil der Marke.
        var client = new OffClientAttrappe();

        await ImportierenAsync(client, new MarkenImportEinstellungen
        {
            Marken = [new Eigenmarke("k-classic", "K-Classic"), new Eigenmarke("k-bio", "K-Bio")],
            Pause = TimeSpan.Zero,
        });

        var gefragt = client.Abfragen.Select(a => $"{a.Wert}@{a.Datenbank.Name}").ToList();

        Assert.Equal(2 * OffDatenbank.Alle.Count, gefragt.Count);
        Assert.Contains("k-classic@Open Food Facts", gefragt);
        Assert.Contains("k-classic@Open Beauty Facts", gefragt);
        Assert.Contains("k-classic@Open Products Facts", gefragt);
        Assert.Contains("k-classic@Open Pet Food Facts", gefragt);
        Assert.Contains("k-bio@Open Beauty Facts", gefragt);
    }

    [Fact]
    public async Task DieAbfrage_FiltertNachMarkeUndLand()
    {
        var client = new OffClientAttrappe();

        await ImportierenAsync(client, new MarkenImportEinstellungen
        {
            Marken = [new Eigenmarke("k-classic", "K-Classic")],
            Datenbanken = [OffDatenbank.Lebensmittel],
            Land = "germany",
            Pause = TimeSpan.Zero,
        });

        var abfrage = Assert.Single(client.Abfragen);
        Assert.Equal("brands_tags", abfrage.Feld);
        Assert.Equal("k-classic", abfrage.Wert);
        Assert.Equal("germany", abfrage.Land);
    }

    [Fact]
    public async Task DieKategorie_KommtAusDenTagsDesArtikels()
    {
        // Eine Marke zieht sich quer durchs Sortiment: unter K-Classic stehen Milch und
        // Toilettenpapier nebeneinander, eine Kategorie je Abfrage gibt es nicht.
        var client = new OffClientAttrappe();
        client.Seiten[(OffDatenbank.Lebensmittel, 1)] = OffSeitenergebnis.Geladen(new OffSuchantwort
        {
            Produkte =
            [
                Produkt("4337185369964", "Vollmilch", ["en:dairies", "en:milks", "en:uht-milks"]),
                Produkt("4337185377532", "Röstzwiebeln", ["en:plant-based-foods", "en:condiments"]),
            ],
        });
        client.Seiten[(OffDatenbank.Haushalt, 1)] = OffSeitenergebnis.Geladen(new OffSuchantwort
        {
            Produkte =
            [
                Produkt("4337185873331", "Toilettenpapier", ["en:home-garden", "en:toilet-papers"]),
            ],
        });

        await ImportierenAsync(client, new MarkenImportEinstellungen
        {
            Marken = [new Eigenmarke("k-classic", "K-Classic")],
            Pause = TimeSpan.Zero,
        });

        Assert.Equal("Milch", await KategorieVonAsync("4337185369964"));
        Assert.Equal("Gewürze", await KategorieVonAsync("4337185377532"));
        Assert.Equal("Papierwaren", await KategorieVonAsync("4337185873331"));
    }

    [Fact]
    public async Task OhneBrauchbareTags_GreiftDieStandardkategorieDerDatenbank()
    {
        // In den kleinen Schwesterdatenbanken ist die Kategorisierung oft leer. Die
        // Oberkategorie stimmt trotzdem — was in Open Pet Food Facts steht, ist Tierbedarf.
        var client = new OffClientAttrappe();
        client.Seiten[(OffDatenbank.Tierbedarf, 1)] = OffSeitenergebnis.Geladen(new OffSuchantwort
        {
            Produkte = [Produkt("4337185369964", "Katzenmilch", null)],
        });
        client.Seiten[(OffDatenbank.Lebensmittel, 1)] = OffSeitenergebnis.Geladen(new OffSuchantwort
        {
            Produkte = [Produkt("4337185377532", "Irgendwas", null)],
        });

        await ImportierenAsync(client, new MarkenImportEinstellungen
        {
            Marken = [new Eigenmarke("k-classic", "K-Classic")],
            Pause = TimeSpan.Zero,
        });

        Assert.Equal("Tierbedarf", await KategorieVonAsync("4337185369964"));

        // Fuer Lebensmittel gibt es keinen sinnvollen Sammelbegriff — lieber keine Kategorie
        // als eine falsche.
        Assert.Null(await KategorieVonAsync("4337185377532"));
    }

    [Fact]
    public async Task OhneObergrenze_WirdDieMarkeVollstaendigGeholt()
    {
        // K-Classic hat mehrere tausend Artikel. Die Voreinstellung des
        // Warengruppen-Imports (500) wuerde die Marke mittendrin abschneiden.
        var client = new OffClientAttrappe();
        client.Seiten[(OffDatenbank.Lebensmittel, 1)] = Seite(anzahl: Seitengroesse, startEan: 0);
        client.Seiten[(OffDatenbank.Lebensmittel, 2)] = Seite(anzahl: Seitengroesse, startEan: 100);
        client.Seiten[(OffDatenbank.Lebensmittel, 3)] = Seite(anzahl: 1, startEan: 200);

        var statistik = await ImportierenAsync(client, new MarkenImportEinstellungen
        {
            Marken = [new Eigenmarke("k-classic", "K-Classic")],
            Datenbanken = [OffDatenbank.Lebensmittel],
            MaxProMarke = 0,
            Seitengroesse = Seitengroesse,
            Pause = TimeSpan.Zero,
        });

        Assert.Equal((2 * Seitengroesse) + 1, statistik.Neu);
    }

    [Fact]
    public async Task EinAusfall_BeendetDieMarkeNicht()
    {
        var client = new OffClientAttrappe();
        client.Seiten[(OffDatenbank.Lebensmittel, 1)] = OffSeitenergebnis.Fehlgeschlagen;
        client.Seiten[(OffDatenbank.Lebensmittel, 2)] = Seite(anzahl: 2, startEan: 0);

        var statistik = await ImportierenAsync(client, new MarkenImportEinstellungen
        {
            Marken = [new Eigenmarke("k-classic", "K-Classic")],
            Datenbanken = [OffDatenbank.Lebensmittel],
            Seitengroesse = Seitengroesse,
            Pause = TimeSpan.Zero,
        });

        Assert.Equal(2, statistik.Neu);
        Assert.Equal(1, statistik.SeitenFehlgeschlagen);
    }

    /// <summary>Klein gehalten, damit "volle Seite" in den Tests ohne Datenberge darstellbar ist.</summary>
    private const int Seitengroesse = 3;

    private Task<Importstatistik> ImportierenAsync(IOffClient client, MarkenImportEinstellungen einstellungen) =>
        new ApiImporter(client, _schreiber, NullLogger<ApiImporter>.Instance)
            .MarkenImportierenAsync(einstellungen, CancellationToken.None);

    private async Task<string?> KategorieVonAsync(string ean)
    {
        var artikel = await _db.Artikel
            .AsNoTracking()
            .Include(a => a.Kategorie)
            .SingleAsync(a => a.Ean == ean);

        return artikel.Kategorie?.Name;
    }

    private static OffProdukt Produkt(string ean, string name, List<string>? tags) =>
        new() { Code = ean, ProduktName = name, KategorieTags = tags };

    private static OffSeitenergebnis Seite(int anzahl, int startEan) =>
        OffSeitenergebnis.Geladen(new OffSuchantwort
        {
            Produkte = [.. Enumerable.Range(startEan, anzahl).Select(i => new OffProdukt
            {
                Code = MitPruefziffer($"400840020{i:0000}"),
                ProduktName = $"Testartikel {i}",
            })],
        });

    private static string MitPruefziffer(string zwoelfStellen)
    {
        var summe = 0;
        var gewicht = 3;
        for (var i = zwoelfStellen.Length - 1; i >= 0; i--)
        {
            summe += (zwoelfStellen[i] - '0') * gewicht;
            gewicht = gewicht == 3 ? 1 : 3;
        }

        return zwoelfStellen + ((10 - (summe % 10)) % 10);
    }

    /// <summary>
    /// Antwortet je Datenbank und Seite nach Plan. Nicht eingeplante Seiten sind leer — eine
    /// Marke, die es in einer Datenbank nicht gibt, ist der Normalfall und kein Fehler.
    /// </summary>
    private sealed class OffClientAttrappe : IOffClient
    {
        public Dictionary<(OffDatenbank Datenbank, int Seite), OffSeitenergebnis> Seiten { get; } = [];

        public List<OffAbfrage> Abfragen { get; } = [];

        public Task<OffSeitenergebnis> SuchenAsync(
            OffAbfrage abfrage,
            int seite,
            int seitengroesse,
            CancellationToken ct)
        {
            if (seite == 1)
            {
                Abfragen.Add(abfrage);
            }

            return Task.FromResult(
                Seiten.TryGetValue((abfrage.Datenbank, seite), out var ergebnis)
                    ? ergebnis
                    : OffSeitenergebnis.Geladen(new OffSuchantwort { Produkte = [] }));
        }
    }
}
