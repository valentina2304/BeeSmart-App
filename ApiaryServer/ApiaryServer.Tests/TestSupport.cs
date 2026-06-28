using ApiaryServer.Application.Interfaces;
using ApiaryServer.Domain.Entities;
using Microsoft.Extensions.Logging;

namespace ApiaryServer.Tests;

internal sealed class NoOpLogger<T> : ILogger<T>
{
    public IDisposable? BeginScope<TState>(TState state) where TState : notnull => null;
    public bool IsEnabled(LogLevel logLevel) => false;
    public void Log<TState>(
        LogLevel logLevel,
        EventId eventId,
        TState state,
        Exception? exception,
        Func<TState, Exception?, string> formatter)
    {
    }
}

internal static class TestEntityFactory
{
    public static Apiary Apiary(string name, Guid userId) =>
        new()
        {
            Id = Guid.NewGuid(),
            UserId = userId,
            Name = name,
            Description = "description",
            Location = "location",
            CreatedAt = DateTimeOffset.Parse("2026-01-01T00:00:00Z"),
            UpdatedAt = DateTimeOffset.Parse("2026-01-02T00:00:00Z")
        };

    public static Hive Hive(string name, Apiary apiary) =>
        new()
        {
            Id = Guid.NewGuid(),
            ApiaryId = apiary.Id,
            Apiary = apiary,
            Name = name,
            Type = HiveType.Langstroth,
            Status = HiveStatus.Active,
            Notes = "initial notes",
            ReginaPrezenta = true,
            VarstaRegina = 2,
            RameAlbine = 8,
            RamePuiet = 4,
            RameMiere = 3,
            CreatedAt = DateTimeOffset.Parse("2026-02-01T00:00:00Z"),
            UpdatedAt = DateTimeOffset.Parse("2026-02-02T00:00:00Z")
        };

    public static HiveTreatment Treatment(Hive hive, string productName = "Varachet") =>
        new()
        {
            Id = Guid.NewGuid(),
            HiveId = hive.Id,
            Hive = hive,
            ApiaryId = hive.ApiaryId,
            Apiary = hive.Apiary,
            TreatmentDate = DateTimeOffset.Parse("2026-03-01T00:00:00Z"),
            Type = TreatmentType.Varroa,
            ProductName = productName,
            Substance = "amitraz",
            Dosage = "2 strips",
            Notes = "treatment notes",
            NextTreatmentDate = DateTimeOffset.Parse("2026-03-14T00:00:00Z"),
            CreatedAt = DateTimeOffset.Parse("2026-03-01T01:00:00Z"),
            UpdatedAt = DateTimeOffset.Parse("2026-03-01T02:00:00Z")
        };

    public static HiveExtraction Extraction(Hive hive, decimal quantity = 12.5m) =>
        new()
        {
            Id = Guid.NewGuid(),
            HiveId = hive.Id,
            Hive = hive,
            ApiaryId = hive.ApiaryId,
            Apiary = hive.Apiary,
            ExtractionDate = DateTimeOffset.Parse("2026-04-01T00:00:00Z"),
            Type = ExtractionType.Honey,
            Quantity = quantity,
            Unit = "kg",
            Notes = "extraction notes",
            CreatedAt = DateTimeOffset.Parse("2026-04-01T01:00:00Z"),
            UpdatedAt = DateTimeOffset.Parse("2026-04-01T02:00:00Z")
        };

    public static Inspection Inspection(Hive hive) =>
        new()
        {
            Id = Guid.NewGuid(),
            HiveId = hive.Id,
            Hive = hive,
            ApiaryId = hive.ApiaryId,
            Apiary = hive.Apiary,
            InspectionDate = DateTimeOffset.Parse("2026-05-01T00:00:00Z"),
            Temperature = 26.5m,
            FramesCount = 10,
            BroodFrames = 4,
            HoneyFrames = 3,
            PollenFrames = 1,
            QueenSeen = true,
            EggsSeen = true,
            LarvaeSeen = false,
            CreatedAt = DateTimeOffset.Parse("2026-05-01T01:00:00Z"),
            UpdatedAt = DateTimeOffset.Parse("2026-05-01T02:00:00Z")
        };

    public static InspectionPhoto Photo(Inspection inspection, string description = "photo notes") =>
        new()
        {
            Id = Guid.NewGuid(),
            InspectionId = inspection.Id,
            Inspection = inspection,
            PhotoUrl = "https://example.test/photo.jpg",
            Description = description,
            CreatedAt = DateTimeOffset.Parse("2026-05-01T03:00:00Z")
        };
}

internal sealed class InMemoryApiaryRepository : IApiaryRepository
{
    public List<Apiary> Apiaries { get; } = new();
    public int SaveChangesCalls { get; private set; }

    public Task<IEnumerable<Apiary>> GetAllByUserIdAsync(Guid userId) =>
        Task.FromResult(Apiaries.Where(a => a.UserId == userId).AsEnumerable());

    public Task<Apiary?> GetByIdAsync(Guid id) =>
        Task.FromResult(Apiaries.SingleOrDefault(a => a.Id == id));

    public Task<Apiary?> GetByIdWithHivesAsync(Guid id) =>
        Task.FromResult(Apiaries.SingleOrDefault(a => a.Id == id));

    public Task AddAsync(Apiary apiary)
    {
        Apiaries.Add(apiary);
        return Task.CompletedTask;
    }

    public Task UpdateAsync(Apiary apiary) => Task.CompletedTask;

    public Task DeleteAsync(Apiary apiary)
    {
        Apiaries.Remove(apiary);
        return Task.CompletedTask;
    }

    public Task<bool> ExistsAsync(Guid id) =>
        Task.FromResult(Apiaries.Any(a => a.Id == id));

    public Task<bool> IsOwnedByUserAsync(Guid apiaryId, Guid userId) =>
        Task.FromResult(Apiaries.Any(a => a.Id == apiaryId && a.UserId == userId));

    public Task SaveChangesAsync()
    {
        SaveChangesCalls++;
        return Task.CompletedTask;
    }
}

internal sealed class InMemoryHiveRepository : IHiveRepository
{
    public List<Hive> Hives { get; } = new();
    public int GetAllByApiaryIdCalls { get; private set; }
    public int AddCalls { get; private set; }
    public int UpdateCalls { get; private set; }
    public int DeleteCalls { get; private set; }
    public int SaveChangesCalls { get; private set; }

    public Task<IEnumerable<Hive>> GetAllByApiaryIdAsync(Guid apiaryId)
    {
        GetAllByApiaryIdCalls++;
        return Task.FromResult(Hives.Where(h => h.ApiaryId == apiaryId).AsEnumerable());
    }

    public Task<IEnumerable<Hive>> GetAllByUserIdAsync(Guid userId) =>
        Task.FromResult(Hives.Where(h => h.Apiary.UserId == userId).AsEnumerable());

    public Task<Hive?> GetByIdAsync(Guid id) =>
        Task.FromResult(Hives.SingleOrDefault(h => h.Id == id));

    public Task AddAsync(Hive hive)
    {
        AddCalls++;
        Hives.Add(hive);
        return Task.CompletedTask;
    }

    public Task UpdateAsync(Hive hive)
    {
        UpdateCalls++;
        return Task.CompletedTask;
    }

    public Task DeleteAsync(Hive hive)
    {
        DeleteCalls++;
        Hives.Remove(hive);
        return Task.CompletedTask;
    }

    public Task<bool> ExistsAsync(Guid id) =>
        Task.FromResult(Hives.Any(h => h.Id == id));

    public Task<bool> IsOwnedByUserAsync(Guid hiveId, Guid userId) =>
        Task.FromResult(Hives.Any(h => h.Id == hiveId && h.Apiary.UserId == userId));

    public Task SaveChangesAsync()
    {
        SaveChangesCalls++;
        return Task.CompletedTask;
    }
}
