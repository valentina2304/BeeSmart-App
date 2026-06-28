using System.Text;
using System.Threading.RateLimiting;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.HttpOverrides;
using Microsoft.AspNetCore.RateLimiting;
using Microsoft.EntityFrameworkCore;
using Microsoft.IdentityModel.Tokens;
using Microsoft.OpenApi.Models;

using ApiaryServer.Infrastructure.Data;
using ApiaryServer.Application.Interfaces;
using ApiaryServer.Infrastructure.Services;
using ApiaryServer.Infrastructure.Repositories;
using ApiaryServer.Application.Options;
using Microsoft.Data.SqlClient;

DotNetEnv.Env.Load();

var builder = WebApplication.CreateBuilder(args);

// Add DbContext
builder.Services.AddDbContext<AppDbContext>(options =>
    options.UseSqlServer(builder.Configuration.GetConnectionString("DefaultConnection")));

// Add Auth Repositories
builder.Services.AddScoped<IUserRepository, UserRepository>();
builder.Services.AddScoped<IRefreshTokenRepository, RefreshTokenRepository>();
builder.Services.AddScoped<IEmailConfirmationTokenRepository, EmailConfirmationTokenRepository>();
builder.Services.AddScoped<IPasswordResetTokenRepository, PasswordResetTokenRepository>();

// Add Hive Management Repositories
builder.Services.AddScoped<IApiaryRepository, ApiaryRepository>();
builder.Services.AddScoped<IHiveRepository, HiveRepository>();
builder.Services.AddScoped<ITaskRepository, TaskRepository>();
builder.Services.AddScoped<IHiveTreatmentRepository, HiveTreatmentRepository>();
builder.Services.AddScoped<IHiveExtractionRepository, HiveExtractionRepository>();

// Add Auth Services
builder.Services.AddScoped<IAuthService, AuthService>();
builder.Services.AddSingleton<IEmailService, EmailService>();

// Add Hive Management Services
builder.Services.AddScoped<IApiaryService, ApiaryService>();
builder.Services.AddScoped<IHiveService, HiveService>();
builder.Services.AddScoped<ITaskService, TaskService>();
builder.Services.AddScoped<IHiveTreatmentService, HiveTreatmentService>();
builder.Services.AddScoped<IHiveExtractionService, HiveExtractionService>();


// Add Inspection Repositories
builder.Services.AddScoped<IInspectionRepository, InspectionRepository>();
builder.Services.AddScoped<IInspectionPhotoRepository, InspectionPhotoRepository>();
builder.Services.AddScoped<IInspectionService, InspectionService>();

// Add AI Analysis Service
builder.Services.Configure<AiServiceOptions>(builder.Configuration.GetSection("AiService"));
builder.Services.AddHttpClient<IAiAnalysisService, AiAnalysisService>();

// Configure JWT
builder.Services.Configure<JwtOptions>(builder.Configuration.GetSection("Jwt"));
builder.Services.AddSingleton<IJwtService, JwtService>();

ConfigureJwtAuthentication(builder);

// Rate Limiting
builder.Services.AddRateLimiter(options =>
{
    options.RejectionStatusCode = StatusCodes.Status429TooManyRequests;

    options.GlobalLimiter = PartitionedRateLimiter.Create<HttpContext, string>(httpContext =>
    {
        var clientIp = httpContext.Connection.RemoteIpAddress?.ToString() ?? "unknown";
        return RateLimitPartition.GetFixedWindowLimiter(clientIp, _ => new FixedWindowRateLimiterOptions
        {
            PermitLimit = 120,
            Window = TimeSpan.FromMinutes(1),
            QueueProcessingOrder = QueueProcessingOrder.OldestFirst,
            QueueLimit = 20
        });
    });

    // General fixed window limiter
    options.AddFixedWindowLimiter("fixed", opt =>
    {
        opt.PermitLimit = 5;
        opt.Window = TimeSpan.FromMinutes(1);
        opt.QueueProcessingOrder = QueueProcessingOrder.OldestFirst;
        opt.QueueLimit = 2;
    });

    // Login specific rate limiter - prevents brute force attacks
    options.AddFixedWindowLimiter("login", opt =>
    {
        opt.PermitLimit = 5; // 5 login attempts
        opt.Window = TimeSpan.FromMinutes(1); // per minute
        opt.QueueProcessingOrder = QueueProcessingOrder.OldestFirst;
        opt.QueueLimit = 0; // No queuing for login attempts
    });
});

builder.Services.AddControllers();
builder.Services.AddEndpointsApiExplorer();
ConfigureSwagger(builder);
builder.Services.Configure<ForwardedHeadersOptions>(options =>
{
    options.ForwardedHeaders = ForwardedHeaders.XForwardedFor | ForwardedHeaders.XForwardedProto;
    options.KnownNetworks.Clear();
    options.KnownProxies.Clear();
});

var app = builder.Build();

ApplyMigrationsWithRetry(app);

// Middleware pipeline
if (app.Environment.IsDevelopment())
{
    app.UseSwagger();
    app.UseSwaggerUI();
}

app.UseForwardedHeaders();

static void ApplyMigrationsWithRetry(WebApplication app)
{
    using var scope = app.Services.CreateScope();
    var logger = scope.ServiceProvider.GetRequiredService<ILoggerFactory>().CreateLogger("DbMigration");
    var db = scope.ServiceProvider.GetRequiredService<AppDbContext>();

    const int maxRetries = 10;
    var delay = TimeSpan.FromSeconds(3);

    for (var attempt = 1; attempt <= maxRetries; attempt++)
    {
        try
        {
            db.Database.Migrate();
            logger.LogInformation("Database migration completed.");
            return;
        }
        catch (SqlException ex)
        {
            logger.LogWarning(ex, "Database not ready (attempt {Attempt}/{MaxRetries}). Retrying in {DelaySeconds}s...", attempt, maxRetries, delay.TotalSeconds);
            if (attempt == maxRetries)
            {
                throw;
            }

            Thread.Sleep(delay);
        }
    }
}

if (!app.Environment.IsDevelopment())
{
    app.UseHttpsRedirection();
}
app.UseRateLimiter();
app.UseAuthentication();
app.UseAuthorization();

app.MapControllers();
app.Run();

// Local functions for clean separation
static void ConfigureJwtAuthentication(WebApplicationBuilder builder)
{
    var jwtSection = builder.Configuration.GetSection("Jwt");
    var secret = jwtSection["Secret"] ?? jwtSection["Key"];

    if (string.IsNullOrEmpty(secret) ||
        secret.Contains("CHANGE_ME", StringComparison.OrdinalIgnoreCase) ||
        secret.Contains("YOUR_LONG_RANDOM_SECRET", StringComparison.OrdinalIgnoreCase) ||
        secret.Length < 32)
    {
        throw new InvalidOperationException("JWT secret must be configured with at least 32 random characters.");
    }

    var key = Encoding.UTF8.GetBytes(secret);

    builder.Services.AddAuthentication(options =>
    {
        options.DefaultAuthenticateScheme = JwtBearerDefaults.AuthenticationScheme;
        options.DefaultChallengeScheme = JwtBearerDefaults.AuthenticationScheme;
    })
    .AddJwtBearer(options =>
    {
        options.RequireHttpsMetadata = !builder.Environment.IsDevelopment();
        options.SaveToken = true;
        options.TokenValidationParameters = new TokenValidationParameters
        {
            ValidateIssuer = true,
            ValidateAudience = true,
            ValidateLifetime = true,
            ValidateIssuerSigningKey = true,
            ValidIssuer = jwtSection["Issuer"],
            ValidAudience = jwtSection["Audience"],
            IssuerSigningKey = new SymmetricSecurityKey(key)
        };
    });
}

static void ConfigureSwagger(WebApplicationBuilder builder)
{
    builder.Services.AddSwaggerGen(c =>
    {
        c.SwaggerDoc("v1", new OpenApiInfo { Title = "ApiaryServer API", Version = "v1" });

        c.AddSecurityDefinition("Bearer", new OpenApiSecurityScheme
        {
            Description = "JWT Bearer scheme. Enter only the token value.",
            Type = SecuritySchemeType.Http,
            Scheme = "bearer",
            BearerFormat = "JWT"
        });

        c.AddSecurityRequirement(new OpenApiSecurityRequirement
        {
            {
                new OpenApiSecurityScheme
                {
                    Reference = new OpenApiReference
                    {
                        Type = ReferenceType.SecurityScheme,
                        Id = "Bearer"
                    }
                },
                Array.Empty<string>()
            }
        });
    });
}
