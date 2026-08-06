using System.ComponentModel.DataAnnotations;

namespace ArtikelFinder.Shared.Dtos;

public sealed record StandortDto
{
    public required Guid Id { get; init; }
    public required Guid ArtikelId { get; init; }
    public required int MarktId { get; init; }
    public required string Gang { get; init; }
    public string? RegalBeschreibung { get; init; }

    /// <summary>X-Koordinate auf dem Grundriss (0..1, relativ zur Kartenbreite). Phase 2.</summary>
    public float? KartenX { get; init; }

    /// <summary>Y-Koordinate auf dem Grundriss (0..1, relativ zur Kartenhoehe). Phase 2.</summary>
    public float? KartenY { get; init; }

    public required DateTimeOffset ErfasstAm { get; init; }
    public string? ErfasstVon { get; init; }
}

public sealed record StandortErfassenDto : IValidatableObject
{
    /// <summary>Weglassen = Standardmarkt aus der Konfiguration.</summary>
    public int? MarktId { get; init; }

    [Required, StringLength(20)]
    public required string Gang { get; init; }

    [StringLength(300)]
    public string? RegalBeschreibung { get; init; }

    [Range(0.0, 1.0)]
    public float? KartenX { get; init; }

    [Range(0.0, 1.0)]
    public float? KartenY { get; init; }

    [StringLength(120)]
    public string? ErfasstVon { get; init; }

    public IEnumerable<ValidationResult> Validate(ValidationContext validationContext)
    {
        // Eine halbe Koordinate ist auf der Karte nicht platzierbar.
        if (KartenX.HasValue != KartenY.HasValue)
        {
            yield return new ValidationResult(
                "KartenX und KartenY muessen gemeinsam angegeben werden.",
                [nameof(KartenX), nameof(KartenY)]);
        }
    }
}
