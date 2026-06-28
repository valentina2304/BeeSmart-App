namespace ApiaryServer.Domain.Entities
{
    public class InspectionPhoto
    {
        public Guid Id { get; set; } = Guid.NewGuid();
        public Guid InspectionId { get; set; }
        public string PhotoUrl { get; set; } = null!;
        public string? Description { get; set; }
        public DateTimeOffset CreatedAt { get; set; } = DateTimeOffset.UtcNow;

        // Navigation property
        public Inspection Inspection { get; set; } = null!;
    }
}