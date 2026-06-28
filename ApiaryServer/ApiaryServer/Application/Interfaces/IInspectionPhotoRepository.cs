using ApiaryServer.Domain.Entities;

namespace ApiaryServer.Application.Interfaces
{
    public interface IInspectionPhotoRepository
    {
        Task<IEnumerable<InspectionPhoto>> GetByInspectionIdAsync(Guid inspectionId);
        Task<InspectionPhoto?> GetByIdAsync(Guid id);
        Task AddAsync(InspectionPhoto photo);
        Task UpdateAsync(InspectionPhoto photo);
        Task DeleteAsync(InspectionPhoto photo);
        Task<bool> ExistsAsync(Guid id);
        Task SaveChangesAsync();
    }
}