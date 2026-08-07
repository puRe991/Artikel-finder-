using System.Text.Json;
using ArtikelFinder.Import.OpenFoodFacts;
using Xunit;

namespace ArtikelFinder.Api.Tests;

/// <summary>
/// Die beiden Dialekte der Open-Food-Facts-Familie: die Produkt-API mit Feldparametern und
/// der Suchdienst mit Abfragesprache. Beide muessen im Importer dasselbe Ergebnis liefern.
/// </summary>
public sealed class OffQuellenTests
{
    [Fact]
    public void ProduktApi_FiltertUeberFeldparameter()
    {
        var abfrage = OffAbfrage.NachMarke(OffDatenbank.Lebensmittel, "k-classic", "germany");

        var adresse = OffClient.Adresse(abfrage, seite: 2, seitengroesse: 100);

        Assert.StartsWith("https://world.openfoodfacts.org/api/v2/search?", adresse);
        Assert.Contains("brands_tags=k-classic", adresse);
        Assert.Contains("countries_tags=germany", adresse);
        Assert.Contains("page=2", adresse);
    }

    [Fact]
    public void Suchdienst_FiltertUeberAbfragesprache()
    {
        var abfrage = OffAbfrage.NachMarke(OffDatenbank.LebensmittelSuche, "k-classic", "germany");

        var adresse = Uri.UnescapeDataString(OffClient.Adresse(abfrage, seite: 2, seitengroesse: 100));

        Assert.StartsWith("https://search.openfoodfacts.org/search?", adresse);

        // Laender stehen im Suchindex mit Sprachpraefix — ohne das findet die Abfrage nichts.
        Assert.Contains("q=brands_tags:\"k-classic\" AND countries_tags:\"en:germany\"", adresse);
    }

    [Fact]
    public void OhneLand_BleibtDerLaenderfilterWeg()
    {
        var ueberApi = OffClient.Adresse(
            OffAbfrage.NachMarke(OffDatenbank.Lebensmittel, "k-classic", string.Empty), 1, 100);

        var ueberSuche = Uri.UnescapeDataString(OffClient.Adresse(
            OffAbfrage.NachMarke(OffDatenbank.LebensmittelSuche, "k-classic", string.Empty), 1, 100));

        Assert.DoesNotContain("countries_tags", ueberApi);
        Assert.DoesNotContain("countries_tags", ueberSuche);
    }

    [Fact]
    public void SuchtrefferWerdenAufDasGemeinsameModellUmgerechnet()
    {
        // Gekuerzte echte Antwort: Treffer heissen "hits", Marken kommen als Liste.
        const string antwort = """
        {
          "count": 3090,
          "page": 1,
          "page_size": 2,
          "hits": [
            {
              "code": "4337185377532",
              "product_name": "Röstzwiebeln",
              "product_name_de": "Röstzwiebeln",
              "brands": ["K Classic", "Kaufland"],
              "quantity": "150 g",
              "categories_tags": ["en:plant-based-foods", "en:condiments"],
              "image_front_small_url": "https://images.openfoodfacts.org/beispiel.jpg"
            }
          ]
        }
        """;

        var gelesen = JsonSerializer.Deserialize<OffSucheAntwort>(antwort)!.AlsSuchantwort();

        var produkt = Assert.Single(gelesen.Produkte);
        Assert.Equal(3090, gelesen.Anzahl);
        Assert.Equal("4337185377532", produkt.Code);
        Assert.Equal("Röstzwiebeln 150 g", produkt.BesterName());
        Assert.Equal("K Classic", produkt.ErsteMarke());
        Assert.Equal(["en:plant-based-foods", "en:condiments"], produkt.KategorieTags);
        Assert.Equal("https://images.openfoodfacts.org/beispiel.jpg", produkt.BildUrl);
    }

    [Fact]
    public void EinzelneMarkeAlsText_KostetNichtDieGanzeSeite()
    {
        // Der Suchdienst liefert Marken als Liste — bis auf Ausreisser. Ein unerwarteter Typ
        // darf nicht die Seite zerlegen, auf der er steht.
        const string antwort = """
        {"count": 1, "hits": [{"code": "4337185377532", "product_name": "Rotkohl", "brands": "K Classic"}]}
        """;

        var produkt = Assert.Single(JsonSerializer.Deserialize<OffSucheAntwort>(antwort)!.AlsSuchantwort().Produkte);

        Assert.Equal("K Classic", produkt.ErsteMarke());
    }

    [Fact]
    public void DerSuchdienstIstEineEigeneQuelleNebenDerProduktApi()
    {
        // Beide Indizes decken sich nicht vollstaendig, und api/v2/search faellt regelmaessig
        // laenger aus. Deshalb wird gefragt, nicht ausgewichen.
        Assert.Contains(OffDatenbank.Lebensmittel, OffDatenbank.Alle);
        Assert.Contains(OffDatenbank.LebensmittelSuche, OffDatenbank.Alle);
    }
}
