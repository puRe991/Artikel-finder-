namespace ArtikelFinder.Import;

public sealed class Importstatistik
{
    public int Gelesen { get; set; }
    public int Neu { get; set; }
    public int Ergaenzt { get; set; }
    public int Uebersprungen { get; set; }
    public int OhneName { get; set; }
    public int OhneGueltigeEan { get; set; }
    public int Geschuetzt { get; set; }

    /// <summary>
    /// Seiten, die auch nach allen Wiederholungen nicht geladen werden konnten. Gehört in
    /// die Zusammenfassung: sonst sieht eine Warengruppe, deren Seiten ausgefallen sind,
    /// genauso aus wie eine, die es wirklich nicht gibt.
    /// </summary>
    public int SeitenFehlgeschlagen { get; set; }

    public void Dazu(Importstatistik andere)
    {
        Gelesen += andere.Gelesen;
        Neu += andere.Neu;
        Ergaenzt += andere.Ergaenzt;
        Uebersprungen += andere.Uebersprungen;
        OhneName += andere.OhneName;
        OhneGueltigeEan += andere.OhneGueltigeEan;
        Geschuetzt += andere.Geschuetzt;
        SeitenFehlgeschlagen += andere.SeitenFehlgeschlagen;
    }

    public override string ToString()
    {
        var text = $"gelesen {Gelesen}, neu {Neu}, ergänzt {Ergaenzt}, übersprungen {Uebersprungen} "
            + $"(ohne Name {OhneName}, ohne gültige EAN {OhneGueltigeEan}, Nutzerdaten geschützt {Geschuetzt})";

        return SeitenFehlgeschlagen == 0
            ? text
            : $"{text} — ACHTUNG: {SeitenFehlgeschlagen} Seite(n) nicht geladen, erneut ausführen";
    }
}
