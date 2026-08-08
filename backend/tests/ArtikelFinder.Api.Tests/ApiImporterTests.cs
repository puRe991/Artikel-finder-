using ArtikelFinder.Api.Data;
using ArtikelFinder.Import;
using ArtikelFinder.Import.OpenFoodFacts;
using Microsoft.Data.Sqlite;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Logging.Abstractions;
using Xunit;

namespace ArtikelFinder.Api.Tests;

/// <summary>
/// Regressionstests fuer das Verhalten bei Ausfaellen. Open Food Facts liefert im echten
/// Betrieb laufend 503er; im ersten vollstaendigen Importlauf sind dadurch drei komplette
/// Warengruppen leer geblieben, weil ein Ausfall auf Seite 1 die ganze Gruppe beendet hat.
/// </summary>
public sealed class ApiImporterTests : IAsyncLifetime
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
    public async Task AusfallAufSeiteEins_BeendetDieWarengruppeNicht()
    {
        // Genau der Fall aus dem echten Lauf: drei Warengruppen blieben leer, weil ein
        // einzelner 503 auf Seite 1 den kompletten Rest gekostet hat.
        var client = new OffClientAttrappe();
        client.SeitenPlan[1] = OffSeitenergebnis.Fehlgeschlagen;
        client.SeitenPlan[2] = Seite(anzahl: 2, startEan: 0); // Teilseite = letzte Seite

        var statistik = await ImportierenAsync(client);

        Assert.Equal(2, statistik.Neu);
        Assert.Equal(1, statistik.SeitenFehlgeschlagen);
    }

    [Fact]
    public async Task AusfallInDerMitte_HoltDieFolgendenSeitenTrotzdem()
    {
        var client = new OffClientAttrappe();
        client.SeitenPlan[1] = Seite(anzahl: Seitengroesse, startEan: 0); // volle Seite
        client.SeitenPlan[2] = OffSeitenergebnis.Fehlgeschlagen;
        client.SeitenPlan[3] = Seite(anzahl: 2, startEan: 100);

        var statistik = await ImportierenAsync(client);

        Assert.Equal(Seitengroesse + 2, statistik.Neu);
        Assert.Equal(1, statistik.SeitenFehlgeschlagen);
    }

    [Fact]
    public async Task DreiAusfaelleInFolge_BrechenDieWarengruppeAb()
    {
        var client = new OffClientAttrappe(); // liefert per Voreinstellung nur Ausfaelle

        var statistik = await ImportierenAsync(client);

        // Nach drei Fehlschlaegen wird aufgegeben, statt bis MaxProZiel weiterzulaufen.
        Assert.Equal(3, statistik.SeitenFehlgeschlagen);
        Assert.Equal(3, client.Aufrufe);
        Assert.Equal(0, statistik.Neu);
    }

    [Fact]
    public async Task LeereSeite_BeendetDieWarengruppeRegulaer()
    {
        var client = new OffClientAttrappe();
        client.SeitenPlan[1] = Seite(anzahl: Seitengroesse, startEan: 0);
        client.SeitenPlan[2] = OffSeitenergebnis.Geladen(new OffSuchantwort { Produkte = [] });

        var statistik = await ImportierenAsync(client);

        Assert.Equal(Seitengroesse, statistik.Neu);
        Assert.Equal(0, statistik.SeitenFehlgeschlagen);
    }

    [Fact]
    public async Task AbgelehnteAnfrage_WirdNichtAlsAusfallGezaehlt()
    {
        // Ein unbekannter Tag ist ein Anwendungsfehler, kein Ausfall — nicht wiederholen.
        var client = new OffClientAttrappe();
        client.SeitenPlan[1] = OffSeitenergebnis.Abgelehnt;

        var statistik = await ImportierenAsync(client);

        Assert.Equal(0, statistik.SeitenFehlgeschlagen);
        Assert.Equal(1, client.Aufrufe);
    }

    [Fact]
    public async Task ProduktOhneNameOderMitKaputterEan_WirdVerworfen()
    {
        var client = new OffClientAttrappe();
        client.SeitenPlan[1] = OffSeitenergebnis.Geladen(new OffSuchantwort
        {
            Produkte =
            [
                new OffProdukt { Code = "4045317058067", ProduktName = "Gute Milch" },
                new OffProdukt { Code = "4045317058067", ProduktName = null },      // kein Name
                new OffProdukt { Code = "4045317058060", ProduktName = "Falsche Prüfziffer" },
            ],
        });
        client.SeitenPlan[2] = OffSeitenergebnis.Geladen(new OffSuchantwort { Produkte = [] });

        var statistik = await ImportierenAsync(client);

        Assert.Equal(1, statistik.Neu);
        Assert.Equal(1, statistik.OhneName);
        Assert.Equal(1, statistik.OhneGueltigeEan);
    }

    /// <summary>Klein gehalten, damit "volle Seite" in den Tests ohne Datenberge darstellbar ist.</summary>
    private const int Seitengroesse = 3;

    private Task<Importstatistik> ImportierenAsync(IOffClient client) =>
        new ApiImporter(client, _schreiber, NullLogger<ApiImporter>.Instance)
            .AusfuehrenAsync(
                new ApiImportEinstellungen
                {
                    Ziele = [new Importziel("en:milks", "Milch")],
                    MaxProZiel = 50,
                    Seitengroesse = Seitengroesse,
                    Pause = TimeSpan.Zero,
                },
                CancellationToken.None);

    /// <summary>Eine Seite mit gueltigen Testprodukten (EANs mit korrekter Pruefziffer).</summary>
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

    private sealed class OffClientAttrappe : IOffClient
    {
        public Dictionary<int, OffSeitenergebnis> SeitenPlan { get; } = [];
        public int Aufrufe { get; private set; }

        public Task<OffSeitenergebnis> SuchenAsync(
            OffAbfrage abfrage,
            int seite,
            int seitengroesse,
            CancellationToken ct)
        {
            Aufrufe++;
            return Task.FromResult(
                SeitenPlan.TryGetValue(seite, out var ergebnis)
                    ? ergebnis
                    : OffSeitenergebnis.Fehlgeschlagen);
        }

        /// <summary>Die Anreicherung spielt in diesen Tests keine Rolle.</summary>
        public Task<OffSeitenergebnis> AngabenAsync(
            IReadOnlyCollection<string> codes,
            CancellationToken ct) =>
            Task.FromResult(OffSeitenergebnis.Geladen(new OffSuchantwort()));
    }
}
