namespace ApiaryServer.Domain.Entities
{
    public enum TaskPriority
    {
        Low,
        Normal,
        High,
        Critical
    }

    public enum TaskStatus
    {
        Pending,
        InProgress,
        Completed,
        Cancelled
    }

    public class HiveTask
    {
        public Guid Id { get; set; } = Guid.NewGuid();
        public Guid UserId { get; set; }
        public Guid? ApiaryId { get; set; }
        public Guid? HiveId { get; set; }
        public string Title { get; set; } = null!;
        public string? Description { get; set; }
        public TaskPriority Priority { get; set; } = TaskPriority.Normal;
        public TaskStatus Status { get; set; } = TaskStatus.Pending;
        public DateTimeOffset? DueDate { get; set; }
        public DateTimeOffset? CompletedAt { get; set; }
        public DateTimeOffset CreatedAt { get; set; } = DateTimeOffset.UtcNow;
        public DateTimeOffset UpdatedAt { get; set; } = DateTimeOffset.UtcNow;

        // Navigation properties
        public User User { get; set; } = null!;
        public Apiary? Apiary { get; set; }
        public Hive? Hive { get; set; }
    }
}