using System.Net;
using System.Net.Http.Json;
using ApiaryServer.Application.Options;
using ApiaryServer.Infrastructure.Services;
using Microsoft.Extensions.Options;
using Xunit;

namespace ApiaryServer.Tests;

public class AiAnalysisServiceTests
{
    [Fact]
    public async Task AnalyzeCellsAsync_PreservesLowQualityStatus()
    {
        var service = CreateService(new
        {
            status = "low_quality",
            results = new Dictionary<string, int>
            {
                ["Egg"] = 0,
                ["Larva"] = 0,
                ["Capped Brood"] = 0,
                ["Other"] = 0,
                ["Pollen"] = 0,
                ["Nectar"] = 0,
                ["Honey"] = 0
            },
            message = "Fotografia pare neclara."
        });

        var response = await service.AnalyzeCellsAsync("data:image/jpeg;base64,abc");

        Assert.Equal("low_quality", response.Status);
        Assert.Equal("Fotografia pare neclara.", response.Message);
        Assert.Equal(0, response.Results["Egg"]);
    }

    [Fact]
    public async Task AnalyzeCellsAsync_PreservesNotCombStatus()
    {
        var service = CreateService(new
        {
            status = "not_comb_image",
            results = new Dictionary<string, int>(),
            message = "Nu am detectat celule de fagure."
        });

        var response = await service.AnalyzeCellsAsync("data:image/jpeg;base64,abc");

        Assert.Equal("not_comb_image", response.Status);
        Assert.Empty(response.Results);
        Assert.Equal("Nu am detectat celule de fagure.", response.Message);
    }

    [Fact]
    public async Task AnalyzeCellsAsync_PreservesCellDetections()
    {
        var service = CreateService(new
        {
            status = "success",
            results = new Dictionary<string, int> { ["Eggs"] = 1 },
            cellDetections = new[]
            {
                new
                {
                    x = 120,
                    y = 240,
                    radius = 18,
                    normalizedX = 0.25,
                    normalizedY = 0.5,
                    normalizedRadius = 0.02,
                    className = "Eggs",
                    confidence = 0.93
                }
            }
        });

        var response = await service.AnalyzeCellsAsync("data:image/jpeg;base64,abc");

        var detection = Assert.Single(response.CellDetections!);
        Assert.Equal(120, detection.X);
        Assert.Equal("Eggs", detection.ClassName);
        Assert.Equal(0.93, detection.Confidence);
    }

    [Fact]
    public async Task AnalyzeCellsAsync_ThrowsWhenAiServiceReturnsHttpError()
    {
        var service = CreateService(HttpStatusCode.InternalServerError, "boom");

        await Assert.ThrowsAsync<HttpRequestException>(() =>
            service.AnalyzeCellsAsync("data:image/jpeg;base64,abc"));
    }

    private static AiAnalysisService CreateService(object responseBody) =>
        CreateService(HttpStatusCode.OK, responseBody);

    private static AiAnalysisService CreateService(HttpStatusCode statusCode, object responseBody)
    {
        var handler = new StubHandler(statusCode, responseBody);
        var client = new HttpClient(handler);
        var options = Options.Create(new AiServiceOptions
        {
            BaseUrl = "http://ai.local",
            AnalyzePath = "/analyze",
            TimeoutSeconds = 5
        });
        return new AiAnalysisService(client, options);
    }

    private sealed class StubHandler : HttpMessageHandler
    {
        private readonly HttpStatusCode _statusCode;
        private readonly object _responseBody;

        public StubHandler(HttpStatusCode statusCode, object responseBody)
        {
            _statusCode = statusCode;
            _responseBody = responseBody;
        }

        protected override Task<HttpResponseMessage> SendAsync(
            HttpRequestMessage request,
            CancellationToken cancellationToken)
        {
            var response = new HttpResponseMessage(_statusCode)
            {
                Content = _responseBody is string text
                    ? new StringContent(text)
                    : JsonContent.Create(_responseBody)
            };
            return Task.FromResult(response);
        }
    }
}
