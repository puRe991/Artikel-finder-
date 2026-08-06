using System.ComponentModel.DataAnnotations;

namespace ArtikelFinder.Shared.Dtos;

public sealed record PreisDto
{
    public required Guid Id { get; init; }
    public required Guid ArtikelId { get; init; }
    public required int MarktId { get; init; }
    public required decimal Preis { get; init; }
    public decimal? Werbepreis { get; init; }
    public DateTimeOffset? WerbepreisGueltigVon { get; init; }
    public DateTimeOffset? WerbepreisGueltigBis { get; init; }
    public required DateTimeOffset ErfasstAm { get; init; }
    public string? ErfasstVon { get; init; }

    /// <summary>Serverseitig ausgewertet: laeuft aktuell eine Werbeaktion?</summary>
    public required bool WerbepreisAktiv { get; init; }

    /// <summary>Der Preis, den der Kunde heute zahlt.</summary>
    public decimal GueltigerPreis => WerbepreisAktiv && Werbepreis.HasValue ? Werbepreis.Value : Preis;
}

public sealed record PreisErfassenDto : IValidatableObject
{
    /// <summary>Weglassen = Standardmarkt aus der Konfiguration (Kaufland Giessen).</summary>
    public int? MarktId { get; init; }

    [Range(0.01, 100_000)]
    public required decimal Preis { get; init; }

    [Range(0.01, 100_000)]
    public decimal? Werbepreis { get; init; }

    public DateTimeOffset? WerbepreisGueltigVon { get; init; }
    public DateTimeOffset? WerbepreisGueltigBis { get; init; }

    [StringLength(120)]
    public string? ErfasstVon { get; init; }

    public IEnumerable<ValidationResult> Validate(ValidationContext validationContext)
    {
        if (Werbepreis.HasValue && Werbepreis.Value > Preis)
        {
            yield return new ValidationResult(
                "Der Werbepreis darf nicht ueber dem Normalpreis liegen.",
                [nameof(Werbepreis)]);
        }

        if (WerbepreisGueltigVon.HasValue && WerbepreisGueltigBis.HasValue &&
            WerbepreisGueltigBis.Value < WerbepreisGueltigVon.Value)
        {
            yield return new ValidationResult(
                "Das Ende des Werbezeitraums liegt vor dem Beginn.",
                [nameof(WerbepreisGueltigBis)]);
        }

        if (!Werbepreis.HasValue && (WerbepreisGueltigVon.HasValue || WerbepreisGueltigBis.HasValue))
        {
            yield return new ValidationResult(
                "Ein Werbezeitraum ohne Werbepreis ergibt keinen Sinn.",
                [nameof(Werbepreis)]);
        }
    }
}
