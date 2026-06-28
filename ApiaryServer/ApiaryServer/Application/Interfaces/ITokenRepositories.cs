using ApiaryServer.Domain.Entities;

namespace ApiaryServer.Application.Interfaces
{
    public interface IEmailConfirmationTokenRepository
    {
        Task<EmailConfirmationToken?> GetByHashAsync(string tokenHash);
        Task<EmailConfirmationToken?> GetValidTokenAsync(Guid userId, string tokenHash);
        Task AddAsync(EmailConfirmationToken token);
        Task DeleteAsync(EmailConfirmationToken token);
        Task SaveChangesAsync();
    }

    public interface IPasswordResetTokenRepository
    {
        Task<PasswordResetToken?> GetByHashAsync(string tokenHash);
        Task<PasswordResetToken?> GetValidTokenAsync(Guid userId, string tokenHash);
        Task AddAsync(PasswordResetToken token);
        Task DeleteAsync(PasswordResetToken token);
        Task SaveChangesAsync();
    }
}