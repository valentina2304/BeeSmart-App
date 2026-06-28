using ApiaryServer.Application.DTOs;
using ApiaryServer.Application.Interfaces;
using ApiaryServer.Application.Exceptions;
using ApiaryServer.Domain.Entities;
using Microsoft.Extensions.Logging;
using TaskStatus = ApiaryServer.Domain.Entities.TaskStatus;

namespace ApiaryServer.Infrastructure.Services
{
    public class TaskService : ITaskService
    {
        private readonly ITaskRepository _taskRepo;
        private readonly IApiaryRepository _apiaryRepo;
        private readonly IHiveRepository _hiveRepo;
        private readonly ILogger<TaskService> _logger;

        public TaskService(
            ITaskRepository taskRepo,
            IApiaryRepository apiaryRepo,
            IHiveRepository hiveRepo,
            ILogger<TaskService> logger)
        {
            _taskRepo = taskRepo;
            _apiaryRepo = apiaryRepo;
            _hiveRepo = hiveRepo;
            _logger = logger;
        }

        public async Task<IEnumerable<TaskResponse>> GetAllTasksAsync(Guid userId)
        {
            var tasks = await _taskRepo.GetAllByUserIdAsync(userId);
            return MapToResponses(tasks);
        }

        public async Task<IEnumerable<TaskResponse>> GetTasksByApiaryIdAsync(Guid apiaryId, Guid userId)
        {
            // Verify apiary ownership
            if (!await _apiaryRepo.IsOwnedByUserAsync(apiaryId, userId))
            {
                _logger.LogWarning("Unauthorized access to apiary {ApiaryId} by user {UserId}", apiaryId, userId);
                throw new System.UnauthorizedAccessException();
            }

            var tasks = await _taskRepo.GetByApiaryIdAsync(apiaryId);
            return MapToResponses(tasks);
        }

        public async Task<IEnumerable<TaskResponse>> GetTasksByHiveIdAsync(Guid hiveId, Guid userId)
        {
            // Verify hive ownership
            if (!await _hiveRepo.IsOwnedByUserAsync(hiveId, userId))
            {
                _logger.LogWarning("Unauthorized access to hive {HiveId} by user {UserId}", hiveId, userId);
                throw new System.UnauthorizedAccessException();
            }

            var tasks = await _taskRepo.GetByHiveIdAsync(hiveId);
            return MapToResponses(tasks);
        }

        public async Task<IEnumerable<TaskResponse>> GetPendingTasksAsync(Guid userId)
        {
            var tasks = await _taskRepo.GetPendingByUserIdAsync(userId);
            return MapToResponses(tasks);
        }

        public async Task<IEnumerable<TaskResponse>> GetOverdueTasksAsync(Guid userId)
        {
            var tasks = await _taskRepo.GetOverdueByUserIdAsync(userId);
            return MapToResponses(tasks);
        }

        public async Task<TaskResponse> GetTaskByIdAsync(Guid id, Guid userId)
        {
            var task = await _taskRepo.GetByIdAsync(id);

            if (task == null)
            {
                _logger.LogWarning("Task not found: {TaskId}", id);
                throw new TaskNotFoundException();
            }

            if (task.UserId != userId)
            {
                _logger.LogWarning("Unauthorized access to task {TaskId} by user {UserId}", id, userId);
                throw new System.UnauthorizedAccessException();
            }

            return MapToResponse(task);
        }

        public async Task<TaskResponse> CreateTaskAsync(CreateTaskRequest dto, Guid userId)
        {
            // Validate apiary if provided
            if (dto.ApiaryId.HasValue)
            {
                if (!await _apiaryRepo.IsOwnedByUserAsync(dto.ApiaryId.Value, userId))
                {
                    _logger.LogWarning("Invalid apiary {ApiaryId} for task creation by user {UserId}", dto.ApiaryId.Value, userId);
                    throw new ApiaryNotFoundException();
                }
            }

            // Validate hive if provided
            if (dto.HiveId.HasValue)
            {
                if (!await _hiveRepo.IsOwnedByUserAsync(dto.HiveId.Value, userId))
                {
                    _logger.LogWarning("Invalid hive {HiveId} for task creation by user {UserId}", dto.HiveId.Value, userId);
                    throw new HiveNotFoundException();
                }
            }

            var task = new HiveTask
            {
                UserId = userId,
                ApiaryId = dto.ApiaryId,
                HiveId = dto.HiveId,
                Title = dto.Title,
                Description = dto.Description,
                Priority = dto.Priority,
                DueDate = dto.DueDate
            };

            await _taskRepo.AddAsync(task);
            await _taskRepo.SaveChangesAsync();

            _logger.LogInformation("Task created: {TaskId} by user {UserId}", task.Id, userId);

            // Reload to get navigation properties
            task = await _taskRepo.GetByIdAsync(task.Id);
            return MapToResponse(task!);
        }

        public async Task<TaskResponse> UpdateTaskAsync(Guid id, UpdateTaskRequest dto, Guid userId)
        {
            var task = await _taskRepo.GetByIdAsync(id);

            if (task == null)
            {
                _logger.LogWarning("Task not found for update: {TaskId}", id);
                throw new TaskNotFoundException();
            }

            if (task.UserId != userId)
            {
                _logger.LogWarning("Unauthorized update attempt on task {TaskId} by user {UserId}", id, userId);
                throw new System.UnauthorizedAccessException();
            }

            // Validate apiary if changed
            if (dto.ApiaryId.HasValue && dto.ApiaryId != task.ApiaryId)
            {
                if (!await _apiaryRepo.IsOwnedByUserAsync(dto.ApiaryId.Value, userId))
                {
                    throw new ApiaryNotFoundException();
                }
            }

            // Validate hive if changed
            if (dto.HiveId.HasValue && dto.HiveId != task.HiveId)
            {
                if (!await _hiveRepo.IsOwnedByUserAsync(dto.HiveId.Value, userId))
                {
                    throw new HiveNotFoundException();
                }
            }

            task.Title = dto.Title;
            task.Description = dto.Description;
            task.Priority = dto.Priority;
            task.Status = dto.Status;
            task.DueDate = dto.DueDate;
            task.ApiaryId = dto.ApiaryId;
            task.HiveId = dto.HiveId;

            // Set CompletedAt if status changed to Completed
            if (dto.Status == TaskStatus.Completed && task.CompletedAt == null)
            {
                task.CompletedAt = DateTimeOffset.UtcNow;
            }
            else if (dto.Status != TaskStatus.Completed)
            {
                task.CompletedAt = null;
            }

            await _taskRepo.UpdateAsync(task);
            await _taskRepo.SaveChangesAsync();

            _logger.LogInformation("Task updated: {TaskId}", id);

            // Reload to get navigation properties
            task = await _taskRepo.GetByIdAsync(id);
            return MapToResponse(task!);
        }

        public async Task<TaskResponse> CompleteTaskAsync(Guid id, Guid userId)
        {
            var task = await _taskRepo.GetByIdAsync(id);

            if (task == null)
            {
                _logger.LogWarning("Task not found for completion: {TaskId}", id);
                throw new TaskNotFoundException();
            }

            if (task.UserId != userId)
            {
                _logger.LogWarning("Unauthorized complete attempt on task {TaskId} by user {UserId}", id, userId);
                throw new System.UnauthorizedAccessException();
            }

            task.Status = TaskStatus.Completed;
            task.CompletedAt = DateTimeOffset.UtcNow;

            await _taskRepo.UpdateAsync(task);
            await _taskRepo.SaveChangesAsync();

            _logger.LogInformation("Task completed: {TaskId}", id);

            return MapToResponse(task);
        }

        public async Task<TaskResponse> UncompleteTaskAsync(Guid id, Guid userId)
        {
            var task = await _taskRepo.GetByIdAsync(id);

            if (task == null)
            {
                _logger.LogWarning("Task not found for reopening: {TaskId}", id);
                throw new TaskNotFoundException();
            }

            if (task.UserId != userId)
            {
                _logger.LogWarning("Unauthorized reopen attempt on task {TaskId} by user {UserId}", id, userId);
                throw new System.UnauthorizedAccessException();
            }

            task.Status = TaskStatus.Pending;
            task.CompletedAt = null;

            await _taskRepo.UpdateAsync(task);
            await _taskRepo.SaveChangesAsync();

            _logger.LogInformation("Task reopened: {TaskId}", id);

            return MapToResponse(task);
        }

        public async Task DeleteTaskAsync(Guid id, Guid userId)
        {
            var task = await _taskRepo.GetByIdAsync(id);

            if (task == null)
            {
                _logger.LogWarning("Task not found for deletion: {TaskId}", id);
                throw new TaskNotFoundException();
            }

            if (task.UserId != userId)
            {
                _logger.LogWarning("Unauthorized delete attempt on task {TaskId} by user {UserId}", id, userId);
                throw new System.UnauthorizedAccessException();
            }

            await _taskRepo.DeleteAsync(task);
            await _taskRepo.SaveChangesAsync();

            _logger.LogInformation("Task deleted: {TaskId}", id);
        }

        private static TaskResponse MapToResponse(HiveTask task)
        {
            return new TaskResponse(
                task.Id,
                task.UserId,
                task.ApiaryId,
                task.Apiary?.Name,
                task.HiveId,
                task.Hive?.Name,
                task.Title,
                task.Description,
                task.Priority,
                task.Status,
                task.DueDate,
                task.CompletedAt,
                task.CreatedAt,
                task.UpdatedAt
            );
        }

        private static IEnumerable<TaskResponse> MapToResponses(IEnumerable<HiveTask> tasks)
        {
            return tasks.Select(MapToResponse);
        }
    }
}
