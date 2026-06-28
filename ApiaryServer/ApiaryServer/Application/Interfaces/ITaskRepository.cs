using ApiaryServer.Domain.Entities;
using Microsoft.AspNetCore.Mvc;

namespace ApiaryServer.Application.Interfaces
{
    public interface ITaskRepository
    {
        Task<IEnumerable<HiveTask>> GetAllByUserIdAsync(Guid userId);
        Task<IEnumerable<HiveTask>> GetByApiaryIdAsync(Guid apiaryId);
        Task<IEnumerable<HiveTask>> GetByHiveIdAsync(Guid hiveId);
        Task<IEnumerable<HiveTask>> GetPendingByUserIdAsync(Guid userId);
        Task<IEnumerable<HiveTask>> GetOverdueByUserIdAsync(Guid userId);
        Task<HiveTask?> GetByIdAsync(Guid id);
        Task AddAsync(HiveTask task);
        Task UpdateAsync(HiveTask task);
        Task DeleteAsync(HiveTask task);
        Task<bool> ExistsAsync(Guid id);
        Task<bool> IsOwnedByUserAsync(Guid taskId, Guid userId);
        Task SaveChangesAsync();
    }

}
