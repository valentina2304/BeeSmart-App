using ApiaryServer.Application.DTOs;
using ApiaryServer.Application.Interfaces;
using ApiaryServer.Application.Exceptions;
using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.Authorization;

namespace ApiaryServer.Api.Controllers
{
    [ApiController]
    [Route("api/hives")]
    [Authorize]
    public class HiveController : ApiControllerBase
    {
        private readonly IHiveService _hiveService;

        public HiveController(IHiveService hiveService)
        {
            _hiveService = hiveService;
        }

        /// <summary>
        /// Get all hives for the current user
        /// </summary>
        [HttpGet]
        public async Task<IActionResult> GetAll()
        {
            try
            {
                var userId = GetUserId();
                var hives = await _hiveService.GetAllHivesAsync(userId);
                return Ok(hives);
            }
            catch (System.UnauthorizedAccessException ex)
            {
                return Unauthorized(new { error = ex.Message });
            }
        }

        /// <summary>
        /// Get all hives for a specific apiary
        /// </summary>
        [HttpGet("apiary/{apiaryId}")]
        public async Task<IActionResult> GetByApiaryId(Guid apiaryId)
        {
            try
            {
                var userId = GetUserId();
                var hives = await _hiveService.GetHivesByApiaryIdAsync(apiaryId, userId);
                return Ok(hives);
            }
            catch (System.UnauthorizedAccessException ex)
            {
                return Unauthorized(new { error = ex.Message });
            }
        }

        /// <summary>
        /// Get a specific hive by ID
        /// </summary>
        [HttpGet("{id}")]
        public async Task<IActionResult> GetById(Guid id)
        {
            try
            {
                var userId = GetUserId();
                var hive = await _hiveService.GetHiveByIdAsync(id, userId);
                return Ok(hive);
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

        /// <summary>
        /// Create a new hive in a specific apiary
        /// </summary>
        [HttpPost("apiary/{apiaryId}")]
        public async Task<IActionResult> Create(Guid apiaryId, [FromBody] CreateHiveRequest dto)
        {
            if (!ModelState.IsValid)
                return BadRequest(ModelState);

            try
            {
                var userId = GetUserId();
                var hive = await _hiveService.CreateHiveAsync(apiaryId, dto, userId);
                return CreatedAtAction(nameof(GetById), new { id = hive.Id }, hive);
            }
            catch (ApiaryNotFoundException ex)
            {
                return NotFound(new { error = ex.Message });
            }
            catch (System.UnauthorizedAccessException ex)
            {
                return Unauthorized(new { error = ex.Message });
            }
        }

        /// <summary>
        /// Update an existing hive
        /// </summary>
        [HttpPut("{id}")]
        public async Task<IActionResult> Update(Guid id, [FromBody] UpdateHiveRequest dto)
        {
            if (!ModelState.IsValid)
                return BadRequest(ModelState);

            try
            {
                var userId = GetUserId();
                var hive = await _hiveService.UpdateHiveAsync(id, dto, userId);
                return Ok(hive);
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

        /// <summary>
        /// Delete a hive
        /// </summary>
        [HttpDelete("{id}")]
        public async Task<IActionResult> Delete(Guid id)
        {
            try
            {
                var userId = GetUserId();
                await _hiveService.DeleteHiveAsync(id, userId);
                return NoContent();
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
    }
}