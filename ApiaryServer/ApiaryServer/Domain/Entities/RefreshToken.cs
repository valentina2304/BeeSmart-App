namespace ApiaryServer.Domain.Entities
{
    public class RefreshToken
    {
        public Guid Id { get; set; } = Guid.NewGuid();
        public Guid UserId { get; set; }
        public string TokenHash { get; set; } = null!;
        public string Jti { get; set; } = null!;
        public string? DeviceInfo { get; set; }
        public DateTimeOffset IssuedAt { get; set; }
        public DateTimeOffset ExpiresAt { get; set; }
        public bool Revoked { get; set; } = false;
        public string? ReplacedByJti { get; set; }

        public User User { get; set; } = null!;
    }
}
