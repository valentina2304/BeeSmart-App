using System;
using System.Collections.Generic;
using System.Threading.Tasks;
using ApiaryServer.Application.DTOs;

namespace ApiaryServer.Application.Interfaces
{
    public interface ITaskService
    {
        Task<IEnumerable<TaskResponse>> GetAllTasksAsync(Guid userId);
        Task<IEnumerable<TaskResponse>> GetTasksByApiaryIdAsync(Guid apiaryId, Guid userId);
        Task<IEnumerable<TaskResponse>> GetTasksByHiveIdAsync(Guid hiveId, Guid userId);
        Task<IEnumerable<TaskResponse>> GetPendingTasksAsync(Guid userId);
        Task<IEnumerable<TaskResponse>> GetOverdueTasksAsync(Guid userId);
        Task<TaskResponse> GetTaskByIdAsync(Guid id, Guid userId);
        Task<TaskResponse> CreateTaskAsync(CreateTaskRequest dto, Guid userId);
        Task<TaskResponse> UpdateTaskAsync(Guid id, UpdateTaskRequest dto, Guid userId);
        Task<TaskResponse> CompleteTaskAsync(Guid id, Guid userId);
        Task<TaskResponse> UncompleteTaskAsync(Guid id, Guid userId);
        Task DeleteTaskAsync(Guid id, Guid userId);
    }
}
