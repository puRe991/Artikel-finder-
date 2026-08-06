using ArtikelFinder.Api.Data;
using ArtikelFinder.Api.Infrastructure;
using ArtikelFinder.Api.Services;
using ArtikelFinder.Shared.Dtos;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Options;

namespace ArtikelFinder.Api.Controllers;

[ApiController]
[Route("api/maerkte")]
[Produces("application/json")]
public class MaerkteController(
    ArtikelFinderDbContext db,
    ArtikelService artikel,
    IOptions<ArtikelFinderOptions> optionen) : ControllerBase
{
    [HttpGet]
    [ProducesResponseType<IReadOnlyList<MarktDto>>(StatusCodes.Status200OK)]
    public async Task<ActionResult<IReadOnlyList<MarktDto>>> Alle(CancellationToken ct)
    {
        var maerkte = await db.Maerkte.AsNoTracking().OrderBy(m => m.Name).ToListAsync(ct);
        return Ok(maerkte.Select(m => m.ZuDto()).ToList());
    }

    /// <summary>Alle im Markt bekannten Gaenge mit Artikelzahl — die Standort-Uebersicht des MVP.</summary>
    [HttpGet("{marktId:int}/gaenge")]
    [ProducesResponseType<IReadOnlyList<GangDto>>(StatusCodes.Status200OK)]
    public async Task<ActionResult<IReadOnlyList<GangDto>>> Gaenge(int marktId, CancellationToken ct) =>
        Ok(await artikel.GaengeAsync(marktId, ct));

    [HttpGet("{marktId:int}/gaenge/{gang}/artikel")]
    [ProducesResponseType<IReadOnlyList<ArtikelListeDto>>(StatusCodes.Status200OK)]
    public async Task<ActionResult<IReadOnlyList<ArtikelListeDto>>> ArtikelImGang(
        int marktId,
        string gang,
        CancellationToken ct) =>
        Ok(await artikel.NachGangAsync(marktId, gang, ct));


    /// <summary>Der Markt, den die App verwendet, wenn der Nutzer keinen waehlt.</summary>
    [HttpGet("standard")]
    [ProducesResponseType<MarktDto>(StatusCodes.Status200OK)]
    [ProducesResponseType(StatusCodes.Status404NotFound)]
    public async Task<ActionResult<MarktDto>> Standard(CancellationToken ct)
    {
        var markt = await db.Maerkte
            .AsNoTracking()
            .FirstOrDefaultAsync(m => m.Id == optionen.Value.StandardMarktId, ct);

        return markt is null
            ? Problem("Es ist kein Standardmarkt konfiguriert.", statusCode: 404)
            : Ok(markt.ZuDto());
    }
}
