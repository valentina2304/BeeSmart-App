using ApiaryServer.Application.DTOs;
using ApiaryServer.Application.Exceptions;
using ApiaryServer.Application.Interfaces;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace ApiaryServer.Api.Controllers
{
    [ApiController]
    [Route("api/treatments")]
    [Authorize]
    public class HiveTreatmentController : ApiControllerBase
    {
        private readonly IHiveTreatmentService _treatmentService;

        public HiveTreatmentController(IHiveTreatmentService treatmentService)
        {
            _treatmentService = treatmentService;
        }

        [HttpGet]
        public async Task<IActionResult> GetAll()
        {
            try
            {
                var userId = GetUserId();
                var treatments = await _treatmentService.GetAllTreatmentsAsync(userId);
                return Ok(treatments);
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
                var treatments = await _treatmentService.GetTreatmentsByApiaryIdAsync(apiaryId, userId);
                return Ok(treatments);
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
                var treatments = await _treatmentService.GetTreatmentsByHiveIdAsync(hiveId, userId);
                return Ok(treatments);
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
                var treatment = await _treatmentService.GetTreatmentByIdAsync(id, userId);
                return Ok(treatment);
            }
            catch (TreatmentNotFoundException ex)
            {
                return NotFound(new { error = ex.Message });
            }
            catch (System.UnauthorizedAccessException ex)
            {
                return Unauthorized(new { error = ex.Message });
            }
        }

        [HttpPost]
        public async Task<IActionResult> Create([FromBody] CreateTreatmentRequest dto)
        {
            if (!ModelState.IsValid)
                return BadRequest(ModelState);

            try
            {
                var userId = GetUserId();
                var treatment = await _treatmentService.CreateTreatmentAsync(dto, userId);
                return CreatedAtAction(nameof(GetById), new { id = treatment.Id }, treatment);
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
        public async Task<IActionResult> Update(Guid id, [FromBody] UpdateTreatmentRequest dto)
        {
            if (!ModelState.IsValid)
                return BadRequest(ModelState);

            try
            {
                var userId = GetUserId();
                var treatment = await _treatmentService.UpdateTreatmentAsync(id, dto, userId);
                return Ok(treatment);
            }
            catch (TreatmentNotFoundException ex)
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
                await _treatmentService.DeleteTreatmentAsync(id, userId);
                return NoContent();
            }
            catch (TreatmentNotFoundException ex)
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
