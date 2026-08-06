using ArtikelFinder.Import;
using ArtikelFinder.Import.OpenFoodFacts;
using Xunit;

namespace ArtikelFinder.Api.Tests;

public sealed class KategoriezuordnungTests
{
    [Fact]
    public void DerSpeziellsteTagGewinnt()
    {
        // Open Food Facts sortiert die Tags von der Ober- zur Unterkategorie. Gewaenne der
        // erste Treffer, landete jeder Joghurt und jeder Kaese unter "Molkereiprodukte".
        var produkt = MitTags("en:dairies", "en:fermented-milk-products", "en:cheeses");

        Assert.Equal("Käse", Kategoriezuordnung.Fuer(produkt, standardKategorie: null));
    }

    [Fact]
    public void UnbekannteUnterkategorie_FaelltAufDieNaechstgroebereZurueck()
    {
        var produkt = MitTags("en:dairies", "en:milks", "en:semi-skimmed-milks-in-glass-bottles");

        Assert.Equal("Milch", Kategoriezuordnung.Fuer(produkt, standardKategorie: null));
    }

    [Fact]
    public void GarKeinBekannterTag_LiefertDieStandardkategorie()
    {
        var produkt = MitTags("en:incorrect-product-type", "en:non-food-products");

        Assert.Equal("Drogerie", Kategoriezuordnung.Fuer(produkt, standardKategorie: "Drogerie"));
        Assert.Null(Kategoriezuordnung.Fuer(produkt, standardKategorie: null));
    }

    [Fact]
    public void OhneTags_LiefertDieStandardkategorie()
    {
        Assert.Equal("Tierbedarf", Kategoriezuordnung.Fuer(new OffProdukt(), standardKategorie: "Tierbedarf"));
    }

    [Theory]
    // Quer durch die Datenbanken der Familie — Lebensmittel, Drogerie, Haushalt, Tierbedarf.
    [InlineData("en:toilet-papers", "Papierwaren")]
    [InlineData("en:wet-cat-food", "Katzenfutter")]
    [InlineData("en:office-supplies", "Haushalt & Sonstiges")]
    [InlineData("en:natural-mineral-waters", "Wasser")]
    [InlineData("en:prepared-meats", "Wurstwaren")]
    [InlineData("en:frozen-vegetables", "Tiefkühlgemüse")]
    [InlineData("en:toothpastes", "Körperpflege")]
    public void BekannteTags_LandenInDerRichtigenKategorie(string tag, string erwartet)
    {
        Assert.Equal(erwartet, Kategoriezuordnung.Fuer(MitTags(tag), standardKategorie: null));
    }

    [Fact]
    public void JedeZugeordneteKategorie_GibtEsImStartraster()
    {
        // Eine Kategorie, die im Raster fehlt, faellt beim Import stillschweigend weg: der
        // Katalogschreiber findet keine Id und laesst das Feld leer.
        var raster = Api.Data.Startdaten.Kategorienamen;

        var unbekannt = AlleZielkategorien().Where(k => !raster.Contains(k)).ToList();

        Assert.Empty(unbekannt);
    }

    private static IEnumerable<string> AlleZielkategorien()
    {
        // Ueber die oeffentliche Schnittstelle: fuer jeden bekannten Tag die Kategorie holen.
        foreach (var tag in Kategoriezuordnung.BekannteTags)
        {
            var kategorie = Kategoriezuordnung.Fuer(MitTags(tag), standardKategorie: null);
            if (kategorie is not null)
            {
                yield return kategorie;
            }
        }
    }

    private static OffProdukt MitTags(params string[] tags) => new() { KategorieTags = [.. tags] };
}
