using ApiaryServer.Application.DTOs;
using ApiaryServer.Application.Exceptions;
using ApiaryServer.Application.Interfaces;
using ApiaryServer.Domain.Entities;
using ApiaryServer.Infrastructure.Services;
using Xunit;

namespace ApiaryServer.Tests;

public class HiveTreatmentServiceTests
{
    private static readonly Guid UserId = Guid.Parse("11111111-1111-1111-1111-111111111111");
    private static readonly Guid OtherUserId = Guid.Parse("22222222-2222-2222-2222-222222222222");

    [Fact]
    public async Task GetTreatmentsByApiaryIdAsync_WithForeignApiary_ThrowsBeforeQuery()
    {
        var apiaryRepo = new InMemoryApiaryRepository();
        var foreignApiary = TestEntityFactory.Apiary("Foreign", OtherUserId);
        apiaryRepo.Apiaries.Add(foreignApiary);
        var treatmentRepo = new FakeTreatmentRepository();
        var service = CreateService(treatmentRepo, apiaryRepo, new InMemoryHiveRepository());

        await Assert.ThrowsAsync<System.UnauthorizedAccessException>(() =>
            service.GetTreatmentsByApiaryIdAsync(foreignApiary.Id, UserId));

        Assert.Equal(0, treatmentRepo.GetByApiaryIdCalls);
    }

    [Fact]
    public async Task GetTreatmentsByHiveIdAsync_WithForeignHive_ThrowsBeforeQuery()
    {
        var foreignApiary = TestEntityFactory.Apiary("Foreign", OtherUserId);
        var foreignHive = TestEntityFactory.Hive("Foreign hive", foreignApiary);
        var hiveRepo = new InMemoryHiveRepository();
        hiveRepo.Hives.Add(foreignHive);
        var treatmentRepo = new FakeTreatmentRepository();
        var service = CreateService(treatmentRepo, new InMemoryApiaryRepository(), hiveRepo);

        await Assert.ThrowsAsync<System.UnauthorizedAccessException>(() =>
            service.GetTreatmentsByHiveIdAsync(foreignHive.Id, UserId));

        Assert.Equal(0, treatmentRepo.GetByHiveIdCalls);
    }

    [Fact]
    public async Task GetTreatmentByIdAsync_WithMissingTreatment_Throws()
    {
        var service = CreateService(new FakeTreatmentRepository(), new InMemoryApiaryRepository(), new InMemoryHiveRepository());

        await Assert.ThrowsAsync<TreatmentNotFoundException>(() =>
            service.GetTreatmentByIdAsync(Guid.NewGuid(), UserId));
    }

    [Fact]
    public async Task GetTreatmentByIdAsync_WithForeignTreatment_Throws()
    {
        var foreignApiary = TestEntityFactory.Apiary("Foreign", OtherUserId);
        var treatment = TestEntityFactory.Treatment(TestEntityFactory.Hive("Foreign hive", foreignApiary));
        var treatmentRepo = new FakeTreatmentRepository();
        treatmentRepo.Treatments.Add(treatment);
        var service = CreateService(treatmentRepo, new InMemoryApiaryRepository(), new InMemoryHiveRepository());

        await Assert.ThrowsAsync<System.UnauthorizedAccessException>(() =>
            service.GetTreatmentByIdAsync(treatment.Id, UserId));
    }

    [Fact]
    public async Task CreateTreatmentAsync_WithMissingHive_DoesNotPersist()
    {
        var treatmentRepo = new FakeTreatmentRepository();
        var service = CreateService(treatmentRepo, new InMemoryApiaryRepository(), new InMemoryHiveRepository());

        await Assert.ThrowsAsync<HiveNotFoundException>(() =>
            service.CreateTreatmentAsync(CreateTreatmentRequest(Guid.NewGuid()), UserId));

        Assert.Empty(treatmentRepo.Treatments);
        Assert.Equal(0, treatmentRepo.SaveChangesCalls);
    }

    [Fact]
    public async Task CreateTreatmentAsync_WithForeignHive_DoesNotPersist()
    {
        var foreignApiary = TestEntityFactory.Apiary("Foreign", OtherUserId);
        var foreignHive = TestEntityFactory.Hive("Foreign hive", foreignApiary);
        var hiveRepo = new InMemoryHiveRepository();
        hiveRepo.Hives.Add(foreignHive);
        var treatmentRepo = new FakeTreatmentRepository();
        var service = CreateService(treatmentRepo, new InMemoryApiaryRepository(), hiveRepo);

        await Assert.ThrowsAsync<System.UnauthorizedAccessException>(() =>
            service.CreateTreatmentAsync(CreateTreatmentRequest(foreignHive.Id), UserId));

        Assert.Empty(treatmentRepo.Treatments);
        Assert.Equal(0, treatmentRepo.SaveChangesCalls);
    }

    [Fact]
    public async Task CreateTreatmentAsync_WithOwnedHive_PersistsApiaryLinkAndMapsResponse()
    {
        var apiary = TestEntityFactory.Apiary("Main", UserId);
        var hive = TestEntityFactory.Hive("Hive A", apiary);
        var hiveRepo = new InMemoryHiveRepository();
        hiveRepo.Hives.Add(hive);
        var treatmentRepo = new FakeTreatmentRepository();
        var service = CreateService(treatmentRepo, new InMemoryApiaryRepository(), hiveRepo);

        var response = await service.CreateTreatmentAsync(CreateTreatmentRequest(hive.Id), UserId);

        Assert.Single(treatmentRepo.Treatments);
        Assert.Equal(hive.Id, treatmentRepo.Treatments[0].HiveId);
        Assert.Equal(apiary.Id, treatmentRepo.Treatments[0].ApiaryId);
        Assert.Equal("Hive A", response.HiveName);
        Assert.Equal("Main", response.ApiaryName);
        Assert.Equal("Varachet", response.ProductName);
        Assert.Equal(1, treatmentRepo.AddCalls);
        Assert.Equal(1, treatmentRepo.SaveChangesCalls);
    }

    [Fact]
    public async Task UpdateTreatmentAsync_WithOwnedTreatment_UpdatesFieldsAndSaves()
    {
        var apiary = TestEntityFactory.Apiary("Main", UserId);
        var treatment = TestEntityFactory.Treatment(TestEntityFactory.Hive("Hive", apiary));
        var treatmentRepo = new FakeTreatmentRepository();
        treatmentRepo.Treatments.Add(treatment);
        var service = CreateService(treatmentRepo, new InMemoryApiaryRepository(), new InMemoryHiveRepository());

        var response = await service.UpdateTreatmentAsync(treatment.Id, UpdateTreatmentRequest(), UserId);

        Assert.Equal("Oxalic", treatment.ProductName);
        Assert.Equal(TreatmentType.Preventive, treatment.Type);
        Assert.Equal("3 ml", response.Dosage);
        Assert.Equal(1, treatmentRepo.UpdateCalls);
        Assert.Equal(1, treatmentRepo.SaveChangesCalls);
    }

    [Fact]
    public async Task UpdateTreatmentAsync_WithForeignTreatment_DoesNotUpdate()
    {
        var foreignApiary = TestEntityFactory.Apiary("Foreign", OtherUserId);
        var treatment = TestEntityFactory.Treatment(TestEntityFactory.Hive("Foreign hive", foreignApiary));
        var treatmentRepo = new FakeTreatmentRepository();
        treatmentRepo.Treatments.Add(treatment);
        var service = CreateService(treatmentRepo, new InMemoryApiaryRepository(), new InMemoryHiveRepository());

        await Assert.ThrowsAsync<System.UnauthorizedAccessException>(() =>
            service.UpdateTreatmentAsync(treatment.Id, UpdateTreatmentRequest(), UserId));

        Assert.Equal("Varachet", treatment.ProductName);
        Assert.Equal(0, treatmentRepo.UpdateCalls);
        Assert.Equal(0, treatmentRepo.SaveChangesCalls);
    }

    [Fact]
    public async Task DeleteTreatmentAsync_WithOwnedTreatment_RemovesAndSaves()
    {
        var apiary = TestEntityFactory.Apiary("Main", UserId);
        var treatment = TestEntityFactory.Treatment(TestEntityFactory.Hive("Hive", apiary));
        var treatmentRepo = new FakeTreatmentRepository();
        treatmentRepo.Treatments.Add(treatment);
        var service = CreateService(treatmentRepo, new InMemoryApiaryRepository(), new InMemoryHiveRepository());

        await service.DeleteTreatmentAsync(treatment.Id, UserId);

        Assert.Empty(treatmentRepo.Treatments);
        Assert.Equal(1, treatmentRepo.DeleteCalls);
        Assert.Equal(1, treatmentRepo.SaveChangesCalls);
    }

    private static HiveTreatmentService CreateService(
        FakeTreatmentRepository treatmentRepo,
        InMemoryApiaryRepository apiaryRepo,
        InMemoryHiveRepository hiveRepo) =>
        new(treatmentRepo, apiaryRepo, hiveRepo, new NoOpLogger<HiveTreatmentService>());

    private static CreateTreatmentRequest CreateTreatmentRequest(Guid hiveId) =>
        new(
            hiveId,
            DateTimeOffset.Parse("2026-05-01T00:00:00Z"),
            TreatmentType.Varroa,
            "Varachet",
            "amitraz",
            "2 strips",
            "notes",
            DateTimeOffset.Parse("2026-05-14T00:00:00Z"));

    private static UpdateTreatmentRequest UpdateTreatmentRequest() =>
        new(
            DateTimeOffset.Parse("2026-06-01T00:00:00Z"),
            TreatmentType.Preventive,
            "Oxalic",
            "oxalic acid",
            "3 ml",
            "updated",
            null);

    private sealed class FakeTreatmentRepository : IHiveTreatmentRepository
    {
        public List<HiveTreatment> Treatments { get; } = new();
        public int GetByApiaryIdCalls { get; private set; }
        public int GetByHiveIdCalls { get; private set; }
        public int AddCalls { get; private set; }
        public int UpdateCalls { get; private set; }
        public int DeleteCalls { get; private set; }
        public int SaveChangesCalls { get; private set; }

        public Task<IEnumerable<HiveTreatment>> GetAllByUserIdAsync(Guid userId) =>
            Task.FromResult(Treatments.Where(t => t.Hive.Apiary.UserId == userId).AsEnumerable());

        public Task<IEnumerable<HiveTreatment>> GetByApiaryIdAsync(Guid apiaryId)
        {
            GetByApiaryIdCalls++;
            return Task.FromResult(Treatments.Where(t => t.ApiaryId == apiaryId).AsEnumerable());
        }

        public Task<IEnumerable<HiveTreatment>> GetByHiveIdAsync(Guid hiveId)
        {
            GetByHiveIdCalls++;
            return Task.FromResult(Treatments.Where(t => t.HiveId == hiveId).AsEnumerable());
        }

        public Task<HiveTreatment?> GetByIdAsync(Guid id) =>
            Task.FromResult(Treatments.SingleOrDefault(t => t.Id == id));

        public Task AddAsync(HiveTreatment treatment)
        {
            AddCalls++;
            Treatments.Add(treatment);
            return Task.CompletedTask;
        }

        public Task UpdateAsync(HiveTreatment treatment)
        {
            UpdateCalls++;
            return Task.CompletedTask;
        }

        public Task DeleteAsync(HiveTreatment treatment)
        {
            DeleteCalls++;
            Treatments.Remove(treatment);
            return Task.CompletedTask;
        }

        public Task<bool> ExistsAsync(Guid id) =>
            Task.FromResult(Treatments.Any(t => t.Id == id));

        public Task<bool> IsOwnedByUserAsync(Guid treatmentId, Guid userId) =>
            Task.FromResult(Treatments.Any(t => t.Id == treatmentId && t.Hive.Apiary.UserId == userId));

        public Task SaveChangesAsync()
        {
            SaveChangesCalls++;
            return Task.CompletedTask;
        }
    }
}
