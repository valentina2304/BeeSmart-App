using Microsoft.EntityFrameworkCore;
using ApiaryServer.Domain.Entities;
using ApiaryServer.Infrastructure.Data;

public class RefreshTokenRepository : IRefreshTokenRepository
{
    private readonly AppDbContext _db;
    public RefreshTokenRepository(AppDbContext db) => _db = db;

    public async Task AddAsync(RefreshToken rt) => await _db.RefreshTokens.AddAsync(rt);

    public async Task<RefreshToken?> GetByHashAsync(string tokenHash) =>
        await _db.RefreshTokens.Include(r => r.User).SingleOrDefaultAsync(r => r.TokenHash == tokenHash);

    public async Task<IEnumerable<RefreshToken>> GetByUserIdAsync(Guid userId) =>
        await _db.RefreshTokens
            .Where(r => r.UserId == userId && !r.Revoked)
            .ToListAsync();

    public async Task SaveChangesAsync() => await _db.SaveChangesAsync();

    public async Task RevokeAsync(RefreshToken rt, string? replacedByJti = null)
    {
        rt.Revoked = true;
        rt.ReplacedByJti = replacedByJti;
        _db.RefreshTokens.Update(rt);
        await _db.SaveChangesAsync();
    }

    public async Task RevokeAllForUserAsync(Guid userId)
    {
        var tokens = await _db.RefreshTokens
            .Where(rt => rt.UserId == userId && !rt.Revoked)
            .ToListAsync();

        foreach (var token in tokens)
        {
            token.Revoked = true;
        }

        await _db.SaveChangesAsync();
    }
}