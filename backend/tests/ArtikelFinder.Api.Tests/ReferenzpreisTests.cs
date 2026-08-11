using ArtikelFinder.Import;
using ArtikelFinder.Import.OpenPrices;
using Xunit;

namespace ArtikelFinder.Api.Tests;

/// <summary>
/// Der Richtpreis ist ein Mittelwert über fremde Filialen und Monate. Was in ihn eingeht
/// und was nicht, entscheidet, ob er im Markt brauchbar ist — deshalb hier festgeschrieben.
/// </summary>
public sealed class ReferenzpreisTests
{
    private static readonly DateOnly Heute = new(2026, 8, 11);
    private static readonly DateOnly VorEinemJahr = Heute.AddDays(-365);

    [Fact]
    public void UngeradeAnzahl_NimmtDenMittlerenWert()
    {
        var referenz = Referenzpreis.Aus([Beobachtung(1.29m), Beobachtung(1.49m), Beobachtung(9.99m)]);

        // Der Ausreißer nach oben verschiebt den Median nicht — genau dafür ist er da.
        Assert.NotNull(referenz);
        Assert.Equal(1.49m, referenz.Wert);
        Assert.Equal(1.29m, referenz.Niedrigster);
        Assert.Equal(9.99m, referenz.Hoechster);
        Assert.Equal(3, referenz.Anzahl);
    }

    [Fact]
    public void GeradeAnzahl_MitteltDieBeidenMittlerenAufCent()
    {
        var referenz = Referenzpreis.Aus([Beobachtung(1.00m), Beobachtung(1.05m)]);

        Assert.Equal(1.03m, referenz!.Wert);
    }

    [Fact]
    public void Stand_IstDieJuengsteErfassung()
    {
        var referenz = Referenzpreis.Aus(
        [
            new Preisbeobachtung(1.00m, new DateOnly(2025, 1, 1)),
            new Preisbeobachtung(1.20m, new DateOnly(2026, 6, 30)),
            new Preisbeobachtung(1.10m, new DateOnly(2025, 9, 9)),
        ]);

        Assert.Equal(new DateOnly(2026, 6, 30), referenz!.Stand);
    }

    [Fact]
    public void OhneBeobachtungen_GibtEsKeinenRichtpreis()
    {
        Assert.Null(Referenzpreis.Aus([]));
    }

    [Fact]
    public void Aktionspreis_ZaehltMitSeinemNormalpreis()
    {
        // Ein Angebot aus einer fremden Filiale ist kein Angebot hier. Steht der
        // Normalpreis dabei, ist er die richtige Angabe.
        var beobachtung = Preisbeobachtung.Aus(
            new OpPreis
            {
                Art = "PRODUCT",
                Waehrung = "EUR",
                Preis = 0.99m,
                IstAktionspreis = true,
                PreisOhneAktion = 1.79m,
                Datum = Heute,
            },
            VorEinemJahr);

        Assert.Equal(1.79m, beobachtung!.Value.Wert);
    }

    [Fact]
    public void AktionspreisOhneNormalpreis_WirdVerworfen()
    {
        // Sonst zöge er den Richtwert unter den Regalpreis, ohne dass man es sieht.
        var beobachtung = Preisbeobachtung.Aus(
            new OpPreis
            {
                Art = "PRODUCT",
                Waehrung = "EUR",
                Preis = 0.99m,
                IstAktionspreis = true,
                Datum = Heute,
            },
            VorEinemJahr);

        Assert.Null(beobachtung);
    }

    [Theory]
    [InlineData("CATEGORY", "EUR", 1.49, 0)]   // lose Ware ohne Barcode
    [InlineData("PRODUCT", "CHF", 1.49, 0)]    // fremde Währung
    [InlineData("PRODUCT", "EUR", 0.0, 0)]     // Preis null
    [InlineData("PRODUCT", "EUR", 2999.0, 0)]  // Tippfehler statt 29,99
    [InlineData("PRODUCT", "EUR", 1.49, 800)]  // zu alt
    public void UnbrauchbareErfassungen_WerdenVerworfen(string art, string waehrung, double preis, int alterTage)
    {
        var beobachtung = Preisbeobachtung.Aus(
            new OpPreis
            {
                Art = art,
                Waehrung = waehrung,
                Preis = (decimal)preis,
                Datum = Heute.AddDays(-alterTage),
            },
            Heute.AddDays(-730));

        Assert.Null(beobachtung);
    }

    [Fact]
    public void ErfassungOhneDatum_WirdVerworfen()
    {
        // Ohne Datum ist nicht zu beurteilen, ob der Preis von gestern oder von 2019 ist.
        var beobachtung = Preisbeobachtung.Aus(
            new OpPreis { Art = "PRODUCT", Waehrung = "EUR", Preis = 1.49m },
            VorEinemJahr);

        Assert.Null(beobachtung);
    }

    private static Preisbeobachtung Beobachtung(decimal wert) => new(wert, Heute);
}
