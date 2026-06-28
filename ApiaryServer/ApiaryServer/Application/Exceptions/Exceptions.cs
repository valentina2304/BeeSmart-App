namespace ApiaryServer.Application.Exceptions
{
    public class InvalidCredentialsException : Exception
    {
        public InvalidCredentialsException() : base("Invalid credentials") { }
    }

    public class DuplicateEmailException : Exception
    {
        public DuplicateEmailException() : base("Email already registered") { }
    }

    public class InvalidTokenException : Exception
    {
        public InvalidTokenException(string message = "Invalid token") : base(message) { }
    }

    public class TokenExpiredException : Exception
    {
        public TokenExpiredException() : base("Token expired") { }
    }

    public class TokenReuseException : Exception
    {
        public TokenReuseException() : base("Token reuse detected - all sessions revoked") { }
    }

    public class UserNotFoundException : Exception
    {
        public UserNotFoundException() : base("User not found") { }
    }

    public class EmailNotConfirmedException : Exception
    {
        public EmailNotConfirmedException() : base("Email not confirmed") { }
    }

    public class EmailDeliveryException : Exception
    {
        public EmailDeliveryException()
            : base("Email delivery failed") { }
    }

    public class TokenAlreadyUsedException : Exception
    {
        public TokenAlreadyUsedException() : base("Token has already been used") { }
    }
}
