namespace ApiaryServer.Application.Options
{
    public class AiServiceOptions
    {
        public string BaseUrl { get; set; } = "http://127.0.0.1:5000";
        public string AnalyzePath { get; set; } = "/analyze";
        public int TimeoutSeconds { get; set; } = 180;
    }
}
