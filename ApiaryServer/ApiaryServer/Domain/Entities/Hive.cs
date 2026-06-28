namespace ApiaryServer.Domain.Entities
{
    public enum HiveType
    {
        Langstroth,
        Dadant,
        TopBar,
        Warre,
        Other
    }

    public enum HiveStatus
    {
        Active,
        Queenless,
        Weak,
        Sick,
        Preparing,
        Inactive
    }

    public class Hive
    {
        public Guid Id { get; set; } = Guid.NewGuid();
        public Guid ApiaryId { get; set; }
        public string Name { get; set; } = null!;
        public HiveType Type { get; set; }
        public HiveStatus Status { get; set; } = HiveStatus.Active;
        public string? Notes { get; set; }
        public bool ReginaPrezenta { get; set; } = false;
        public int VarstaRegina { get; set; } = 0;
        public int RameAlbine { get; set; } = 0;
        public int RamePuiet { get; set; } = 0;
        public int RameMiere { get; set; } = 0;
        public string? UltimaInspectie { get; set; }
        public DateTimeOffset CreatedAt { get; set; } = DateTimeOffset.UtcNow;
        public DateTimeOffset UpdatedAt { get; set; } = DateTimeOffset.UtcNow;

        // Navigation properties
        public Apiary Apiary { get; set; } = null!;
        public ICollection<HiveTask> Tasks { get; set; } = new List<HiveTask>();

        public ICollection<Inspection> Inspections { get; set; } = new List<Inspection>();
        public ICollection<InspectionAiAnalysis> AiAnalyses { get; set; } = new List<InspectionAiAnalysis>();
        public ICollection<HiveTreatment> Treatments { get; set; } = new List<HiveTreatment>();
        public ICollection<HiveExtraction> Extractions { get; set; } = new List<HiveExtraction>();
    }
}
