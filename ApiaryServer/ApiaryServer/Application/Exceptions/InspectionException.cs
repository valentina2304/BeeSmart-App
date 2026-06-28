namespace ApiaryServer.Application.Exceptions
{
    public class InspectionNotFoundException : Exception
    {
        public InspectionNotFoundException()
            : base("Inspection not found") { }

        public InspectionNotFoundException(string message)
            : base(message) { }
    }

    public class InspectionPhotoNotFoundException : Exception
    {
        public InspectionPhotoNotFoundException()
            : base("Inspection photo not found") { }

        public InspectionPhotoNotFoundException(string message)
            : base(message) { }
    }
}