using ApiaryServer.Domain.Entities;
using ApiaryServer.Infrastructure.Data;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata;
using Xunit;

namespace ApiaryServer.Tests;

public class AppDbContextModelTests
{
    [Fact]
    public void DecimalProperties_HaveExplicitPrecision()
    {
        using var db = CreateContext();
        var model = db.Model;

        AssertPrecision<Inspection>(model, nameof(Inspection.Temperature), 5, 2);
        AssertPrecision<HiveExtraction>(model, nameof(HiveExtraction.Quantity), 10, 2);
        AssertPrecision<InspectionAiAnalysis>(model, nameof(InspectionAiAnalysis.BroodDensity), 9, 6);
        AssertPrecision<InspectionAiAnalysis>(model, nameof(InspectionAiAnalysis.LarvaeToCappedRatio), 9, 6);
        AssertPrecision<InspectionAiAnalysis>(model, nameof(InspectionAiAnalysis.StoresRatio), 9, 6);
    }

    [Fact]
    public void UserEmail_HasUniqueIndex()
    {
        using var db = CreateContext();

        var userType = AssertEntity<User>(db.Model);
        var emailIndex = Assert.Single(userType.GetIndexes(), index =>
            index.Properties.Count == 1 &&
            index.Properties[0].Name == nameof(User.Email));

        Assert.True(emailIndex.IsUnique);
    }

    [Fact]
    public void TaskForeignKeys_ToApiaryAndHive_DoNotCascade()
    {
        using var db = CreateContext();

        var taskType = AssertEntity<HiveTask>(db.Model);

        AssertForeignKeyDeleteBehavior(taskType, nameof(HiveTask.ApiaryId), DeleteBehavior.NoAction);
        AssertForeignKeyDeleteBehavior(taskType, nameof(HiveTask.HiveId), DeleteBehavior.NoAction);
        AssertForeignKeyDeleteBehavior(taskType, nameof(HiveTask.UserId), DeleteBehavior.Cascade);
    }

    [Fact]
    public void InspectionAndAiAnalysis_DeleteBehavior_AvoidsMultipleCascadePaths()
    {
        using var db = CreateContext();

        var inspectionType = AssertEntity<Inspection>(db.Model);
        AssertForeignKeyDeleteBehavior(inspectionType, nameof(Inspection.ApiaryId), DeleteBehavior.NoAction);
        AssertForeignKeyDeleteBehavior(inspectionType, nameof(Inspection.HiveId), DeleteBehavior.Cascade);

        var analysisType = AssertEntity<InspectionAiAnalysis>(db.Model);
        AssertForeignKeyDeleteBehavior(analysisType, nameof(InspectionAiAnalysis.ApiaryId), DeleteBehavior.NoAction);
        AssertForeignKeyDeleteBehavior(analysisType, nameof(InspectionAiAnalysis.HiveId), DeleteBehavior.NoAction);
        AssertForeignKeyDeleteBehavior(analysisType, nameof(InspectionAiAnalysis.InspectionId), DeleteBehavior.Cascade);
    }

    [Fact]
    public void InspectionAiAnalysis_HasCellDetectionsJsonColumn()
    {
        using var db = CreateContext();

        var property = AssertEntity<InspectionAiAnalysis>(db.Model)
            .FindProperty(nameof(InspectionAiAnalysis.CellDetectionsJson));

        Assert.NotNull(property);
        Assert.False(property.IsNullable);
        Assert.Equal("nvarchar(max)", property.GetColumnType());
    }

    private static AppDbContext CreateContext()
    {
        var options = new DbContextOptionsBuilder<AppDbContext>()
            .UseSqlServer("Server=(localdb)\\mssqllocaldb;Database=BeeSmartModelTests;Trusted_Connection=True;")
            .Options;

        return new AppDbContext(options);
    }

    private static void AssertPrecision<TEntity>(
        IModel model,
        string propertyName,
        int precision,
        int scale)
    {
        var property = AssertEntity<TEntity>(model).FindProperty(propertyName);
        Assert.NotNull(property);
        Assert.Equal(precision, property.GetPrecision());
        Assert.Equal(scale, property.GetScale());
    }

    private static IEntityType AssertEntity<TEntity>(IModel model)
    {
        var entityType = model.FindEntityType(typeof(TEntity));
        Assert.NotNull(entityType);
        return entityType;
    }

    private static void AssertForeignKeyDeleteBehavior(
        IEntityType entityType,
        string foreignKeyProperty,
        DeleteBehavior expected)
    {
        var foreignKey = Assert.Single(entityType.GetForeignKeys(), fk =>
            fk.Properties.Count == 1 &&
            fk.Properties[0].Name == foreignKeyProperty);

        Assert.Equal(expected, foreignKey.DeleteBehavior);
    }
}
