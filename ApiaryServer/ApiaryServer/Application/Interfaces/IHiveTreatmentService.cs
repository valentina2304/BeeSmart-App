using ApiaryServer.Application.DTOs;

namespace ApiaryServer.Application.Interfaces
{
    public interface IHiveTreatmentService
    {
        Task<IEnumerable<TreatmentResponse>> GetAllTreatmentsAsync(Guid userId);
        Task<IEnumerable<TreatmentResponse>> GetTreatmentsByApiaryIdAsync(Guid apiaryId, Guid userId);
        Task<IEnumerable<TreatmentResponse>> GetTreatmentsByHiveIdAsync(Guid hiveId, Guid userId);
        Task<TreatmentResponse> GetTreatmentByIdAsync(Guid id, Guid userId);
        Task<TreatmentResponse> CreateTreatmentAsync(CreateTreatmentRequest dto, Guid userId);
        Task<TreatmentResponse> UpdateTreatmentAsync(Guid id, UpdateTreatmentRequest dto, Guid userId);
        Task DeleteTreatmentAsync(Guid id, Guid userId);
    }
}
