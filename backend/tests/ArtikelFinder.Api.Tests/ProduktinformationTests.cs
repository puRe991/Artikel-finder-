using ArtikelFinder.Import;
using Xunit;

namespace ArtikelFinder.Api.Tests;

/// <summary>
/// Die Angaben landen in einer App, die im Laden Auskunft gibt. Ein falsch beschriftetes
/// Allergen ist dort kein Schoenheitsfehler — deshalb steht hier vor allem, was <b>nicht</b>
/// durchkommen darf.
/// </summary>
public class ProduktinformationTests
{
    [Fact]
    public void Nur_die_kennzeichnungspflichtigen_Allergene_kommen_durch()
    {
        // Genau so liefert Open Food Facts es: echte Tags neben Bruchstuecken, die es aus
        // der Zutatenliste geraten hat.
        string[] tags =
        [
            "en:milk", "en:nuts", "en:soybeans",
            "en:Butterreinfett", "en:Magermilchpulver", "en:Mandelstückchen", "en:Molkenerzeugnis",
        ];

        var gelesen = Produktinformation.AllergeneLesen(tags);

        Assert.Equal("Milch, Schalenfrüchte, Soja", gelesen);
    }

    [Fact]
    public void Alle_vierzehn_Allergene_sind_uebersetzt()
    {
        string[] tags =
        [
            "en:gluten", "en:crustaceans", "en:eggs", "en:fish", "en:peanuts", "en:soybeans",
            "en:milk", "en:nuts", "en:celery", "en:mustard", "en:sesame-seeds",
            "en:sulphur-dioxide-and-sulphites", "en:lupin", "en:molluscs",
        ];

        var gelesen = Produktinformation.AllergeneLesen(tags);

        Assert.NotNull(gelesen);
        Assert.Equal(14, gelesen!.Split(", ").Length);
        Assert.DoesNotContain("en:", gelesen);
    }

    [Fact]
    public void Ohne_bekannte_Allergene_bleibt_es_leer_statt_erfunden()
    {
        // Wichtig: null heisst „keine Angabe", nicht „keine Allergene". Die App zeigt das
        // ausdruecklich als fehlende Angabe an.
        Assert.Null(Produktinformation.AllergeneLesen(["en:Weizenmehl", "en:irgendwas"]));
        Assert.Null(Produktinformation.AllergeneLesen([]));
        Assert.Null(Produktinformation.AllergeneLesen(null));
    }

    [Fact]
    public void Dieselbe_Angabe_erscheint_nur_einmal()
    {
        // OFF fuehrt Bio unter mehreren Tags gleichzeitig.
        var gelesen = Produktinformation.AuszeichnungenLesen(["en:organic", "en:eu-organic", "de:bio"]);

        Assert.Equal("Bio", gelesen);
    }

    [Fact]
    public void Auszeichnungen_ohne_Nutzen_werden_weggelassen()
    {
        var gelesen = Produktinformation.AuszeichnungenLesen(
            ["en:vegan", "fr:triman", "en:rainforest-alliance-cocoa", "en:gluten-free"]);

        Assert.Equal("Vegan, Glutenfrei", gelesen);
    }

    [Fact]
    public void Naehrwerte_werden_in_Packungsreihenfolge_geschrieben()
    {
        var naehrwerte = new Dictionary<string, object?>
        {
            ["salt_100g"] = 0.2,
            ["energy-kcal_100g"] = 250.0,
            ["fat_100g"] = 12.5,
            ["sugars_100g"] = 18.86,
            ["proteins_serving"] = 99.0, // je Portion — gehoert nicht in die Tabelle
        };

        var gelesen = Produktinformation.NaehrwerteLesen(naehrwerte);

        Assert.Equal("kcal=250;fett=12.5;zucker=18.86;salz=0.2", gelesen);
    }

    [Fact]
    public void Naehrwerte_kommen_auch_als_Zeichenkette_und_mit_Komma()
    {
        var naehrwerte = new Dictionary<string, object?>
        {
            ["energy-kcal_100g"] = "250",
            ["fat_100g"] = "12,5",
        };

        Assert.Equal("kcal=250;fett=12.5", Produktinformation.NaehrwerteLesen(naehrwerte));
    }

    [Fact]
    public void Unsinnige_Naehrwerte_werden_verworfen()
    {
        var naehrwerte = new Dictionary<string, object?>
        {
            ["energy-kcal_100g"] = -5.0,
            ["fat_100g"] = 999_999.0,
            ["salz_100g"] = "keine Angabe",
            ["salt_100g"] = 1.2,
        };

        Assert.Equal("salz=1.2", Produktinformation.NaehrwerteLesen(naehrwerte));
    }

    [Theory]
    [InlineData("a", "a")]
    [InlineData("E", "e")]
    [InlineData("unknown", null)]
    [InlineData("not-applicable", null)]
    [InlineData("", null)]
    [InlineData(null, null)]
    public void Nutriscore_nur_als_Note_von_a_bis_e(string? roh, string? erwartet)
    {
        Assert.Equal(erwartet, Produktinformation.NutriscoreLesen(roh));
    }

    [Fact]
    public void Zutaten_werden_von_Zeilenumbruechen_befreit()
    {
        // Tabulator und Umbruch wuerden die Katalogdatei zerlegen.
        var gelesen = Produktinformation.ZutatenLesen("Milch,\tZucker,\nKakaobutter   (7%)");

        Assert.Equal("Milch, Zucker, Kakaobutter (7%)", gelesen);
    }

    [Fact]
    public void Zu_kurze_Zutatenlisten_sagen_nichts_aus()
    {
        Assert.Null(Produktinformation.ZutatenLesen("Zucker"));
        Assert.Null(Produktinformation.ZutatenLesen("   "));
        Assert.Null(Produktinformation.ZutatenLesen(null));
    }

    [Fact]
    public void Sehr_lange_Zutatenlisten_werden_gekuerzt()
    {
        var lang = string.Join(", ", Enumerable.Repeat("Zutat", 500));

        var gelesen = Produktinformation.ZutatenLesen(lang);

        Assert.NotNull(gelesen);
        Assert.True(gelesen!.Length <= 1502, $"Erwartet höchstens 1502 Zeichen, waren {gelesen.Length}");
        Assert.EndsWith("…", gelesen);
    }
}
