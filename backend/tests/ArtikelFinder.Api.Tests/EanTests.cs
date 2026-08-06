using ArtikelFinder.Shared;
using Xunit;

namespace ArtikelFinder.Api.Tests;

public class EanTests
{
    [Theory]
    [InlineData("4008400202990", "4008400202990")]
    [InlineData(" 4008-400-202990 ", "4008400202990")]
    [InlineData("0000000000000", "0000000000000")]
    [InlineData("123", null)]          // zu kurz
    [InlineData("123456789012345", null)] // zu lang
    [InlineData("", null)]
    [InlineData(null, null)]
    public void Normalisieren_RaeumtEingabeAuf(string? eingabe, string? erwartet) =>
        Assert.Equal(erwartet, Ean.Normalisieren(eingabe));

    [Theory]
    [InlineData("4008400202990")] // EAN-13
    [InlineData("4045317058067")] // EAN-13, aus dem Open-Food-Facts-Import
    [InlineData("40084015")]      // EAN-8
    public void PruefzifferKorrekt_AkzeptiertEchteBarcodes(string ean) =>
        Assert.True(Ean.PruefzifferKorrekt(ean));

    [Theory]
    [InlineData("4008400202991")] // letzte Ziffer verdreht
    [InlineData("4008400202900")] // Ziffern vertauscht
    [InlineData("123456789")]     // ungueltige Laenge
    public void PruefzifferKorrekt_LehntTippfehlerAb(string ean) =>
        Assert.False(Ean.PruefzifferKorrekt(ean));
}
