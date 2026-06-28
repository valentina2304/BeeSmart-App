using ApiaryServer.Domain.Entities;

namespace ApiaryServer.Application.Interfaces
{
    public interface IHiveTreatmentRepository
    {
        Task<IEnumerable<HiveTreatment>> GetAllByUserIdAsync(Guid userId);
        Task<IEnumerable<HiveTreatment>> GetByApiaryIdAsync(Guid apiaryId);
        Task<IEnumerable<HiveTreatment>> GetByHiveIdAsync(Guid hiveId);
        Task<HiveTreatment?> GetByIdAsync(Guid id);
        Task AddAsync(HiveTreatment treatment);
        Task UpdateAsync(HiveTreatment treatment);
        Task DeleteAsync(HiveTreatment treatment);
        Task<bool> ExistsAsync(Guid id);
        Task<bool> IsOwnedByUserAsync(Guid treatmentId, Guid userId);
        Task SaveChangesAsync();
    }
}
