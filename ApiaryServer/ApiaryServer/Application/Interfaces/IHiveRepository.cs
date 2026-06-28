using ApiaryServer.Domain.Entities;
using Microsoft.AspNetCore.Mvc;

namespace ApiaryServer.Application.Interfaces
{
    public interface IHiveRepository
    {
        Task<IEnumerable<Hive>> GetAllByApiaryIdAsync(Guid apiaryId);
        Task<IEnumerable<Hive>> GetAllByUserIdAsync(Guid userId);
        Task<Hive?> GetByIdAsync(Guid id);
        Task AddAsync(Hive hive);
        Task UpdateAsync(Hive hive);
        Task DeleteAsync(Hive hive);
        Task<bool> ExistsAsync(Guid id);
        Task<bool> IsOwnedByUserAsync(Guid hiveId, Guid userId);
        Task SaveChangesAsync();
    }
}
