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

    // --- Angaben fuer die Auskunft im Laden. Quelle: Open Food Facts, siehe Produktinformation. ---

    /// <summary>Fuellmenge wie auf der Packung, z.B. "250 g".</summary>
    public string? Menge { get; set; }

    /// <summary>Kennzeichnungspflichtige Allergene auf Deutsch, kommagetrennt.</summary>
    public string? Allergene { get; set; }

    /// <summary>„Kann Spuren enthalten von …", gleiche Form wie <see cref="Allergene"/>.</summary>
    public string? Spuren { get; set; }

    /// <summary>Bio, Vegan, Glutenfrei … kommagetrennt.</summary>
    public string? Auszeichnungen { get; set; }

    /// <summary>Naehrwerte je 100 g als <c>kcal=250;fett=12.5</c>.</summary>
    public string? Naehrwerte { get; set; }

    /// <summary>Nutri-Score a–e.</summary>
    public string? Nutriscore { get; set; }

    public string? Zutaten { get; set; }

    public Erstellerquelle ErstelltVon { get; set; } = Erstellerquelle.Nutzer;

    public DateTimeOffset ErstelltAm { get; set; }

    public DateTimeOffset? GeaendertAm { get; set; }

    public ICollection<Preis> Preise { get; set; } = [];
    public ICollection<Standort> Standorte { get; set; } = [];
    public ICollection<Verlaufseintrag> Verlauf { get; set; } = [];
}
