using ArtikelFinder.Shared;
using Xunit;

namespace ArtikelFinder.Api.Tests;

public class SuchtextTests
{
    [Theory]
    [InlineData("Müller Käse!", "mueller kaese")]
    [InlineData("  Bio   Vollmilch 3,8%  ", "bio vollmilch 3 8")]
    [InlineData("Crème fraîche", "creme fraiche")]
    [InlineData("Straßenkäse", "strassenkaese")]
    [InlineData(null, "")]
    [InlineData("   ", "")]
    public void Normalisieren_LiefertKanonischeForm(string? eingabe, string erwartet) =>
        Assert.Equal(erwartet, Suchtext.Normalisieren(eingabe));

    [Fact]
    public void FuerIndex_EnthaeltBeideUmlautSchreibweisen()
    {
        var index = Suchtext.FuerIndex("Bärenmarke");

        // Wer "barenmarke" tippt, soll den Artikel genauso finden wie mit "baerenmarke".
        Assert.Contains("baerenmarke", index);
        Assert.Contains("barenmarke", index);
    }

    [Fact]
    public void FuerIndex_DupliziertNichtOhneUmlaute()
    {
        Assert.Equal("vollmilch", Suchtext.FuerIndex("Vollmilch"));
    }

    [Fact]
    public void Normalisieren_ErzeugtKeineLikeWildcards()
    {
        // Der Suchservice baut LIKE-Muster aus dem normalisierten Text; kaemen dort
        // % oder _ durch, koennte eine Suche den halben Katalog zurueckgeben.
        var normalisiert = Suchtext.Normalisieren("100% _Rabatt_ 50%");

        Assert.DoesNotContain('%', normalisiert);
        Assert.DoesNotContain('_', normalisiert);
    }
}
