using ApiaryServer.Application.DTOs;
using ApiaryServer.Application.Interfaces;
using ApiaryServer.Application.Exceptions;
using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.Authorization;
using System;

namespace ApiaryServer.Api.Controllers
{
    [ApiController]
    [Route("api/apiaries")]
    [Authorize]
    public class ApiaryController : ApiControllerBase
    {
        private readonly IApiaryService _apiaryService;

        public ApiaryController(IApiaryService apiaryService)
        {
            _apiaryService = apiaryService;
        }

        /// <summary>
        /// Get all apiaries for the current user
        /// </summary>
        [HttpGet]
        public async Task<IActionResult> GetAll()
        {
            try
            {
                var userId = GetUserId();
                var apiaries = await _apiaryService.GetAllApiariesAsync(userId);
                return Ok(apiaries);
            }
            catch (System.UnauthorizedAccessException ex)
            {
                return Unauthorized(new { error = ex.Message });
            }
        }

        /// <summary>
        /// Get a specific apiary by ID with its hives
        /// </summary>
        [HttpGet("{id}")]
        public async Task<IActionResult> GetById(Guid id)
        {
            try
            {
                var userId = GetUserId();
                var apiary = await _apiaryService.GetApiaryByIdAsync(id, userId);
                return Ok(apiary);
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
        /// Create a new apiary
        /// </summary>
        [HttpPost]
        public async Task<IActionResult> Create([FromBody] CreateApiaryRequest dto)
        {
            if (!ModelState.IsValid)
                return BadRequest(ModelState);

            try
            {
                var userId = GetUserId();
                var apiary = await _apiaryService.CreateApiaryAsync(dto, userId);
                return CreatedAtAction(nameof(GetById), new { id = apiary.Id }, apiary);
            }
            catch (System.UnauthorizedAccessException ex)
            {
                return Unauthorized(new { error = ex.Message });
            }
        }

        /// <summary>
        /// Update an existing apiary
        /// </summary>
        [HttpPut("{id}")]
        public async Task<IActionResult> Update(Guid id, [FromBody] UpdateApiaryRequest dto)
        {
            if (!ModelState.IsValid)
                return BadRequest(ModelState);

            try
            {
                var userId = GetUserId();
                var apiary = await _apiaryService.UpdateApiaryAsync(id, dto, userId);
                return Ok(apiary);
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
        /// Delete an apiary
        /// </summary>
        [HttpDelete("{id}")]
        public async Task<IActionResult> Delete(Guid id)
        {
            try
            {
                var userId = GetUserId();
                await _apiaryService.DeleteApiaryAsync(id, userId);
                return NoContent();
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
    }
}