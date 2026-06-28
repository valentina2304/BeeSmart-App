using ApiaryServer.Domain.Entities;

namespace ApiaryServer.Application.Interfaces
{
    public interface IInspectionRepository
    {
        Task<IEnumerable<Inspection>> GetAllByUserIdAsync(Guid userId);
        Task<IEnumerable<Inspection>> GetByApiaryIdAsync(Guid apiaryId);
        Task<IEnumerable<Inspection>> GetByHiveIdAsync(Guid hiveId);
        Task<Inspection?> GetByIdAsync(Guid id);
        Task<Inspection?> GetByIdWithPhotosAsync(Guid id);
        Task AddAsync(Inspection inspection);
        Task UpdateAsync(Inspection inspection);
        Task DeleteAsync(Inspection inspection);
        Task<bool> ExistsAsync(Guid id);
        Task<bool> IsOwnedByUserAsync(Guid inspectionId, Guid userId);
        Task SaveChangesAsync();
    }
}