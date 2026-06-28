using ApiaryServer.Application.DTOs;
using ApiaryServer.Application.Exceptions;
using ApiaryServer.Application.Interfaces;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace ApiaryServer.Api.Controllers
{
    [ApiController]
    [Route("api/extractions")]
    [Authorize]
    public class HiveExtractionController : ApiControllerBase
    {
        private readonly IHiveExtractionService _extractionService;

        public HiveExtractionController(IHiveExtractionService extractionService)
        {
            _extractionService = extractionService;
        }

        [HttpGet]
        public async Task<IActionResult> GetAll()
        {
            try
            {
                var userId = GetUserId();
                var extractions = await _extractionService.GetAllExtractionsAsync(userId);
                return Ok(extractions);
            }
            catch (System.UnauthorizedAccessException ex)
            {
                return Unauthorized(new { error = ex.Message });
            }
        }

        [HttpGet("apiary/{apiaryId}")]
        public async Task<IActionResult> GetByApiaryId(Guid apiaryId)
        {
            try
            {
                var userId = GetUserId();
                var extractions = await _extractionService.GetExtractionsByApiaryIdAsync(apiaryId, userId);
                return Ok(extractions);
            }
            catch (System.UnauthorizedAccessException ex)
            {
                return Unauthorized(new { error = ex.Message });
            }
        }

        [HttpGet("hive/{hiveId}")]
        public async Task<IActionResult> GetByHiveId(Guid hiveId)
        {
            try
            {
                var userId = GetUserId();
                var extractions = await _extractionService.GetExtractionsByHiveIdAsync(hiveId, userId);
                return Ok(extractions);
            }
            catch (System.UnauthorizedAccessException ex)
            {
                return Unauthorized(new { error = ex.Message });
            }
            catch (HiveNotFoundException ex)
            {
                return NotFound(new { error = ex.Message });
            }
        }

        [HttpGet("{id}")]
        public async Task<IActionResult> GetById(Guid id)
        {
            try
            {
                var userId = GetUserId();
                var extraction = await _extractionService.GetExtractionByIdAsync(id, userId);
                return Ok(extraction);
            }
            catch (ExtractionNotFoundException ex)
            {
                return NotFound(new { error = ex.Message });
            }
            catch (System.UnauthorizedAccessException ex)
            {
                return Unauthorized(new { error = ex.Message });
            }
        }

        [HttpPost]
        public async Task<IActionResult> Create([FromBody] CreateExtractionRequest dto)
        {
            if (!ModelState.IsValid)
                return BadRequest(ModelState);

            try
            {
                var userId = GetUserId();
                var extraction = await _extractionService.CreateExtractionAsync(dto, userId);
                return CreatedAtAction(nameof(GetById), new { id = extraction.Id }, extraction);
            }
            catch (HiveNotFoundException ex)
            {
                return NotFound(new { error = ex.Message });
            }
            catch (System.UnauthorizedAccessException ex)
            {
                return Unauthorized(new { error = ex.Message });
            }
        }

        [HttpPut("{id}")]
        public async Task<IActionResult> Update(Guid id, [FromBody] UpdateExtractionRequest dto)
        {
            if (!ModelState.IsValid)
                return BadRequest(ModelState);

            try
            {
                var userId = GetUserId();
                var extraction = await _extractionService.UpdateExtractionAsync(id, dto, userId);
                return Ok(extraction);
            }
            catch (ExtractionNotFoundException ex)
            {
                return NotFound(new { error = ex.Message });
            }
            catch (System.UnauthorizedAccessException ex)
            {
                return Unauthorized(new { error = ex.Message });
            }
        }

        [HttpDelete("{id}")]
        public async Task<IActionResult> Delete(Guid id)
        {
            try
            {
                var userId = GetUserId();
                await _extractionService.DeleteExtractionAsync(id, userId);
                return NoContent();
            }
            catch (ExtractionNotFoundException ex)
            {
                return NotFound(new { error = ex.Message });
            }
            catch (System.UnauthorizedAccessException ex)
            {
                return Unauthorized(new { error = ex.Message });
            }
        }
    }
}
