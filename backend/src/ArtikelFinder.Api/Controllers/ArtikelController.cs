using ArtikelFinder.Api.Infrastructure;
using ArtikelFinder.Api.Services;
using ArtikelFinder.Shared.Dtos;
using Microsoft.AspNetCore.Mvc;
using Microsoft.Extensions.Options;

namespace ArtikelFinder.Api.Controllers;

[ApiController]
[Route("api/artikel")]
[Produces("application/json")]
public class ArtikelController(ArtikelService artikel, IOptions<ArtikelFinderOptions> optionen) : ControllerBase
{
    private ArtikelFinderOptions Optionen => optionen.Value;

    /// <summary>Volltextsuche mit Filtern. Ohne Parameter liefert der Endpunkt den Katalog seitenweise.</summary>
    [HttpGet]
    [ProducesResponseType<SeitenErgebnis<ArtikelListeDto>>(StatusCodes.Status200OK)]
    public async Task<ActionResult<SeitenErgebnis<ArtikelListeDto>>> Suchen(
        [FromQuery] string? q,
        [FromQuery] string? ean,
        [FromQuery] int? kategorieId,
        [FromQuery] int? marktId,
        [FromQuery] bool nurMitStandort = false,
        [FromQuery] bool nurMitWerbepreis = false,
        [FromQuery] int seite = 1,
        [FromQuery] int? groesse = null,
        CancellationToken ct = default)
    {
        var filter = new ArtikelSuchfilter
        {
            Suchbegriff = q,
            Ean = ean,
            KategorieId = kategorieId,
            MarktId = marktId ?? Optionen.StandardMarktId,
            NurMitStandort = nurMitStandort,
            NurMitWerbepreis = nurMitWerbepreis,
            Seite = Math.Max(1, seite),
            Seitengroesse = Math.Clamp(groesse ?? Optionen.StandardSeitengroesse, 1, Optionen.MaxSeitengroesse),
        };

        return Ok(await artikel.SuchenAsync(filter, ct));
    }

    [HttpGet("{id:guid}")]
    [ProducesResponseType<ArtikelDetailDto>(StatusCodes.Status200OK)]
    [ProducesResponseType(StatusCodes.Status404NotFound)]
    public async Task<ActionResult<ArtikelDetailDto>> Holen(Guid id, CancellationToken ct)
    {
        var ergebnis = await artikel.HolenAsync(id, ct);
        return ergebnis is null ? Problem($"Artikel {id} existiert nicht.", statusCode: 404) : Ok(ergebnis);
    }

    /// <summary>Barcode-Lookup. 404 ist hier ein erwartetes Ergebnis: die App bietet dann
    /// "Artikel anlegen" an.</summary>
    [HttpGet("ean/{ean}")]
    [ProducesResponseType<ArtikelDetailDto>(StatusCodes.Status200OK)]
    [ProducesResponseType(StatusCodes.Status404NotFound)]
    public async Task<ActionResult<ArtikelDetailDto>> PerEan(string ean, CancellationToken ct)
    {
        var ergebnis = await artikel.PerEanHolenAsync(ean, ct);
        return ergebnis is null ? Problem($"Zur EAN {ean} ist kein Artikel bekannt.", statusCode: 404) : Ok(ergebnis);
    }

    [HttpPost]
    [ProducesResponseType<ArtikelDetailDto>(StatusCodes.Status201Created)]
    [ProducesResponseType(StatusCodes.Status400BadRequest)]
    [ProducesResponseType(StatusCodes.Status409Conflict)]
    public async Task<ActionResult<ArtikelDetailDto>> Anlegen(
        [FromBody] ArtikelAnlegenDto eingabe,
        CancellationToken ct)
    {
        var ergebnis = await artikel.AnlegenAsync(eingabe, Optionen.StandardMarktId, ct);
        if (!ergebnis.IstErfolg)
        {
            return AlsProblem(ergebnis.Fehlerart, ergebnis.Meldung!);
        }

        return CreatedAtAction(nameof(Holen), new { id = ergebnis.Wert!.Id }, ergebnis.Wert);
    }

    [HttpPut("{id:guid}")]
    [ProducesResponseType<ArtikelDetailDto>(StatusCodes.Status200OK)]
    [ProducesResponseType(StatusCodes.Status404NotFound)]
    [ProducesResponseType(StatusCodes.Status409Conflict)]
    public async Task<ActionResult<ArtikelDetailDto>> Aendern(
        Guid id,
        [FromBody] ArtikelAendernDto eingabe,
        [FromQuery] string? geaendertVon,
        CancellationToken ct)
    {
        var ergebnis = await artikel.AendernAsync(id, eingabe, geaendertVon, ct);
        return ergebnis.IstErfolg ? Ok(ergebnis.Wert) : AlsProblem(ergebnis.Fehlerart, ergebnis.Meldung!);
    }

    [HttpDelete("{id:guid}")]
    [ProducesResponseType(StatusCodes.Status204NoContent)]
    [ProducesResponseType(StatusCodes.Status404NotFound)]
    public async Task<IActionResult> Loeschen(Guid id, CancellationToken ct)
    {
        var ergebnis = await artikel.LoeschenAsync(id, ct);
        return ergebnis.IstErfolg ? NoContent() : AlsProblem(ergebnis.Fehlerart, ergebnis.Meldung!);
    }

    [HttpGet("{id:guid}/preise")]
    [ProducesResponseType<IReadOnlyList<PreisDto>>(StatusCodes.Status200OK)]
    [ProducesResponseType(StatusCodes.Status404NotFound)]
    public async Task<ActionResult<IReadOnlyList<PreisDto>>> Preise(Guid id, CancellationToken ct)
    {
        var detail = await artikel.HolenAsync(id, ct);
        return detail is null ? Problem($"Artikel {id} existiert nicht.", statusCode: 404) : Ok(detail.Preise);
    }

    [HttpPost("{id:guid}/preise")]
    [ProducesResponseType<PreisDto>(StatusCodes.Status201Created)]
    [ProducesResponseType(StatusCodes.Status400BadRequest)]
    [ProducesResponseType(StatusCodes.Status404NotFound)]
    public async Task<ActionResult<PreisDto>> PreisErfassen(
        Guid id,
        [FromBody] PreisErfassenDto eingabe,
        CancellationToken ct)
    {
        var ergebnis = await artikel.PreisErfassenAsync(id, eingabe, Optionen.StandardMarktId, ct);
        if (!ergebnis.IstErfolg)
        {
            return AlsProblem(ergebnis.Fehlerart, ergebnis.Meldung!);
        }

        return CreatedAtAction(nameof(Preise), new { id }, ergebnis.Wert);
    }

    [HttpGet("{id:guid}/standorte")]
    [ProducesResponseType<IReadOnlyList<StandortDto>>(StatusCodes.Status200OK)]
    [ProducesResponseType(StatusCodes.Status404NotFound)]
    public async Task<ActionResult<IReadOnlyList<StandortDto>>> Standorte(Guid id, CancellationToken ct)
    {
        var detail = await artikel.HolenAsync(id, ct);
        return detail is null ? Problem($"Artikel {id} existiert nicht.", statusCode: 404) : Ok(detail.Standorte);
    }

    [HttpPost("{id:guid}/standorte")]
    [ProducesResponseType<StandortDto>(StatusCodes.Status201Created)]
    [ProducesResponseType(StatusCodes.Status400BadRequest)]
    [ProducesResponseType(StatusCodes.Status404NotFound)]
    public async Task<ActionResult<StandortDto>> StandortErfassen(
        Guid id,
        [FromBody] StandortErfassenDto eingabe,
        CancellationToken ct)
    {
        var ergebnis = await artikel.StandortErfassenAsync(id, eingabe, Optionen.StandardMarktId, ct);
        if (!ergebnis.IstErfolg)
        {
            return AlsProblem(ergebnis.Fehlerart, ergebnis.Meldung!);
        }

        return CreatedAtAction(nameof(Standorte), new { id }, ergebnis.Wert);
    }

    /// <summary>Aenderungsverlauf: wer hat wann welchen Preis/Standort erfasst.</summary>
    [HttpGet("{id:guid}/verlauf")]
    [ProducesResponseType<IReadOnlyList<VerlaufEintragDto>>(StatusCodes.Status200OK)]
    [ProducesResponseType(StatusCodes.Status404NotFound)]
    public async Task<ActionResult<IReadOnlyList<VerlaufEintragDto>>> Verlauf(Guid id, CancellationToken ct)
    {
        var ergebnis = await artikel.VerlaufAsync(id, ct);
        return ergebnis.IstErfolg ? Ok(ergebnis.Wert) : AlsProblem(ergebnis.Fehlerart, ergebnis.Meldung!);
    }

    private ObjectResult AlsProblem(Fehlerart fehlerart, string meldung) => Problem(
        detail: meldung,
        statusCode: fehlerart switch
        {
            Fehlerart.NichtGefunden => StatusCodes.Status404NotFound,
            Fehlerart.Konflikt => StatusCodes.Status409Conflict,
            _ => StatusCodes.Status400BadRequest,
        });
}
