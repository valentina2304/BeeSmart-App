using ApiaryServer.Application.DTOs;
using ApiaryServer.Application.Interfaces;
using ApiaryServer.Application.Exceptions;
using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.Authorization;

namespace ApiaryServer.Api.Controllers
{
    [ApiController]
    [Route("api/tasks")]
    [Authorize]
    public class TaskController : ApiControllerBase
    {
        private readonly ITaskService _taskService;

        public TaskController(ITaskService taskService)
        {
            _taskService = taskService;
        }

        /// <summary>
        /// Get all tasks for the current user
        /// </summary>
        [HttpGet]
        public async Task<IActionResult> GetAll()
        {
            try
            {
                var userId = GetUserId();
                var tasks = await _taskService.GetAllTasksAsync(userId);
                return Ok(tasks);
            }
            catch (System.UnauthorizedAccessException ex)
            {
                return Unauthorized(new { error = ex.Message });
            }
        }

        /// <summary>
        /// Get pending tasks for the current user
        /// </summary>
        [HttpGet("pending")]
        public async Task<IActionResult> GetPending()
        {
            try
            {
                var userId = GetUserId();
                var tasks = await _taskService.GetPendingTasksAsync(userId);
                return Ok(tasks);
            }
            catch (System.UnauthorizedAccessException ex)
            {
                return Unauthorized(new { error = ex.Message });
            }
        }

        /// <summary>
        /// Get overdue tasks for the current user
        /// </summary>
        [HttpGet("overdue")]
        public async Task<IActionResult> GetOverdue()
        {
            try
            {
                var userId = GetUserId();
                var tasks = await _taskService.GetOverdueTasksAsync(userId);
                return Ok(tasks);
            }
            catch (System.UnauthorizedAccessException ex)
            {
                return Unauthorized(new { error = ex.Message });
            }
        }

        /// <summary>
        /// Get all tasks for a specific apiary
        /// </summary>
        [HttpGet("apiary/{apiaryId}")]
        public async Task<IActionResult> GetByApiaryId(Guid apiaryId)
        {
            try
            {
                var userId = GetUserId();
                var tasks = await _taskService.GetTasksByApiaryIdAsync(apiaryId, userId);
                return Ok(tasks);
            }
            catch (System.UnauthorizedAccessException ex)
            {
                return Unauthorized(new { error = ex.Message });
            }
        }

        /// <summary>
        /// Get all tasks for a specific hive
        /// </summary>
        [HttpGet("hive/{hiveId}")]
        public async Task<IActionResult> GetByHiveId(Guid hiveId)
        {
            try
            {
                var userId = GetUserId();
                var tasks = await _taskService.GetTasksByHiveIdAsync(hiveId, userId);
                return Ok(tasks);
            }
            catch (System.UnauthorizedAccessException ex)
            {
                return Unauthorized(new { error = ex.Message });
            }
        }

        /// <summary>
        /// Get a specific task by ID
        /// </summary>
        [HttpGet("{id}")]
        public async Task<IActionResult> GetById(Guid id)
        {
            try
            {
                var userId = GetUserId();
                var task = await _taskService.GetTaskByIdAsync(id, userId);
                return Ok(task);
            }
            catch (TaskNotFoundException ex)
            {
                return NotFound(new { error = ex.Message });
            }
            catch (System.UnauthorizedAccessException ex)
            {
                return Unauthorized(new { error = ex.Message });
            }
        }

        /// <summary>
        /// Create a new task
        /// </summary>
        [HttpPost]
        public async Task<IActionResult> Create([FromBody] CreateTaskRequest dto)
        {
            if (!ModelState.IsValid)
                return BadRequest(ModelState);

            try
            {
                var userId = GetUserId();
                var task = await _taskService.CreateTaskAsync(dto, userId);
                return CreatedAtAction(nameof(GetById), new { id = task.Id }, task);
            }
            catch (ApiaryNotFoundException ex)
            {
                return NotFound(new { error = ex.Message });
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
        /// Update an existing task
        /// </summary>
        [HttpPut("{id}")]
        public async Task<IActionResult> Update(Guid id, [FromBody] UpdateTaskRequest dto)
        {
            if (!ModelState.IsValid)
                return BadRequest(ModelState);

            try
            {
                var userId = GetUserId();
                var task = await _taskService.UpdateTaskAsync(id, dto, userId);
                return Ok(task);
            }
            catch (TaskNotFoundException ex)
            {
                return NotFound(new { error = ex.Message });
            }
            catch (ApiaryNotFoundException ex)
            {
                return NotFound(new { error = ex.Message });
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
        /// Mark a task as completed
        /// </summary>
        [HttpPost("{id}/complete")]
        public async Task<IActionResult> Complete(Guid id)
        {
            try
            {
                var userId = GetUserId();
                var task = await _taskService.CompleteTaskAsync(id, userId);
                return Ok(task);
            }
            catch (TaskNotFoundException ex)
            {
                return NotFound(new { error = ex.Message });
            }
            catch (System.UnauthorizedAccessException ex)
            {
                return Unauthorized(new { error = ex.Message });
            }
        }

        /// <summary>
        /// Reopen a completed task
        /// </summary>
        [HttpPost("{id}/uncomplete")]
        public async Task<IActionResult> Uncomplete(Guid id)
        {
            try
            {
                var userId = GetUserId();
                var task = await _taskService.UncompleteTaskAsync(id, userId);
                return Ok(task);
            }
            catch (TaskNotFoundException ex)
            {
                return NotFound(new { error = ex.Message });
            }
            catch (System.UnauthorizedAccessException ex)
            {
                return Unauthorized(new { error = ex.Message });
            }
        }

        /// <summary>
        /// Delete a task
        /// </summary>
        [HttpDelete("{id}")]
        public async Task<IActionResult> Delete(Guid id)
        {
            try
            {
                var userId = GetUserId();
                await _taskService.DeleteTaskAsync(id, userId);
                return NoContent();
            }
            catch (TaskNotFoundException ex)
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
