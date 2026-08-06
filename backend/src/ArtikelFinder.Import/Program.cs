using System.CommandLine;
using System.Net.Http.Headers;
using ArtikelFinder.Api.Data;
using ArtikelFinder.Import;
using ArtikelFinder.Import.OpenFoodFacts;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Logging;

// Open Food Facts verlangt einen identifizierbaren User-Agent mit Kontaktmoeglichkeit.
// Anonyme Bots werden geblockt — hier bitte die eigene Adresse eintragen.
const string StandardUserAgent = "ArtikelFinder/0.1 (privates Projekt; kontakt@example.org)";

var datenbankOption = new Option<string>(
    ["--datenbank", "-d"],
    () => "Data Source=artikelfinder.db",
    "Verbindungszeichenfolge zur SQLite-Datei.");

var userAgentOption = new Option<string>(
    "--user-agent",
    () => StandardUserAgent,
    "User-Agent für Open Food Facts. Bitte eigene Kontaktadresse eintragen.");

var ausfuehrlichOption = new Option<bool>(
    ["--ausfuehrlich", "-v"],
    "Zeigt zusätzlich Debug-Ausgaben.");

// --- Befehl: api ---
var landOption = new Option<string>("--land", () => "germany", "Ländertag bei Open Food Facts.");
var maxProZielOption = new Option<int>("--max", () => 500, "Maximale Artikel je Warengruppe.");
var seitengroesseOption = new Option<int>("--seitengroesse", () => 100, "Produkte pro Anfrage (max. 100).");
var pauseOption = new Option<int>("--pause", () => 6000, "Pause zwischen Anfragen in Millisekunden.");
var kategorieOption = new Option<string[]>(
    "--kategorie",
    "Open-Food-Facts-Tag, z.B. en:milks. Mehrfach angebbar. Ohne Angabe wird die Standardliste importiert.")
{
    AllowMultipleArgumentsPerToken = true,
};

var apiBefehl = new Command("api", "Importiert über die Open-Food-Facts-Such-API.")
{
    landOption, maxProZielOption, seitengroesseOption, pauseOption, kategorieOption,
};

// --- Befehl: marken ---
var markenLandOption = new Option<string>("--land", () => "germany", "Ländertag. Leer = alle Länder.");
var markenMaxOption = new Option<int>("--max", () => 0, "Maximale Artikel je Marke und Datenbank. 0 = kein Limit.");
var markenSeitengroesseOption = new Option<int>("--seitengroesse", () => 100, "Produkte pro Anfrage (max. 100).");
var markenPauseOption = new Option<int>("--pause", () => 6000, "Pause zwischen Anfragen in Millisekunden.");
var markeOption = new Option<string[]>(
    "--marke",
    "Marken-Tag, z.B. k-classic. Mehrfach angebbar. Ohne Angabe werden alle Kaufland-Marken geholt.")
{
    AllowMultipleArgumentsPerToken = true,
};

var markenBefehl = new Command(
    "marken",
    "Importiert die Kaufland-Eigenmarken (K-Classic, K-Bio, Purland, Bevola …) aus allen "
    + "vier Datenbanken der Open-Food-Facts-Familie.")
{
    markeOption, markenLandOption, markenMaxOption, markenSeitengroesseOption, markenPauseOption,
};

// --- Befehl: csv ---
var dateiOption = new Option<string>("--datei", "Pfad zum TSV-Export (auch .gz).") { IsRequired = true };
var csvLandOption = new Option<string>("--land", () => "germany", "Nur Produkte mit diesem Land. Leer = alle.");
var csvMaxOption = new Option<int>("--max", () => 0, "Maximale Artikelzahl. 0 = kein Limit.");
var stapelOption = new Option<int>("--stapel", () => 2000, "Artikel pro Schreibvorgang.");

var csvBefehl = new Command("csv", "Importiert aus dem Open-Food-Facts-Bulk-Export.")
{
    dateiOption, csvLandOption, csvMaxOption, stapelOption,
};

// --- Befehle: export / seed ---
// Ein aufgebauter Katalog soll nicht an der lokalen SQLite-Datei hängen: 'export' schreibt
// ihn als gepacktes TSV, 'seed' liest ihn in eine frische Datenbank zurück.
var katalogDateiOption = new Option<string>(
    "--datei",
    () => "../../daten/katalog-seed.tsv.gz",
    "Pfad der Katalogdatei (.gz wird gepackt).");

var exportBefehl = new Command("export", "Schreibt den Artikelkatalog in eine portable Datei.")
{
    katalogDateiOption,
};

var seedStapelOption = new Option<int>("--stapel", () => 2000, "Artikel pro Schreibvorgang.");
var seedBefehl = new Command("seed", "Liest eine mit 'export' erzeugte Katalogdatei ein.")
{
    katalogDateiOption, seedStapelOption,
};

var wurzel = new RootCommand("Befüllt den Artikel-Finder-Katalog aus der Open-Food-Facts-Familie.")
{
    apiBefehl, markenBefehl, csvBefehl, exportBefehl, seedBefehl,
};

wurzel.AddGlobalOption(datenbankOption);
wurzel.AddGlobalOption(userAgentOption);
wurzel.AddGlobalOption(ausfuehrlichOption);

apiBefehl.SetHandler(async kontext =>
{
    var dienste = DiensteBauen(
        kontext.ParseResult.GetValueForOption(datenbankOption)!,
        kontext.ParseResult.GetValueForOption(userAgentOption)!,
        kontext.ParseResult.GetValueForOption(ausfuehrlichOption));

    await using var _ = dienste;
    var ct = kontext.GetCancellationToken();
    await DatenbankVorbereitenAsync(dienste, ct);

    var tags = kontext.ParseResult.GetValueForOption(kategorieOption) ?? [];
    var ziele = tags.Length > 0
        ? tags.Select(t => new Importziel(t, ZielkategorieRaten(t))).ToList()
        : Importziel.Standard.ToList();

    var einstellungen = new ApiImportEinstellungen
    {
        Ziele = ziele,
        Land = kontext.ParseResult.GetValueForOption(landOption)!,
        MaxProZiel = kontext.ParseResult.GetValueForOption(maxProZielOption),
        Seitengroesse = Math.Clamp(kontext.ParseResult.GetValueForOption(seitengroesseOption), 1, 100),
        Pause = TimeSpan.FromMilliseconds(Math.Max(0, kontext.ParseResult.GetValueForOption(pauseOption))),
    };

    var importer = dienste.GetRequiredService<ApiImporter>();
    var protokoll = dienste.GetRequiredService<ILoggerFactory>().CreateLogger("Import");

    protokoll.LogInformation("Starte API-Import für {Anzahl} Warengruppen.", ziele.Count);
    var statistik = await importer.AusfuehrenAsync(einstellungen, ct);
    protokoll.LogInformation("Fertig. {Statistik}", statistik);
});

markenBefehl.SetHandler(async kontext =>
{
    var dienste = DiensteBauen(
        kontext.ParseResult.GetValueForOption(datenbankOption)!,
        kontext.ParseResult.GetValueForOption(userAgentOption)!,
        kontext.ParseResult.GetValueForOption(ausfuehrlichOption));

    await using var _ = dienste;
    var ct = kontext.GetCancellationToken();
    await DatenbankVorbereitenAsync(dienste, ct);

    var tags = kontext.ParseResult.GetValueForOption(markeOption) ?? [];
    var marken = tags.Length > 0
        ? tags.Select(MarkeFinden).ToList()
        : Eigenmarke.Standard.ToList();

    var einstellungen = new MarkenImportEinstellungen
    {
        Marken = marken,
        Land = kontext.ParseResult.GetValueForOption(markenLandOption)!,
        MaxProMarke = Math.Max(0, kontext.ParseResult.GetValueForOption(markenMaxOption)),
        Seitengroesse = Math.Clamp(kontext.ParseResult.GetValueForOption(markenSeitengroesseOption), 1, 100),
        Pause = TimeSpan.FromMilliseconds(Math.Max(0, kontext.ParseResult.GetValueForOption(markenPauseOption))),
    };

    var importer = dienste.GetRequiredService<ApiImporter>();
    var protokoll = dienste.GetRequiredService<ILoggerFactory>().CreateLogger("Import");

    protokoll.LogInformation(
        "Starte Markenimport: {Marken} Marken × {Datenbanken} Datenbanken.",
        marken.Count, einstellungen.Datenbanken.Count);

    var statistik = await importer.MarkenImportierenAsync(einstellungen, ct);
    protokoll.LogInformation("Fertig. {Statistik}", statistik);
});

csvBefehl.SetHandler(async kontext =>
{
    var dienste = DiensteBauen(
        kontext.ParseResult.GetValueForOption(datenbankOption)!,
        kontext.ParseResult.GetValueForOption(userAgentOption)!,
        kontext.ParseResult.GetValueForOption(ausfuehrlichOption));

    await using var _ = dienste;
    var ct = kontext.GetCancellationToken();
    await DatenbankVorbereitenAsync(dienste, ct);

    var einstellungen = new CsvImportEinstellungen
    {
        Datei = kontext.ParseResult.GetValueForOption(dateiOption)!,
        Land = kontext.ParseResult.GetValueForOption(csvLandOption),
        MaxArtikel = kontext.ParseResult.GetValueForOption(csvMaxOption),
        Stapelgroesse = Math.Max(1, kontext.ParseResult.GetValueForOption(stapelOption)),
    };

    var importer = dienste.GetRequiredService<CsvImporter>();
    var protokoll = dienste.GetRequiredService<ILoggerFactory>().CreateLogger("Import");

    protokoll.LogInformation("Starte Bulk-Import aus {Datei}.", einstellungen.Datei);
    var statistik = await importer.AusfuehrenAsync(einstellungen, ct);
    protokoll.LogInformation("Fertig. {Statistik}", statistik);
});

exportBefehl.SetHandler(async kontext =>
{
    var dienste = DiensteBauen(
        kontext.ParseResult.GetValueForOption(datenbankOption)!,
        kontext.ParseResult.GetValueForOption(userAgentOption)!,
        kontext.ParseResult.GetValueForOption(ausfuehrlichOption));

    await using var _ = dienste;
    var ct = kontext.GetCancellationToken();

    var pfad = kontext.ParseResult.GetValueForOption(katalogDateiOption)!;
    var protokoll = dienste.GetRequiredService<ILoggerFactory>().CreateLogger("Export");

    var anzahl = await dienste.GetRequiredService<Katalogdatei>().SchreibenAsync(pfad, ct);
    var groesse = new FileInfo(pfad).Length / 1024.0;

    protokoll.LogInformation(
        "{Anzahl} Artikel nach {Datei} geschrieben ({Groesse:0} KB).", anzahl, pfad, groesse);
});

seedBefehl.SetHandler(async kontext =>
{
    var dienste = DiensteBauen(
        kontext.ParseResult.GetValueForOption(datenbankOption)!,
        kontext.ParseResult.GetValueForOption(userAgentOption)!,
        kontext.ParseResult.GetValueForOption(ausfuehrlichOption));

    await using var _ = dienste;
    var ct = kontext.GetCancellationToken();
    await DatenbankVorbereitenAsync(dienste, ct);

    var pfad = kontext.ParseResult.GetValueForOption(katalogDateiOption)!;
    var protokoll = dienste.GetRequiredService<ILoggerFactory>().CreateLogger("Seed");

    protokoll.LogInformation("Lese Katalog aus {Datei}.", pfad);

    var statistik = await dienste.GetRequiredService<Katalogdatei>().LesenAsync(
        pfad,
        dienste.GetRequiredService<Katalogschreiber>(),
        Math.Max(1, kontext.ParseResult.GetValueForOption(seedStapelOption)),
        ct);

    protokoll.LogInformation("Fertig. {Statistik}", statistik);
});

return await wurzel.InvokeAsync(args);

static ServiceProvider DiensteBauen(string verbindung, string userAgent, bool ausfuehrlich)
{
    var dienste = new ServiceCollection();

    dienste.AddLogging(b => b
        .AddSimpleConsole(o => o.SingleLine = true)
        .SetMinimumLevel(ausfuehrlich ? LogLevel.Debug : LogLevel.Information)
        // Ein Import schreibt zehntausende Zeilen — jedes INSERT zu protokollieren macht
        // den Fortschritt unlesbar und kostet spuerbar Zeit.
        .AddFilter("Microsoft.EntityFrameworkCore", LogLevel.Warning)
        .AddFilter("System.Net.Http", LogLevel.Warning));

    dienste.AddDbContext<ArtikelFinderDbContext>(o => o.UseSqlite(verbindung));

    // Keine BaseAddress: die Adresse steht je Abfrage fest, weil die Schwesterdatenbanken
    // unter eigenen Hostnamen liegen.
    dienste.AddHttpClient<IOffClient, OffClient>(http =>
    {
        http.Timeout = TimeSpan.FromSeconds(60);
        http.DefaultRequestHeaders.UserAgent.ParseAdd(userAgent);
        http.DefaultRequestHeaders.Accept.Add(new MediaTypeWithQualityHeaderValue("application/json"));
    });

    dienste.AddScoped<Katalogschreiber>();
    dienste.AddScoped<Katalogdatei>();
    dienste.AddScoped<ApiImporter>();
    dienste.AddScoped<CsvImporter>();

    return dienste.BuildServiceProvider();
}

static async Task DatenbankVorbereitenAsync(IServiceProvider dienste, CancellationToken ct)
{
    // Der Importer darf vor dem ersten API-Start laufen, deshalb migriert er selbst.
    var db = dienste.GetRequiredService<ArtikelFinderDbContext>();
    await db.Database.MigrateAsync(ct);
    await Startdaten.AnwendenAsync(db, ct);
}

/// <summary>
/// Rät aus einem frei angegebenen OFF-Tag eine Zielkategorie, indem in der Standardliste
/// nachgesehen wird. Unbekannte Tags landen ohne Kategorie im Katalog — besser als eine
/// falsche Zuordnung, die du später einzeln aufräumen müsstest.
/// </summary>
static string ZielkategorieRaten(string offTag) =>
    Importziel.Standard.FirstOrDefault(z =>
        z.OffTag.Equals(offTag, StringComparison.OrdinalIgnoreCase))?.ZielKategorie
    ?? string.Empty;

/// <summary>
/// Sucht zu einem angegebenen Tag die bekannte Eigenmarke. Ein unbekannter Tag ist erlaubt —
/// er wird einfach so abgefragt, dann heisst die Marke im Protokoll eben wie ihr Tag.
/// </summary>
static Eigenmarke MarkeFinden(string markenTag) =>
    Eigenmarke.Standard.FirstOrDefault(m =>
        m.MarkenTag.Equals(markenTag, StringComparison.OrdinalIgnoreCase))
    ?? new Eigenmarke(markenTag, markenTag);
