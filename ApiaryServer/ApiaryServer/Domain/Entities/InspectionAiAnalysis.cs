namespace ApiaryServer.Domain.Entities
{
    public class InspectionAiAnalysis
    {
        public Guid Id { get; set; } = Guid.NewGuid();
        public Guid InspectionId { get; set; }
        public Guid HiveId { get; set; }
        public Guid ApiaryId { get; set; }
        public string Status { get; set; } = "success";
        public string? Message { get; set; }
        public string RawResultsJson { get; set; } = "{}";
        public string CellDetectionsJson { get; set; } = "[]";
        public int TotalCells { get; set; }
        public int CappedBroodCells { get; set; }
        public int LarvaeCells { get; set; }
        public int EggsCells { get; set; }
        public int HoneyCells { get; set; }
        public int PollenCells { get; set; }
        public int EmptyCells { get; set; }
        public int OtherCells { get; set; }
        public int BroodCells { get; set; }
        public int StoresCells { get; set; }
        public decimal? BroodDensity { get; set; }
        public decimal? LarvaeToCappedRatio { get; set; }
        public decimal? StoresRatio { get; set; }
        public DateTimeOffset CreatedAt { get; set; } = DateTimeOffset.UtcNow;

        public Inspection Inspection { get; set; } = null!;
        public Hive Hive { get; set; } = null!;
        public Apiary Apiary { get; set; } = null!;
    }
}
