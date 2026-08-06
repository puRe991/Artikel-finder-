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

    public void Dazu(Importstatistik andere)
    {
        Gelesen += andere.Gelesen;
        Neu += andere.Neu;
        Ergaenzt += andere.Ergaenzt;
        Uebersprungen += andere.Uebersprungen;
        OhneName += andere.OhneName;
        OhneGueltigeEan += andere.OhneGueltigeEan;
        Geschuetzt += andere.Geschuetzt;
    }

    public override string ToString() =>
        $"gelesen {Gelesen}, neu {Neu}, ergänzt {Ergaenzt}, übersprungen {Uebersprungen} "
        + $"(ohne Name {OhneName}, ohne gültige EAN {OhneGueltigeEan}, Nutzerdaten geschützt {Geschuetzt})";
}
