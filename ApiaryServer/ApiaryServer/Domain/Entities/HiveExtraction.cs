namespace ApiaryServer.Domain.Entities
{
    public enum ExtractionType
    {
        Honey,
        Pollen,
        Propolis,
        RoyalJelly,
        Wax,
        Other
    }

    public class HiveExtraction
    {
        public Guid Id { get; set; } = Guid.NewGuid();
        public Guid HiveId { get; set; }
        public Guid ApiaryId { get; set; }
        public DateTimeOffset ExtractionDate { get; set; }
        public ExtractionType Type { get; set; } = ExtractionType.Honey;
        public decimal Quantity { get; set; }
        public string Unit { get; set; } = "kg";
        public string? Notes { get; set; }
        public DateTimeOffset CreatedAt { get; set; } = DateTimeOffset.UtcNow;
        public DateTimeOffset UpdatedAt { get; set; } = DateTimeOffset.UtcNow;

        // Navigation properties
        public Hive Hive { get; set; } = null!;
        public Apiary Apiary { get; set; } = null!;
    }
}
