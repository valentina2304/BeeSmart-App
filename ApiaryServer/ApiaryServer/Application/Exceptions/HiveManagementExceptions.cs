namespace ApiaryServer.Application.Exceptions
{
    public class ApiaryNotFoundException : Exception
    {
        public ApiaryNotFoundException() : base("Apiary not found") { }
        public ApiaryNotFoundException(string message) : base(message) { }
    }

    public class HiveNotFoundException : Exception
    {
        public HiveNotFoundException() : base("Hive not found") { }
        public HiveNotFoundException(string message) : base(message) { }
    }

    public class TaskNotFoundException : Exception
    {
        public TaskNotFoundException() : base("Task not found") { }
        public TaskNotFoundException(string message) : base(message) { }
    }

    public class TreatmentNotFoundException : Exception
    {
        public TreatmentNotFoundException() : base("Treatment not found") { }
        public TreatmentNotFoundException(string message) : base(message) { }
    }

    public class ExtractionNotFoundException : Exception
    {
        public ExtractionNotFoundException() : base("Extraction not found") { }
        public ExtractionNotFoundException(string message) : base(message) { }
    }

    public class UnauthorizedAccessException : Exception
    {
        public UnauthorizedAccessException() : base("Unauthorized access to resource") { }
        public UnauthorizedAccessException(string message) : base(message) { }
    }
}