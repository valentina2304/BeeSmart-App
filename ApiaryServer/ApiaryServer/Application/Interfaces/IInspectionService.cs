using ApiaryServer.Application.DTOs;

namespace ApiaryServer.Application.Interfaces
{
    public interface IInspectionService
    {
        Task<IEnumerable<InspectionResponse>> GetAllInspectionsAsync(Guid userId);
        Task<IEnumerable<InspectionResponse>> GetInspectionsByApiaryIdAsync(Guid apiaryId, Guid userId);
        Task<IEnumerable<InspectionResponse>> GetInspectionsByHiveIdAsync(Guid hiveId, Guid userId);
        Task<InspectionDetailResponse> GetInspectionByIdAsync(Guid id, Guid userId);
        Task<InspectionResponse> CreateInspectionAsync(CreateInspectionRequest dto, Guid userId);
        Task<InspectionResponse> UpdateInspectionAsync(Guid id, UpdateInspectionRequest dto, Guid userId);
        Task DeleteInspectionAsync(Guid id, Guid userId);

        // Photo management
        Task<InspectionPhotoResponse> AddPhotoAsync(Guid inspectionId, AddInspectionPhotoRequest dto, Guid userId);
        Task<InspectionPhotoResponse> UpdatePhotoAsync(Guid photoId, UpdateInspectionPhotoRequest dto, Guid userId);
        Task DeletePhotoAsync(Guid photoId, Guid userId);
        Task<InspectionAiAnalysisResponse> SaveAiAnalysisAsync(Guid inspectionId, SaveInspectionAiAnalysisRequest dto, Guid userId);
        Task<IEnumerable<InspectionAiAnalysisResponse>> GetAiAnalysesByHiveIdAsync(Guid hiveId, Guid userId);
    }
}
