using Microsoft.EntityFrameworkCore;
using ApiaryServer.Domain.Entities;

namespace ApiaryServer.Infrastructure.Data
{
    public class AppDbContext : DbContext
    {
        public AppDbContext(DbContextOptions<AppDbContext> options) : base(options) { }

        public DbSet<User> Users => Set<User>();
        public DbSet<RefreshToken> RefreshTokens => Set<RefreshToken>();
        public DbSet<EmailConfirmationToken> EmailConfirmationTokens => Set<EmailConfirmationToken>();
        public DbSet<PasswordResetToken> PasswordResetTokens => Set<PasswordResetToken>();
        public DbSet<Apiary> Apiaries => Set<Apiary>();
        public DbSet<Hive> Hives => Set<Hive>();
        public DbSet<HiveTask> Tasks => Set<HiveTask>();

        public DbSet<Inspection> Inspections => Set<Inspection>();
        public DbSet<InspectionPhoto> InspectionPhotos => Set<InspectionPhoto>();
        public DbSet<InspectionAiAnalysis> InspectionAiAnalyses => Set<InspectionAiAnalysis>();
        public DbSet<HiveTreatment> HiveTreatments => Set<HiveTreatment>();
        public DbSet<HiveExtraction> HiveExtractions => Set<HiveExtraction>();

        protected override void OnModelCreating(ModelBuilder modelBuilder)
        {
            // User configuration
            modelBuilder.Entity<User>()
                .HasIndex(u => u.Email)
                .IsUnique();

            // RefreshToken configuration
            modelBuilder.Entity<RefreshToken>()
                .HasOne(rt => rt.User)
                .WithMany(u => u.RefreshTokens)
                .HasForeignKey(rt => rt.UserId)
                .OnDelete(DeleteBehavior.Cascade);

            // EmailConfirmationToken configuration
            modelBuilder.Entity<EmailConfirmationToken>()
                .HasOne(t => t.User)
                .WithMany(u => u.EmailConfirmationTokens)
                .HasForeignKey(t => t.UserId)
                .OnDelete(DeleteBehavior.Cascade);

            modelBuilder.Entity<EmailConfirmationToken>()
                .HasIndex(t => t.TokenHash);

            // PasswordResetToken configuration
            modelBuilder.Entity<PasswordResetToken>()
                .HasOne(t => t.User)
                .WithMany(u => u.PasswordResetTokens)
                .HasForeignKey(t => t.UserId)
                .OnDelete(DeleteBehavior.Cascade);

            modelBuilder.Entity<PasswordResetToken>()
                .HasIndex(t => t.TokenHash);

            // Apiary configuration
            modelBuilder.Entity<Apiary>()
                .HasOne(a => a.User)
                .WithMany()
                .HasForeignKey(a => a.UserId)
                .OnDelete(DeleteBehavior.Cascade);

            modelBuilder.Entity<Apiary>()
                .HasMany(a => a.Hives)
                .WithOne(h => h.Apiary)
                .HasForeignKey(h => h.ApiaryId)
                .OnDelete(DeleteBehavior.Cascade);

            // Hive configuration
            modelBuilder.Entity<Hive>()
                .HasOne(h => h.Apiary)
                .WithMany(a => a.Hives)
                .HasForeignKey(h => h.ApiaryId)
                .OnDelete(DeleteBehavior.Cascade);

            // Task configuration
            modelBuilder.Entity<HiveTask>()
                .HasOne(t => t.User)
                .WithMany()
                .HasForeignKey(t => t.UserId)
                .OnDelete(DeleteBehavior.Cascade);

            modelBuilder.Entity<HiveTask>()
                .HasOne(t => t.Apiary)
                .WithMany(a => a.Tasks)
                .HasForeignKey(t => t.ApiaryId)
                .OnDelete(DeleteBehavior.NoAction); // Prevent cascade delete conflicts

            modelBuilder.Entity<HiveTask>()
                .HasOne(t => t.Hive)
                .WithMany(h => h.Tasks)
                .HasForeignKey(t => t.HiveId)
                .OnDelete(DeleteBehavior.NoAction); // Prevent cascade delete conflicts

            // Indexes for better query performance
            modelBuilder.Entity<Apiary>()
                .HasIndex(a => a.UserId);

            modelBuilder.Entity<Hive>()
                .HasIndex(h => h.ApiaryId);

            modelBuilder.Entity<HiveTask>()
                .HasIndex(t => t.UserId);

            modelBuilder.Entity<HiveTask>()
                .HasIndex(t => t.ApiaryId);

            modelBuilder.Entity<HiveTask>()
                .HasIndex(t => t.HiveId);

            modelBuilder.Entity<HiveTask>()
                .HasIndex(t => new { t.UserId, t.Status });

            modelBuilder.Entity<HiveTask>()
                .HasIndex(t => new { t.UserId, t.DueDate });



            // Inspection configuration
            modelBuilder.Entity<Inspection>()
                .HasOne(i => i.Hive)
                .WithMany(h => h.Inspections)
                .HasForeignKey(i => i.HiveId)
                .OnDelete(DeleteBehavior.Cascade);

            modelBuilder.Entity<Inspection>()
                .HasOne(i => i.Apiary)
                .WithMany(a => a.Inspections)
                .HasForeignKey(i => i.ApiaryId)
                .OnDelete(DeleteBehavior.NoAction); // Prevent cascade delete conflicts

            modelBuilder.Entity<Inspection>()
                .HasMany(i => i.Photos)
                .WithOne(p => p.Inspection)
                .HasForeignKey(p => p.InspectionId)
                .OnDelete(DeleteBehavior.Cascade);

            modelBuilder.Entity<Inspection>()
                .HasMany(i => i.AiAnalyses)
                .WithOne(a => a.Inspection)
                .HasForeignKey(a => a.InspectionId)
                .OnDelete(DeleteBehavior.Cascade);

            // InspectionPhoto configuration
            modelBuilder.Entity<InspectionPhoto>()
                .HasOne(p => p.Inspection)
                .WithMany(i => i.Photos)
                .HasForeignKey(p => p.InspectionId)
                .OnDelete(DeleteBehavior.Cascade);

            // InspectionAiAnalysis configuration
            modelBuilder.Entity<InspectionAiAnalysis>()
                .HasOne(a => a.Inspection)
                .WithMany(i => i.AiAnalyses)
                .HasForeignKey(a => a.InspectionId)
                .OnDelete(DeleteBehavior.Cascade);

            modelBuilder.Entity<InspectionAiAnalysis>()
                .HasOne(a => a.Hive)
                .WithMany(h => h.AiAnalyses)
                .HasForeignKey(a => a.HiveId)
                .OnDelete(DeleteBehavior.NoAction);

            modelBuilder.Entity<InspectionAiAnalysis>()
                .HasOne(a => a.Apiary)
                .WithMany(a => a.AiAnalyses)
                .HasForeignKey(a => a.ApiaryId)
                .OnDelete(DeleteBehavior.NoAction);

            // Indexes for better query performance
            modelBuilder.Entity<Inspection>()
                .HasIndex(i => i.HiveId);

            modelBuilder.Entity<Inspection>()
                .HasIndex(i => i.ApiaryId);

            modelBuilder.Entity<Inspection>()
                .HasIndex(i => i.InspectionDate);

            modelBuilder.Entity<InspectionPhoto>()
                .HasIndex(p => p.InspectionId);

            modelBuilder.Entity<InspectionAiAnalysis>()
                .HasIndex(a => a.InspectionId);

            modelBuilder.Entity<InspectionAiAnalysis>()
                .HasIndex(a => new { a.HiveId, a.CreatedAt });

            modelBuilder.Entity<Inspection>()
                .Property(i => i.Temperature)
                .HasPrecision(5, 2);

            modelBuilder.Entity<InspectionAiAnalysis>()
                .Property(a => a.BroodDensity)
                .HasPrecision(9, 6);

            modelBuilder.Entity<InspectionAiAnalysis>()
                .Property(a => a.LarvaeToCappedRatio)
                .HasPrecision(9, 6);

            modelBuilder.Entity<InspectionAiAnalysis>()
                .Property(a => a.StoresRatio)
                .HasPrecision(9, 6);

            // HiveTreatment configuration
            modelBuilder.Entity<HiveTreatment>()
                .HasOne(t => t.Hive)
                .WithMany(h => h.Treatments)
                .HasForeignKey(t => t.HiveId)
                .OnDelete(DeleteBehavior.Cascade);

            modelBuilder.Entity<HiveTreatment>()
                .HasOne(t => t.Apiary)
                .WithMany(a => a.Treatments)
                .HasForeignKey(t => t.ApiaryId)
                .OnDelete(DeleteBehavior.NoAction); // Prevent cascade delete conflicts

            // Indexes for better query performance
            modelBuilder.Entity<HiveTreatment>()
                .HasIndex(t => t.HiveId);

            modelBuilder.Entity<HiveTreatment>()
                .HasIndex(t => t.ApiaryId);

            modelBuilder.Entity<HiveTreatment>()
                .HasIndex(t => t.TreatmentDate);

            // HiveExtraction configuration
            modelBuilder.Entity<HiveExtraction>()
                .HasOne(e => e.Hive)
                .WithMany(h => h.Extractions)
                .HasForeignKey(e => e.HiveId)
                .OnDelete(DeleteBehavior.Cascade);

            modelBuilder.Entity<HiveExtraction>()
                .HasOne(e => e.Apiary)
                .WithMany(a => a.Extractions)
                .HasForeignKey(e => e.ApiaryId)
                .OnDelete(DeleteBehavior.NoAction); // Prevent cascade delete conflicts

            // Indexes for better query performance
            modelBuilder.Entity<HiveExtraction>()
                .HasIndex(e => e.HiveId);

            modelBuilder.Entity<HiveExtraction>()
                .HasIndex(e => e.ApiaryId);

            modelBuilder.Entity<HiveExtraction>()
                .HasIndex(e => e.ExtractionDate);

            modelBuilder.Entity<HiveExtraction>()
                .Property(e => e.Quantity)
                .HasPrecision(10, 2);
        }
    }
}
