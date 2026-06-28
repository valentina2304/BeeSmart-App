using ApiaryServer.Api.Controllers;
using ApiaryServer.Application.DTOs;
using ApiaryServer.Application.Interfaces;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;
using System.Security.Claims;
using Xunit;

namespace ApiaryServer.Tests;

public class InspectionControllerTests
{
    private static readonly Guid UserId = Guid.Parse("11111111-1111-1111-1111-111111111111");

    [Fact]
    public async Task AnalyzeCells_WithRemoteUrl_ReturnsBadRequestWithoutCallingAiService()
    {
        var aiService = new FakeAiAnalysisService();
        var controller = new InspectionController(new FakeInspectionService(), aiService);

        var response = await controller.AnalyzeCells(
            new AnalyzeCellsRequest("https://example.test/frame.jpg"),
            CancellationToken.None);

        Assert.IsType<BadRequestObjectResult>(response);
        Assert.Equal(0, aiService.AnalyzeCalls);
    }

    [Fact]
    public async Task AnalyzeCells_WithOversizedImage_ReturnsPayloadTooLargeWithoutCallingAiService()
    {
        var aiService = new FakeAiAnalysisService();
        var controller = new InspectionController(new FakeInspectionService(), aiService);
        var oversizedJpeg = new byte[(6 * 1024 * 1024) + 1];
        oversizedJpeg[0] = 0xFF;
        oversizedJpeg[1] = 0xD8;
        oversizedJpeg[2] = 0xFF;

        var response = await controller.AnalyzeCells(
            new AnalyzeCellsRequest("data:image/jpeg;base64," + Convert.ToBase64String(oversizedJpeg)),
            CancellationToken.None);

        var result = Assert.IsType<ObjectResult>(response);
        Assert.Equal(StatusCodes.Status413PayloadTooLarge, result.StatusCode);
        Assert.Equal(0, aiService.AnalyzeCalls);
    }

    [Fact]
    public async Task SaveAiAnalysis_WithSuccessAndNoPositiveCells_ReturnsBadRequestWithoutCallingService()
    {
        var service = new FakeInspectionService();
        var controller = new InspectionController(service, new FakeAiAnalysisService());
        SetUser(controller, UserId);

        var response = await controller.SaveAiAnalysis(
            Guid.NewGuid(),
            new SaveInspectionAiAnalysisRequest(new Dictionary<string, int> { ["Capped"] = 0 }, "success", null));

        Assert.IsType<BadRequestObjectResult>(response);
        Assert.Equal(0, service.SaveAiAnalysisCalls);
    }

    [Fact]
    public async Task SaveAiAnalysis_WithNegativeCounts_ReturnsBadRequestWithoutCallingService()
    {
        var service = new FakeInspectionService();
        var controller = new InspectionController(service, new FakeAiAnalysisService());
        SetUser(controller, UserId);

        var response = await controller.SaveAiAnalysis(
            Guid.NewGuid(),
            new SaveInspectionAiAnalysisRequest(new Dictionary<string, int> { ["Eggs"] = -1 }, "success", null));

        Assert.IsType<BadRequestObjectResult>(response);
        Assert.Equal(0, service.SaveAiAnalysisCalls);
    }

    private static void SetUser(ControllerBase controller, Guid userId)
    {
        controller.ControllerContext = new ControllerContext
        {
            HttpContext = new DefaultHttpContext
            {
                User = new ClaimsPrincipal(new ClaimsIdentity(
                    new[] { new Claim(ClaimTypes.NameIdentifier, userId.ToString()) },
                    "TestAuth"))
            }
        };
    }

    private sealed class FakeAiAnalysisService : IAiAnalysisService
    {
        public int AnalyzeCalls { get; private set; }

        public Task<AnalyzeCellsResponse> AnalyzeCellsAsync(
            string imageBase64,
            CancellationToken cancellationToken = default)
        {
            AnalyzeCalls++;
            return Task.FromResult(new AnalyzeCellsResponse(
                "success",
                new Dictionary<string, int> { ["Capped"] = 1 },
                null));
        }
    }

    private sealed class FakeInspectionService : IInspectionService
    {
        public int SaveAiAnalysisCalls { get; private set; }

        public Task<IEnumerable<InspectionResponse>> GetAllInspectionsAsync(Guid userId) =>
            Task.FromResult(Enumerable.Empty<InspectionResponse>());

        public Task<IEnumerable<InspectionResponse>> GetInspectionsByApiaryIdAsync(Guid apiaryId, Guid userId) =>
            Task.FromResult(Enumerable.Empty<InspectionResponse>());

        public Task<IEnumerable<InspectionResponse>> GetInspectionsByHiveIdAsync(Guid hiveId, Guid userId) =>
            Task.FromResult(Enumerable.Empty<InspectionResponse>());

        public Task<InspectionDetailResponse> GetInspectionByIdAsync(Guid id, Guid userId) =>
            throw new NotImplementedException();

        public Task<InspectionResponse> CreateInspectionAsync(CreateInspectionRequest dto, Guid userId) =>
            throw new NotImplementedException();

        public Task<InspectionResponse> UpdateInspectionAsync(Guid id, UpdateInspectionRequest dto, Guid userId) =>
            throw new NotImplementedException();

        public Task DeleteInspectionAsync(Guid id, Guid userId) =>
            Task.CompletedTask;

        public Task<InspectionPhotoResponse> AddPhotoAsync(Guid inspectionId, AddInspectionPhotoRequest dto, Guid userId) =>
            throw new NotImplementedException();

        public Task<InspectionPhotoResponse> UpdatePhotoAsync(Guid photoId, UpdateInspectionPhotoRequest dto, Guid userId) =>
            throw new NotImplementedException();

        public Task DeletePhotoAsync(Guid photoId, Guid userId) =>
            Task.CompletedTask;

        public Task<InspectionAiAnalysisResponse> SaveAiAnalysisAsync(
            Guid inspectionId,
            SaveInspectionAiAnalysisRequest dto,
            Guid userId)
        {
            SaveAiAnalysisCalls++;
            return Task.FromResult(new InspectionAiAnalysisResponse(
                Guid.NewGuid(),
                inspectionId,
                Guid.NewGuid(),
                Guid.NewGuid(),
                DateTimeOffset.UtcNow,
                dto.Status ?? "success",
                dto.Results,
                dto.Message,
                dto.Results.Values.Sum(),
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                null,
                null,
                null,
                DateTimeOffset.UtcNow));
        }

        public Task<IEnumerable<InspectionAiAnalysisResponse>> GetAiAnalysesByHiveIdAsync(Guid hiveId, Guid userId) =>
            Task.FromResult(Enumerable.Empty<InspectionAiAnalysisResponse>());
    }
}
