using ApiaryServer.Domain.Entities;

public interface IRefreshTokenRepository
{
    Task<RefreshToken?> GetByHashAsync(string tokenHash);
    Task<IEnumerable<RefreshToken>> GetByUserIdAsync(Guid userId);
    Task AddAsync(RefreshToken refreshToken);
    Task SaveChangesAsync();
    Task RevokeAsync(RefreshToken rt, string? replacedByJti = null);
    Task RevokeAllForUserAsync(Guid userId);
}