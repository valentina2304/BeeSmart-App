namespace ApiaryServer.Domain.Entities
{
    public enum TreatmentType
    {
        Varroa,
        Nosema,
        Fungal,
        Viral,
        Bacterial,
        Preventive,
        Other
    }

    public class HiveTreatment
    {
        public Guid Id { get; set; } = Guid.NewGuid();
        public Guid HiveId { get; set; }
        public Guid ApiaryId { get; set; }
        public DateTimeOffset TreatmentDate { get; set; }
        public TreatmentType Type { get; set; } = TreatmentType.Other;
        public string ProductName { get; set; } = null!;
        public string? Substance { get; set; }
        public string? Dosage { get; set; }
        public string? Notes { get; set; }
        public DateTimeOffset? NextTreatmentDate { get; set; }
        public DateTimeOffset CreatedAt { get; set; } = DateTimeOffset.UtcNow;
        public DateTimeOffset UpdatedAt { get; set; } = DateTimeOffset.UtcNow;

        // Navigation properties
        public Hive Hive { get; set; } = null!;
        public Apiary Apiary { get; set; } = null!;
    }
}
