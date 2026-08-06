namespace ArtikelFinder.Api.Services;

public enum Fehlerart
{
    Keiner = 0,
    NichtGefunden = 1,
    Konflikt = 2,
    Ungueltig = 3,
}

/// <summary>
/// Ergebnis einer Service-Operation. Bewusst kein Exception-Flow: "EAN gibt es schon" ist
/// beim Scannen der Normalfall, keine Ausnahme.
/// </summary>
public readonly record struct Ergebnis<T>
{
    private Ergebnis(T? wert, Fehlerart fehlerart, string? meldung)
    {
        Wert = wert;
        Fehlerart = fehlerart;
        Meldung = meldung;
    }

    public T? Wert { get; }
    public Fehlerart Fehlerart { get; }
    public string? Meldung { get; }

    public bool IstErfolg => Fehlerart == Fehlerart.Keiner;

    public static Ergebnis<T> Erfolg(T wert) => new(wert, Fehlerart.Keiner, null);
    public static Ergebnis<T> NichtGefunden(string meldung) => new(default, Fehlerart.NichtGefunden, meldung);
    public static Ergebnis<T> Konflikt(string meldung) => new(default, Fehlerart.Konflikt, meldung);
    public static Ergebnis<T> Ungueltig(string meldung) => new(default, Fehlerart.Ungueltig, meldung);
}
