using Microsoft.EntityFrameworkCore;
using ApiaryServer.Domain.Entities;
using ApiaryServer.Infrastructure.Data;

public class UserRepository : IUserRepository
{
    private readonly AppDbContext _db;
    public UserRepository(AppDbContext db) => _db = db;
    public async Task<IEnumerable<User>> GetAllAsync() => await _db.Users.ToListAsync();
    public async Task AddAsync(User user) { await _db.Users.AddAsync(user); }
    public async Task<User?> GetByEmailAsync(string email) =>
        await _db.Users.Include(u => u.RefreshTokens).SingleOrDefaultAsync(u => u.Email == email);
    public async Task<User?> GetByIdAsync(Guid id) => await _db.Users.FindAsync(id);
    public async Task SaveChangesAsync() => await _db.SaveChangesAsync();
}
