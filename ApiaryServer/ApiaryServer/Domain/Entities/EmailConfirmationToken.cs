namespace ApiaryServer.Domain.Entities
{
    public class EmailConfirmationToken
    {
        public Guid Id { get; set; } = Guid.NewGuid();
        public Guid UserId { get; set; }
        public string TokenHash { get; set; } = null!;
        public DateTimeOffset CreatedAt { get; set; } = DateTimeOffset.UtcNow;
        public DateTimeOffset ExpiresAt { get; set; }
        public bool Used { get; set; } = false;

        public User User { get; set; } = null!;
    }
}