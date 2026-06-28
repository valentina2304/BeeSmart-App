using Microsoft.EntityFrameworkCore;
using ApiaryServer.Domain.Entities;
using ApiaryServer.Infrastructure.Data;
using ApiaryServer.Application.Interfaces;

namespace ApiaryServer.Infrastructure.Repositories
{
    public class EmailConfirmationTokenRepository : IEmailConfirmationTokenRepository
    {
        private readonly AppDbContext _db;

        public EmailConfirmationTokenRepository(AppDbContext db) => _db = db;

        public async Task<EmailConfirmationToken?> GetByHashAsync(string tokenHash) =>
            await _db.EmailConfirmationTokens
                .Include(t => t.User)
                .SingleOrDefaultAsync(t => t.TokenHash == tokenHash);

        public async Task<EmailConfirmationToken?> GetValidTokenAsync(Guid userId, string tokenHash) =>
            await _db.EmailConfirmationTokens
                .Include(t => t.User)
                .SingleOrDefaultAsync(t =>
                    t.UserId == userId &&
                    t.TokenHash == tokenHash &&
                    t.ExpiresAt > DateTimeOffset.UtcNow);

        public async Task AddAsync(EmailConfirmationToken token) =>
            await _db.EmailConfirmationTokens.AddAsync(token);

        public Task DeleteAsync(EmailConfirmationToken token)
        {
            _db.EmailConfirmationTokens.Remove(token);
            return Task.CompletedTask;
        }

        public async Task SaveChangesAsync() => await _db.SaveChangesAsync();
    }

    public class PasswordResetTokenRepository : IPasswordResetTokenRepository
    {
        private readonly AppDbContext _db;

        public PasswordResetTokenRepository(AppDbContext db) => _db = db;

        public async Task<PasswordResetToken?> GetByHashAsync(string tokenHash) =>
            await _db.PasswordResetTokens
                .Include(t => t.User)
                .SingleOrDefaultAsync(t => t.TokenHash == tokenHash);

        public async Task<PasswordResetToken?> GetValidTokenAsync(Guid userId, string tokenHash) =>
            await _db.PasswordResetTokens
                .Include(t => t.User)
                .SingleOrDefaultAsync(t =>
                    t.UserId == userId &&
                    t.TokenHash == tokenHash &&
                    t.ExpiresAt > DateTimeOffset.UtcNow);

        public async Task AddAsync(PasswordResetToken token) =>
            await _db.PasswordResetTokens.AddAsync(token);

        public Task DeleteAsync(PasswordResetToken token)
        {
            _db.PasswordResetTokens.Remove(token);
            return Task.CompletedTask;
        }

        public async Task SaveChangesAsync() => await _db.SaveChangesAsync();
    }
}
