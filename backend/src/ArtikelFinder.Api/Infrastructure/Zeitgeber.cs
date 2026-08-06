namespace ArtikelFinder.Api.Infrastructure;

/// <summary>Abstrahiert die Uhr, damit Werbepreis-Zeitraeume testbar bleiben.</summary>
public interface IZeitgeber
{
    DateTimeOffset Jetzt { get; }
}

public sealed class SystemZeitgeber : IZeitgeber
{
    /// <summary>Bewusst UTC: SQLite sortiert DateTimeOffset lexikografisch, das ist nur
    /// bei einheitlichem Offset korrekt.</summary>
    public DateTimeOffset Jetzt => DateTimeOffset.UtcNow;
}
