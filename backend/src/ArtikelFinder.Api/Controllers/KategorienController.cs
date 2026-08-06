using ArtikelFinder.Api.Services;
using ArtikelFinder.Shared.Dtos;
using Microsoft.AspNetCore.Mvc;

namespace ArtikelFinder.Api.Controllers;

[ApiController]
[Route("api/kategorien")]
[Produces("application/json")]
public class KategorienController(KategorieService kategorien) : ControllerBase
{
    /// <summary>Der komplette Kategorienbaum, flach mit Pfad — die App baut daraus ihren Filter.</summary>
    [HttpGet]
    [ProducesResponseType<IReadOnlyList<KategorieDto>>(StatusCodes.Status200OK)]
    public async Task<ActionResult<IReadOnlyList<KategorieDto>>> Alle(CancellationToken ct) =>
        Ok(await kategorien.AlleAsync(ct));

    [HttpPost]
    [ProducesResponseType<KategorieDto>(StatusCodes.Status201Created)]
    [ProducesResponseType(StatusCodes.Status400BadRequest)]
    [ProducesResponseType(StatusCodes.Status409Conflict)]
    public async Task<ActionResult<KategorieDto>> Anlegen(
        [FromBody] KategorieAnlegenDto eingabe,
        CancellationToken ct)
    {
        var ergebnis = await kategorien.AnlegenAsync(eingabe, ct);
        if (ergebnis.IstErfolg)
        {
            return CreatedAtAction(nameof(Alle), null, ergebnis.Wert);
        }

        return Problem(
            detail: ergebnis.Meldung,
            statusCode: ergebnis.Fehlerart == Fehlerart.Konflikt
                ? StatusCodes.Status409Conflict
                : StatusCodes.Status400BadRequest);
    }
}
