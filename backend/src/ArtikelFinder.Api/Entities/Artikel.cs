using ArtikelFinder.Shared.Enums;

namespace ArtikelFinder.Api.Entities;

public class Artikel
{
    public Guid Id { get; set; }

    public required string Name { get; set; }

    /// <summary>
    /// Normalisierte Fassung von Name + Marke fuer die Volltextsuche.
    /// Wird ausschliesslich im DbContext gepflegt, nie von aussen gesetzt.
    /// </summary>
    public string SuchText { get; set; } = string.Empty;

    public string? Marke { get; set; }

    /// <summary>EAN-8/13 oder UPC, nur Ziffern. Eindeutig, sofern gesetzt.</summary>
    public string? Ean { get; set; }

    /// <summary>Interne Artikelnummer des Marktes, falls auf dem Regaletikett ablesbar.</summary>
    public string? Artikelnummer { get; set; }

    public int? KategorieId { get; set; }
    public Kategorie? Kategorie { get; set; }

    public string? BildUrl { get; set; }

    /// <summary>
    /// Richtwert aus Open Prices: Median der Preise, die Freiwillige zu dieser EAN in
    /// deutschen Laeden erfasst haben.
    ///
    /// Bewusst am Artikel und nicht in <see cref="Preise"/>: der Wert gehoert zu keinem
    /// Markt und zu keinem Erfassungszeitpunkt im eigenen Markt. Er dient der Orientierung,
    /// solange kein eigener Preis erfasst ist, und wird von einem Importlauf ueberschrieben —
    /// eigene Preise dagegen nie.
    /// </summary>
    public decimal? Referenzpreis { get; set; }

    /// <summary>Guenstigste und teuerste eingegangene Erfassung — die Spanne sagt, wie
    /// einheitlich der Preis ueber die Ketten hinweg ist.</summary>
    public decimal? ReferenzpreisNiedrigster { get; set; }

    public decimal? ReferenzpreisHoechster { get; set; }

    /// <summary>Zahl der Erfassungen hinter dem Median. Ein Median aus einer einzigen
    /// Erfassung ist etwas anderes als einer aus zwanzig.</summary>
    public int? ReferenzpreisAnzahl { get; set; }

    /// <summary>Datum der juengsten eingegangenen Erfassung.</summary>
    public DateOnly? ReferenzpreisStand { get; set; }

    public Erstellerquelle ErstelltVon { get; set; } = Erstellerquelle.Nutzer;

    public DateTimeOffset ErstelltAm { get; set; }

    public DateTimeOffset? GeaendertAm { get; set; }

    public ICollection<Preis> Preise { get; set; } = [];
    public ICollection<Standort> Standorte { get; set; } = [];
    public ICollection<Verlaufseintrag> Verlauf { get; set; } = [];
}
