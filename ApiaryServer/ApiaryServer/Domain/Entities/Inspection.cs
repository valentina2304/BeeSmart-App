namespace ApiaryServer.Domain.Entities
{
    public class Inspection
    {
        public Guid Id { get; set; } = Guid.NewGuid();
        public Guid HiveId { get; set; }
        public Guid ApiaryId { get; set; }
        public DateTimeOffset InspectionDate { get; set; }
        public decimal? Temperature { get; set; }
        public int? FramesCount { get; set; }
        public int? BroodFrames { get; set; }
        public int? HoneyFrames { get; set; }
        public int? PollenFrames { get; set; }
        public bool QueenSeen { get; set; }
        public bool EggsSeen { get; set; }
        public bool LarvaeSeen { get; set; }
        public bool QueenCellsSeen { get; set; }
        public bool QueenCellsWithEggs { get; set; }
        public bool BeardingAtEntrance { get; set; }
        public bool SpaceNeeded { get; set; }
        public string? BroodPattern { get; set; }
        public int? HoneyCappingPercent { get; set; }
        public bool FeedingGiven { get; set; }
        public bool WaterAvailable { get; set; }
        public bool MoistureOrMold { get; set; }
        public bool DeadBeesAtEntrance { get; set; }
        public bool UnusualBehavior { get; set; }
        public string? Temperament { get; set; }
        public int? OldCombsToReplace { get; set; }
        public string? Notes { get; set; }
        public DateTimeOffset CreatedAt { get; set; } = DateTimeOffset.UtcNow;
        public DateTimeOffset UpdatedAt { get; set; } = DateTimeOffset.UtcNow;

        // Navigation properties
        public Hive Hive { get; set; } = null!;
        public Apiary Apiary { get; set; } = null!;
        public ICollection<InspectionPhoto> Photos { get; set; } = new List<InspectionPhoto>();
        public ICollection<InspectionAiAnalysis> AiAnalyses { get; set; } = new List<InspectionAiAnalysis>();
    }
}
