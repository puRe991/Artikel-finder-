namespace ArtikelFinder.Api.Entities;

/// <summary>Hierarchische Warengruppe. Die Hierarchie ist flach genug, um sie komplett
/// in den Speicher zu laden (Kategorienbaum, keine rekursiven Queries noetig).</summary>
public class Kategorie
{
    public int Id { get; set; }

    public required string Name { get; set; }

    public int? ParentKategorieId { get; set; }
    public Kategorie? ParentKategorie { get; set; }

    public ICollection<Kategorie> Unterkategorien { get; set; } = [];
    public ICollection<Artikel> Artikel { get; set; } = [];
}
