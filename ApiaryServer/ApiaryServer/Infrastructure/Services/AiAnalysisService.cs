using System.Net.Http.Json;
using ApiaryServer.Application.DTOs;
using ApiaryServer.Application.Interfaces;
using ApiaryServer.Application.Options;
using Microsoft.Extensions.Options;

namespace ApiaryServer.Infrastructure.Services
{
    public class AiAnalysisService : IAiAnalysisService
    {
        private readonly HttpClient _httpClient;
        private readonly string _analyzePath;

        public AiAnalysisService(HttpClient httpClient, IOptions<AiServiceOptions> options)
        {
            _httpClient = httpClient;

            var config = options.Value;
            _httpClient.BaseAddress = new Uri(config.BaseUrl);
            _httpClient.Timeout = TimeSpan.FromSeconds(config.TimeoutSeconds);
            _analyzePath = config.AnalyzePath;
        }

        public async Task<AnalyzeCellsResponse> AnalyzeCellsAsync(string imageBase64, CancellationToken cancellationToken = default)
        {
            var payload = new Dictionary<string, string>
            {
                ["image_base64"] = imageBase64
            };

            var response = await _httpClient.PostAsJsonAsync(_analyzePath, payload, cancellationToken);
            var body = await response.Content.ReadAsStringAsync(cancellationToken);

            if (!response.IsSuccessStatusCode)
            {
                throw new HttpRequestException($"AI service error {(int)response.StatusCode}: {body}");
            }

            var parsed = await response.Content.ReadFromJsonAsync<AnalyzeCellsResponse>(cancellationToken: cancellationToken);
            if (parsed is null)
            {
                throw new InvalidOperationException("AI service returned an empty response.");
            }

            return new AnalyzeCellsResponse(
                parsed.Status,
                parsed.Results ?? new Dictionary<string, int>(),
                parsed.Message,
                parsed.Quality,
                parsed.CellDetections ?? Array.Empty<CellDetectionDto>()
            );
        }
    }
}
