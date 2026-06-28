using ApiaryServer.Application.DTOs;
using ApiaryServer.Application.Exceptions;
using ApiaryServer.Application.Interfaces;
using ApiaryServer.Domain.Entities;
using ApiaryServer.Infrastructure.Services;
using Xunit;

namespace ApiaryServer.Tests;

public class HiveExtractionServiceTests
{
    private static readonly Guid UserId = Guid.Parse("11111111-1111-1111-1111-111111111111");
    private static readonly Guid OtherUserId = Guid.Parse("22222222-2222-2222-2222-222222222222");

    [Fact]
    public async Task GetExtractionsByApiaryIdAsync_WithForeignApiary_ThrowsBeforeQuery()
    {
        var apiaryRepo = new InMemoryApiaryRepository();
        var foreignApiary = TestEntityFactory.Apiary("Foreign", OtherUserId);
        apiaryRepo.Apiaries.Add(foreignApiary);
        var extractionRepo = new FakeExtractionRepository();
        var service = CreateService(extractionRepo, apiaryRepo, new InMemoryHiveRepository());

        await Assert.ThrowsAsync<System.UnauthorizedAccessException>(() =>
            service.GetExtractionsByApiaryIdAsync(foreignApiary.Id, UserId));

        Assert.Equal(0, extractionRepo.GetByApiaryIdCalls);
    }

    [Fact]
    public async Task GetExtractionsByHiveIdAsync_WithForeignHive_ThrowsBeforeQuery()
    {
        var foreignApiary = TestEntityFactory.Apiary("Foreign", OtherUserId);
        var foreignHive = TestEntityFactory.Hive("Foreign hive", foreignApiary);
        var hiveRepo = new InMemoryHiveRepository();
        hiveRepo.Hives.Add(foreignHive);
        var extractionRepo = new FakeExtractionRepository();
        var service = CreateService(extractionRepo, new InMemoryApiaryRepository(), hiveRepo);

        await Assert.ThrowsAsync<System.UnauthorizedAccessException>(() =>
            service.GetExtractionsByHiveIdAsync(foreignHive.Id, UserId));

        Assert.Equal(0, extractionRepo.GetByHiveIdCalls);
    }

    [Fact]
    public async Task GetExtractionByIdAsync_WithMissingExtraction_Throws()
    {
        var service = CreateService(new FakeExtractionRepository(), new InMemoryApiaryRepository(), new InMemoryHiveRepository());

        await Assert.ThrowsAsync<ExtractionNotFoundException>(() =>
            service.GetExtractionByIdAsync(Guid.NewGuid(), UserId));
    }

    [Fact]
    public async Task GetExtractionByIdAsync_WithForeignExtraction_Throws()
    {
        var foreignApiary = TestEntityFactory.Apiary("Foreign", OtherUserId);
        var extraction = TestEntityFactory.Extraction(TestEntityFactory.Hive("Foreign hive", foreignApiary));
        var extractionRepo = new FakeExtractionRepository();
        extractionRepo.Extractions.Add(extraction);
        var service = CreateService(extractionRepo, new InMemoryApiaryRepository(), new InMemoryHiveRepository());

        await Assert.ThrowsAsync<System.UnauthorizedAccessException>(() =>
            service.GetExtractionByIdAsync(extraction.Id, UserId));
    }

    [Fact]
    public async Task CreateExtractionAsync_WithMissingHive_DoesNotPersist()
    {
        var extractionRepo = new FakeExtractionRepository();
        var service = CreateService(extractionRepo, new InMemoryApiaryRepository(), new InMemoryHiveRepository());

        await Assert.ThrowsAsync<HiveNotFoundException>(() =>
            service.CreateExtractionAsync(CreateExtractionRequest(Guid.NewGuid()), UserId));

        Assert.Empty(extractionRepo.Extractions);
        Assert.Equal(0, extractionRepo.SaveChangesCalls);
    }

    [Fact]
    public async Task CreateExtractionAsync_WithForeignHive_DoesNotPersist()
    {
        var foreignApiary = TestEntityFactory.Apiary("Foreign", OtherUserId);
        var foreignHive = TestEntityFactory.Hive("Foreign hive", foreignApiary);
        var hiveRepo = new InMemoryHiveRepository();
        hiveRepo.Hives.Add(foreignHive);
        var extractionRepo = new FakeExtractionRepository();
        var service = CreateService(extractionRepo, new InMemoryApiaryRepository(), hiveRepo);

        await Assert.ThrowsAsync<System.UnauthorizedAccessException>(() =>
            service.CreateExtractionAsync(CreateExtractionRequest(foreignHive.Id), UserId));

        Assert.Empty(extractionRepo.Extractions);
        Assert.Equal(0, extractionRepo.SaveChangesCalls);
    }

    [Fact]
    public async Task CreateExtractionAsync_WithOwnedHive_PersistsApiaryLinkAndMapsResponse()
    {
        var apiary = TestEntityFactory.Apiary("Main", UserId);
        var hive = TestEntityFactory.Hive("Hive A", apiary);
        var hiveRepo = new InMemoryHiveRepository();
        hiveRepo.Hives.Add(hive);
        var extractionRepo = new FakeExtractionRepository();
        var service = CreateService(extractionRepo, new InMemoryApiaryRepository(), hiveRepo);

        var response = await service.CreateExtractionAsync(CreateExtractionRequest(hive.Id), UserId);

        Assert.Single(extractionRepo.Extractions);
        Assert.Equal(hive.Id, extractionRepo.Extractions[0].HiveId);
        Assert.Equal(apiary.Id, extractionRepo.Extractions[0].ApiaryId);
        Assert.Equal(12.75m, response.Quantity);
        Assert.Equal("Hive A", response.HiveName);
        Assert.Equal("Main", response.ApiaryName);
        Assert.Equal(1, extractionRepo.AddCalls);
        Assert.Equal(1, extractionRepo.SaveChangesCalls);
    }

    [Fact]
    public async Task UpdateExtractionAsync_WithOwnedExtraction_UpdatesFieldsAndSaves()
    {
        var apiary = TestEntityFactory.Apiary("Main", UserId);
        var extraction = TestEntityFactory.Extraction(TestEntityFactory.Hive("Hive", apiary));
        var extractionRepo = new FakeExtractionRepository();
        extractionRepo.Extractions.Add(extraction);
        var service = CreateService(extractionRepo, new InMemoryApiaryRepository(), new InMemoryHiveRepository());

        var response = await service.UpdateExtractionAsync(extraction.Id, UpdateExtractionRequest(), UserId);

        Assert.Equal(7.25m, extraction.Quantity);
        Assert.Equal(ExtractionType.Wax, extraction.Type);
        Assert.Equal("kg", response.Unit);
        Assert.Equal(1, extractionRepo.UpdateCalls);
        Assert.Equal(1, extractionRepo.SaveChangesCalls);
    }

    [Fact]
    public async Task UpdateExtractionAsync_WithForeignExtraction_DoesNotUpdate()
    {
        var foreignApiary = TestEntityFactory.Apiary("Foreign", OtherUserId);
        var extraction = TestEntityFactory.Extraction(TestEntityFactory.Hive("Foreign hive", foreignApiary));
        var extractionRepo = new FakeExtractionRepository();
        extractionRepo.Extractions.Add(extraction);
        var service = CreateService(extractionRepo, new InMemoryApiaryRepository(), new InMemoryHiveRepository());

        await Assert.ThrowsAsync<System.UnauthorizedAccessException>(() =>
            service.UpdateExtractionAsync(extraction.Id, UpdateExtractionRequest(), UserId));

        Assert.Equal(12.5m, extraction.Quantity);
        Assert.Equal(0, extractionRepo.UpdateCalls);
        Assert.Equal(0, extractionRepo.SaveChangesCalls);
    }

    [Fact]
    public async Task DeleteExtractionAsync_WithOwnedExtraction_RemovesAndSaves()
    {
        var apiary = TestEntityFactory.Apiary("Main", UserId);
        var extraction = TestEntityFactory.Extraction(TestEntityFactory.Hive("Hive", apiary));
        var extractionRepo = new FakeExtractionRepository();
        extractionRepo.Extractions.Add(extraction);
        var service = CreateService(extractionRepo, new InMemoryApiaryRepository(), new InMemoryHiveRepository());

        await service.DeleteExtractionAsync(extraction.Id, UserId);

        Assert.Empty(extractionRepo.Extractions);
        Assert.Equal(1, extractionRepo.DeleteCalls);
        Assert.Equal(1, extractionRepo.SaveChangesCalls);
    }

    private static HiveExtractionService CreateService(
        FakeExtractionRepository extractionRepo,
        InMemoryApiaryRepository apiaryRepo,
        InMemoryHiveRepository hiveRepo) =>
        new(extractionRepo, apiaryRepo, hiveRepo, new NoOpLogger<HiveExtractionService>());

    private static CreateExtractionRequest CreateExtractionRequest(Guid hiveId) =>
        new(
            hiveId,
            DateTimeOffset.Parse("2026-07-01T00:00:00Z"),
            ExtractionType.Honey,
            12.75m,
            "kg",
            "summer harvest");

    private static UpdateExtractionRequest UpdateExtractionRequest() =>
        new(
            DateTimeOffset.Parse("2026-08-01T00:00:00Z"),
            ExtractionType.Wax,
            7.25m,
            "kg",
            "updated");

    private sealed class FakeExtractionRepository : IHiveExtractionRepository
    {
        public List<HiveExtraction> Extractions { get; } = new();
        public int GetByApiaryIdCalls { get; private set; }
        public int GetByHiveIdCalls { get; private set; }
        public int AddCalls { get; private set; }
        public int UpdateCalls { get; private set; }
        public int DeleteCalls { get; private set; }
        public int SaveChangesCalls { get; private set; }

        public Task<IEnumerable<HiveExtraction>> GetAllByUserIdAsync(Guid userId) =>
            Task.FromResult(Extractions.Where(e => e.Hive.Apiary.UserId == userId).AsEnumerable());

        public Task<IEnumerable<HiveExtraction>> GetByApiaryIdAsync(Guid apiaryId)
        {
            GetByApiaryIdCalls++;
            return Task.FromResult(Extractions.Where(e => e.ApiaryId == apiaryId).AsEnumerable());
        }

        public Task<IEnumerable<HiveExtraction>> GetByHiveIdAsync(Guid hiveId)
        {
            GetByHiveIdCalls++;
            return Task.FromResult(Extractions.Where(e => e.HiveId == hiveId).AsEnumerable());
        }

        public Task<HiveExtraction?> GetByIdAsync(Guid id) =>
            Task.FromResult(Extractions.SingleOrDefault(e => e.Id == id));

        public Task AddAsync(HiveExtraction extraction)
        {
            AddCalls++;
            Extractions.Add(extraction);
            return Task.CompletedTask;
        }

        public Task UpdateAsync(HiveExtraction extraction)
        {
            UpdateCalls++;
            return Task.CompletedTask;
        }

        public Task DeleteAsync(HiveExtraction extraction)
        {
            DeleteCalls++;
            Extractions.Remove(extraction);
            return Task.CompletedTask;
        }

        public Task<bool> ExistsAsync(Guid id) =>
            Task.FromResult(Extractions.Any(e => e.Id == id));

        public Task<bool> IsOwnedByUserAsync(Guid extractionId, Guid userId) =>
            Task.FromResult(Extractions.Any(e => e.Id == extractionId && e.Hive.Apiary.UserId == userId));

        public Task SaveChangesAsync()
        {
            SaveChangesCalls++;
            return Task.CompletedTask;
        }
    }
}
