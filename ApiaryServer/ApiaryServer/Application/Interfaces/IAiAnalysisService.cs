using ApiaryServer.Application.DTOs;

namespace ApiaryServer.Application.Interfaces
{
    public interface IAiAnalysisService
    {
        Task<AnalyzeCellsResponse> AnalyzeCellsAsync(string imageBase64, CancellationToken cancellationToken = default);
    }
}
