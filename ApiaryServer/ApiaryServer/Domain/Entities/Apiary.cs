namespace ApiaryServer.Domain.Entities
{
    public class Apiary
    {
        public Guid Id { get; set; } = Guid.NewGuid();
        public Guid UserId { get; set; }
        public string Name { get; set; } = null!;
        public string? Description { get; set; }
        public string? Location { get; set; }
        public DateTimeOffset CreatedAt { get; set; } = DateTimeOffset.UtcNow;
        public DateTimeOffset UpdatedAt { get; set; } = DateTimeOffset.UtcNow;

        // Navigation properties
        public User User { get; set; } = null!;
        public ICollection<Hive> Hives { get; set; } = new List<Hive>();
        public ICollection<HiveTask> Tasks { get; set; } = new List<HiveTask>();

        public ICollection<Inspection> Inspections { get; set; } = new List<Inspection>();
        public ICollection<InspectionAiAnalysis> AiAnalyses { get; set; } = new List<InspectionAiAnalysis>();
        public ICollection<HiveTreatment> Treatments { get; set; } = new List<HiveTreatment>();
        public ICollection<HiveExtraction> Extractions { get; set; } = new List<HiveExtraction>();
    }
}
